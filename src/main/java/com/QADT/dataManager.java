package com.QADT;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Platform;

public class dataManager {
	//Tracking/data attributes for plane
	ArrayList<tuple> path, picPoints;
	float distToOrg, distTrav, altAlt, altGPS, speed, HDOP, sValid, battState, heading;
	int satelites, btsAv;
	String fixType;
	
	//Utility objects
	GUIController cont;
	SerialCommunicator comm;
	static final Logger log = Logger.getLogger(dataManager.class.getName());
	protected static final DecimalFormat df = new DecimalFormat("#.##");
	protected static final DecimalFormat df4 = new DecimalFormat("#.####");

	//Transmission constants (all private by default)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c', BATT_V = 'b';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	static final String[] FIX_TYPE = {"invalid", "GPS fix (SPS)", "DGPS fix",  "PPS fix", "Real Time Kinematic", "Float RTK", "estimated (dead reckoning) (2.3 feature)", "Manual input mode", "Simulation mode"};
	static final String[] ON_MAP = {"unknown", "left of map", "right of map", "above map", "below map", "on map"};

	//Class Constructor
	public dataManager(GUIController _cont) {
		cont = _cont;
		GUIController.addLogHandler(log); 
		distToOrg = distTrav = battState = HDOP = altAlt = altGPS = speed = sValid = satelites = btsAv = 0;
		fixType = FIX_TYPE[0]; // Initializes fix type to be disconnected
		path = new ArrayList<tuple>();
		picPoints = new ArrayList<tuple>();
		update(); //Initial printing of data to the GUI
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
		
		//Fix condition
		if(fixType == FIX_TYPE[0] && !cont.getGW())
			cont.setGW(true);
		else if(fixType != FIX_TYPE[0] && cont.getGW())
			cont.setGW(false);
	}
	
	//Updates the time since the last data packet was received
	public void printPackTime() {
		double packDelta = (double)comm.getPackTime() / 1000;
		Platform.runLater(new Runnable() {
			@Override
			public void run() {cont.packTime.setText("Pack Time: " + df.format(packDelta) + 's'); }
		});
		statusChecks(packDelta);
	}
	
	//Prints new data to the GUI
	private void update() {
		cont.updateWarnText();
		Platform.runLater(new Runnable()  {
			@Override
			public void run() {
				//Physical status data
				cont.orginDist.setText("Distance from origin: " + Float.toString(distToOrg) + "m");
				cont.travDist.setText("Distance travelled: " + Float.toString(distTrav) + "m");
				cont.heightL.setText("Height: " + getHeight());
				cont.onMapStatus.setText("Currently: " + ON_MAP[cont.mapRel]);
				cont.speed.setText("Speed: " + speed);
				cont.headingLabel.setText("Heading: " + heading);
				
				//GPS data
				cont.hdop.setText("HDOP: " + Float.toString(HDOP));
				cont.satNum.setText("Satellites: " + satelites);
				cont.vHDOP.setText("Time since valid HDOP: " + sValid + "s");
				cont.fixTp.setText("Fix Type: " + fixType);
				cont.pointsTaken.setText("Points taken: " + path.size());
				
				//Connection data
				cont.batLevel.setText("Battery state: " + df.format(battState) + " V");
				cont.bytesAvail.setText("Bytes availible: " + btsAv);
				cont.packetsR.setText("Packets received: " + comm.packsIn);
				cont.picPLabel.setText("Picture Packets: " + picPoints.size());
				cont.dataPLabel.setText("Data Packets: " + path.size());
				cont.packRateLabel.setText("Pack rate: " + df.format(comm.packRate) + " packs/s");
				if(comm != null && comm.getState())
					cont.connState.setText("Connection state: Connected to " + comm.portName);
				else
					cont.connState.setText("Connection state: Disconnected.");
			}
		});
	}
	
	//Adds a new point to the path (list of GPS coordinates with height and heading
	public void newPoint(float latt, float lon, float h1, float h2, float heading){
		tuple newLoc = new tuple(latt,lon, h1, h2, heading);
		if(path.size() < 1) {
			path.add(newLoc);
			return;
		}
		//Check if new location more than 200m away from last point, if so don't add point
		float distFromLast = getDistance(path.get(path.size()-1),newLoc);
		if(distFromLast <= 200 || true) { //Get ride of || true after more testing...
			distTrav += distFromLast;
			distToOrg = getDistance(path.get(0),newLoc);
			path.add(newLoc);
		}
		cont.drawPath();
	}
	
	//Returns the distance between two points (in meters)
	private float getDistance(tuple loc1, tuple loc2){
		double dlatt = Math.toRadians(loc2.x)-Math.toRadians(loc1.x);
		double dlong = Math.toRadians(loc2.y)-Math.toRadians(loc1.y);
		double a = Math.pow(Math.sin(dlatt/2),2) + Math.cos(Math.toRadians(loc1.x)) * Math.cos(Math.toRadians(loc2.x)) * Math.pow(Math.sin(dlong/2),2);
		float c = (float) (2 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a)));
		float dist = 6371 * c;
		return dist*1000; 	//dist in meters
	}
	//Returns distance 
	public float getDistanceTraveled(){return distTrav; }
	public float distanceFromStart(){return distToOrg; }
	
	//Reorders incoming bytes from the Xbee (i.e. 4321 -> 1234)
	private byte[] flipBytes(byte[] set) {
		int len = set.length;
		byte[] out = new byte[len];
		for(int i=0;i<len;i++)
			out[i] = set[len-i-1];
		return out;
	}
	
	//Adds a point to list of points/locations where a picture was taken
	public void newPicturePacket(byte[] pack) {
		float lat = 0, lng = 0;
		for(int i=2;i<pack.length-3;i+=4) {
			byte[] tmp = new byte[4];
			System.arraycopy(pack, i, tmp, 0, 4);
			tmp = flipBytes(tmp);
			if(i == 2)
				altAlt = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 6)
				lng = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 10)
				lat = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 14)
				altGPS = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 18)
				heading = ByteBuffer.wrap(tmp).getFloat();
		}
		picPoints.add(new tuple(lng, lat, altAlt, altGPS, heading));
	}
	
	//Method overload to make testing easiers
	public void newPacket(byte[] pack) {newPacket(pack, -1); }
	
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
	
	//Returns the average height reported between altimeter and GPS
	public float getHeight() {return (float)getAvg(altAlt, altGPS); }
	public double getAvg(double a, double b) {return (a + b) / 2; }
	
	//Method overload to easily export both fligh path and picture points
	public void exportData() {
		exportData(path, "flight");
		exportData(picPoints, "pics");
	}
	//Exports path data to a JSON object for use later
	/* JSON form:
	 * out { data [ point, point, point, ... ] }
	 *  |-> point: {long, lat, height, bearing}
	 */
	public void exportData(ArrayList<tuple> dataSet, String setName) {
		if(dataSet.isEmpty()) return; //No reason to export an empty file
		
		JSONObject out = new JSONObject(); //Base of JSON Object
		JSONArray data = new JSONArray();
		for(tuple pt : dataSet) {	//Adds each point from the given data set
			JSONObject tmp = new JSONObject();
			tmp.put("long", pt.x);
			tmp.put("lat", pt.y);
			tmp.put("height", getAvg(pt.h1, pt.h2));
			tmp.put("bearing", pt.head);
			data.put(tmp);
		}
		
		out.put("data", data); //Adds data array to base
		
		//Export to file
		String folderName = "data/";
		String fileName = folderName + setName + new SimpleDateFormat("'_'yyyy'_'MM'_'dd'_'HH'_'mm'.json'").format(new Date());
		if(!Files.exists(Paths.get(folderName))) {	//Makes a new folder if one is not already initialized
			new File(folderName).mkdir();
			log.info("Made a new folder for logged files");
		}
		try(FileWriter file = new FileWriter(fileName)) {
			file.write(out.toString());
			file.flush();
		} catch(IOException e) {
			log.severe("Failed to export data to file!");
			e.printStackTrace();
		}
	}
}
