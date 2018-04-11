package com.QADT;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Platform;

public class dataManager {
	private float distToOrg, distTrav;
	private ArrayList<tuple> path;
	private float altAlt, altGPS, speed, HDOP, sValid, battState, heading;
	private int satelites, btsAv;
	private String fixType;
	private GUIController cont;
	private SerialCommunicator comm;
	private static final Logger log = Logger.getLogger(dataManager.class.getName());
	private static final DecimalFormat df = new DecimalFormat("#.##");
	private static final DecimalFormat df4 = new DecimalFormat("#.####");
	
	//Transmission constants (all private by default, not written for clarity)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c', BATT_V = 'b';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	static final String[] FIX_TYPE = {"invalid", "GPS fix (SPS)", "DGPS fix",  "PPS fix", "Real Time Kinematic", "Float RTK", "estimated (dead reckoning) (2.3 feature)", "Manual input mode", "Simulation mode"};
	static final String[] ON_MAP = {"unknown", "left of map", "right of map", "above map", "below map", "on map"};
	
	//Class Constructor
	public dataManager(GUIController _cont) {
		cont = _cont;
		distToOrg = distTrav = battState = HDOP = altAlt = altGPS = speed = sValid = satelites = btsAv = 0;
		fixType = FIX_TYPE[0];
		path = new ArrayList<tuple>();
		update();
	}
	//Returns list of GPS coordinates 
	public ArrayList<tuple> getPath() {return path; }
	
	//Formats number to 2 decimal places
	public String getFormatted(double inp) {return df.format(inp); }
	
	//Formats number to 4 decimal places
	public String getFormatted4(double inp) {return df4.format(inp); }
	
	//Passes communicator class
	public void passComm(SerialCommunicator _comm) {comm = _comm; }
	
	//Checks status of connections for warnings
	public void statusChecks(double packDelta) {
		boolean badConn = false;
		//Xbee Connection
		if(packDelta > 2) {
			cont.setXW(true);
			badConn = true;
		}
		else if(cont.getXW())
			cont.setXW(false);
		//Serial Connection
		else if(cont.getSW())
			cont.setSW(false);
		if(badConn && !comm.getPortStatus())
			cont.setSW(true);
		//Battery Level
		if(battState < 9.5 && !cont.getBW())
			cont.setBW(true);
		else if(battState >= 9.5 && cont.getBW())
			cont.setBW(false);
		if(fixType == FIX_TYPE[0] && !cont.getGW())
			cont.setGW(true);
		else if(fixType != FIX_TYPE[0] && cont.getGW())
			cont.setGW(false);
	}
	public void printPackTime() {
		double packDelta = (double)comm.getPackTime() / 1000;
		Platform.runLater(new Runnable() {
			@Override
			public void run() {cont.packTime.setText("Pack Time: " + df.format(packDelta) + 's'); }
		});
		statusChecks(packDelta);
	}
	private void update() {
		cont.updateWarnText();
		Platform.runLater(new Runnable()  {
			@Override
			public void run() {
				cont.orginDist.setText("Distance from origin: " + Float.toString(distToOrg) + "m");
				cont.travDist.setText("Distance travelled: " + Float.toString(distTrav) + "m");
				cont.batLevel.setText("Battery state: " + df.format(battState) + " V");
				cont.hdop.setText("HDOP: " + Float.toString(HDOP));
				cont.heightL.setText("Height: " + getHeight());
				cont.satNum.setText("Satellites: " + satelites);
				cont.bytesAvail.setText("Bytes availible: " + btsAv);
				cont.packetsR.setText("Packets received: " + comm.packsIn);
				cont.packRateLabel.setText("Pack rate: " + df.format(comm.packRate) + " packs/s");
				cont.speed.setText("Speed: " + speed);
				cont.vHDOP.setText("Time since valid HDOP: " + sValid + "s");
				cont.fixTp.setText("Fix Type: " + fixType);
				cont.pointsTaken.setText("Points taken: " + path.size());
				cont.headingLabel.setText("Heading: " + heading);
				cont.onMapStatus.setText("Currently: " + ON_MAP[cont.mapRel]);
				if(comm != null && comm.getState())
					cont.connState.setText("Connection state: Connected to " + comm.portName);
				else
					cont.connState.setText("Connection state: Disconnected.");
			}
		});
	}
	public void openBay() {comm.sendByte((byte)DROP_OPEN); }
	public void closeBay() {comm.sendByte((byte)DROP_CLOSE); }
	public void newPoint(float latt, float lon, float h1, float h2, float heading){
		tuple newLoc = new tuple(latt,lon, h1, h2, heading);
		if(path.size() < 1) {
			path.add(newLoc);
			return;
		}
		float distFromLast = getDistance(path.get(path.size()-1),newLoc);
		//check if new location more than 200m away from last point, if so don't add point
		if(distFromLast<=100 || true) {
			distTrav += distFromLast;
			distToOrg = getDistance(path.get(0),newLoc);
			path.add(newLoc);
		}
		cont.drawPath();
	}
	private float getDistance(tuple loc1, tuple loc2){
		double dlatt = Math.toRadians(loc2.x)-Math.toRadians(loc1.x);
		double dlong = Math.toRadians(loc2.y)-Math.toRadians(loc1.y);
		double a = Math.pow(Math.sin(dlatt/2),2) + Math.cos(Math.toRadians(loc1.x)) * Math.cos(Math.toRadians(loc2.x)) * Math.pow(Math.sin(dlong/2),2);
		float c = (float) (2 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a)));
		float dist = 6371 * c;
		return dist*1000; 	//dist in meters
	}
	public float getDistanceTraveled(){return distTrav; }
	public float distanceFromStart(){return distToOrg; }
	public void newPacket(byte[] pack) {newPacket(pack, -1); }
	//Reorders incoming bytes from the Xbee (i.e. 4321 -> 1234)
	private byte[] flipBytes(byte[] set) {
		int len = set.length;
		byte[] out = new byte[len];
		for(int i=0;i<len;i++)
			out[i] = set[len-i-1];
		return out;
	}
	//Parses new incoming data packet
	public void newPacket(byte[] pack, int _btsAv) {
		float lat = 0, lng = 0;
		for(int i=2;i<31;i+=4) {
			byte[] tmp = new byte[4];
			System.arraycopy(pack, i, tmp, 0, 4);
			tmp = flipBytes(tmp);
			if(i == 2)
				altAlt = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 6)
				speed = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 10)
				lng = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 14)
				lat = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 18)
				HDOP = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 22)
				sValid = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 26)
				altGPS = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 30)
				battState = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 34)
				heading = ByteBuffer.wrap(tmp).getFloat();
		}
		if(pack[38] != 0 && HDOP < 3)
			newPoint(lat, lng, altAlt, altGPS, heading);

		btsAv = _btsAv;
		try {
			fixType = FIX_TYPE[pack[38]];
			satelites = pack[39];
		} catch(ArrayIndexOutOfBoundsException e) {log.severe("You fucked up... fix type doesn't exist"); }
		update();
	}
	public float getHeight() {return (altAlt + altGPS) / 2; }
	public double getAvg(double a, double b) {return (a + b) / 2; }
	public void exportData() {
		if(path.isEmpty()) return;
		
		JSONObject out = new JSONObject();
		JSONArray data = new JSONArray();
		for(tuple pt : path) {
			JSONObject tmp = new JSONObject();
			tmp.put("long", pt.x);
			tmp.put("lat", pt.y);
			tmp.put("height", getAvg(pt.h1, pt.h2));
			tmp.put("bearing", pt.head);
			data.put(tmp);
		}
		out.put("data", data);
		String fileName = new SimpleDateFormat("'data_'yyyy'_'MM'_'dd'_'HH'_'mm'.json'").format(new Date());
		try(FileWriter file = new FileWriter(fileName)) {
			file.write(out.toString());
			file.flush();
		} catch(IOException e) {
			log.severe("Failed to export data to file!");
			e.printStackTrace();
		}
	}
}
