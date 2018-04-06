package application;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.logging.Logger;
import javafx.application.Platform;

public class dataManager {
	private float distToOrg, distTrav;
	private ArrayList<tuple> path;
	private float altAlt, altGPS, speed, HDOP, sValid, battState;
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
		log.addHandler(GUIController.taHandle);
		log.addHandler(GUIController.filehandle);
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
	public void newPoint(float latt, float lon){
		if(path.size() < 1) {
			path.add(new tuple(latt,lon));
			return;
		}
		tuple newLoc = new tuple(latt,lon);
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
		}
		if(pack[34] != 0 && HDOP < 3)
			newPoint(lat, lng);

		btsAv = _btsAv;
		try {
			fixType = FIX_TYPE[pack[34]];
			satelites = pack[35];
		} catch(ArrayIndexOutOfBoundsException e) {log.severe("You fucked up... fix type doesn't exist"); }
		update();
	}
	public float getHeight() {return (altAlt + altGPS) / 2; }
}
