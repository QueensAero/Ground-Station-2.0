package application;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;
import com.fazecast.jSerialComm.SerialPort;

public class dataManager {
	private float distToOrg;
	private float distTrav;
	private ArrayList<tuple> path;
	private float altAlt, altGPS, speed, HDOP, sValid, battState;
	private int satelites;
	private String fixType;
	private GUIController cont;
	private SerialCommunicator comm;
	private static final Logger log = Logger.getLogger(dataManager.class.getName());
	
	//Transmission constants (all private by default, not written for clarity)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c', BATT_V = 'b';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	static final String[] FIX_TYPE = {"invalid", "GPS fix (SPS)", "DGPS fix",  "PPS fix", "Real Time Kinematic", "Float RTK", "estimated (dead reckoning) (2.3 feature)", "Manual input mode", "Simulation mode"};
		
	public dataManager(GUIController _cont) {
		cont = _cont;
		distToOrg = distTrav = battState = HDOP = 0;
		path = new ArrayList<tuple>();
	}
	public ArrayList<tuple> getPath() {return path; }
	public void passComm(SerialCommunicator _comm) {comm = _comm; }
	private void update() {
		synchronized(cont) {
			cont.orginDist.setText("Distance from origin: " + Float.toString(distToOrg) + "m");
			cont.travDist.setText("Distance travelled: " + Float.toString(distTrav) + "m");
			cont.batLevel.setText("Battery state: " + Float.toString(battState) + "%");
			cont.hdop.setText("HDOP: " + Float.toString(HDOP));
			if(comm != null && comm.getState())
				cont.connState.setText("Connection state: Connected to " + comm.portName);
			else
				cont.connState.setText("Disconnected.");
		}
	}
	public void openBay() {comm.sendByte((byte)DROP_OPEN); }
	public void closeBay() {comm.sendByte((byte)DROP_CLOSE); }
	public void newPoint(float latt, float lon){
		if(path.size()  < 1) {
			path.add(new tuple((float)Math.toRadians(latt),(float)Math.toRadians(lon)));
			return;
		}
		tuple newLoc = new tuple((float)Math.toRadians(latt),(float)Math.toRadians(lon));	
		float distFromLast = getDistance(path.get(path.size()-1),newLoc);
		//check if new location more than 200m away from last point, if so don't add point
		if(distFromLast > 1 && distFromLast<=200){
			distTrav = distTrav + distFromLast;
			distToOrg = getDistance(path.get(0),newLoc);
			path.add(newLoc);
		}		
	}
	private float getDistance(tuple loc1, tuple loc2){
		float dlatt = loc2.x-loc1.x;
		float dlong = loc2.y-loc1.y;
		double a = Math.pow(Math.sin(dlatt/2),2) + Math.cos(loc1.x) * Math.cos(loc2.x) * Math.pow(Math.sin(dlong/2),2);
		float c = (float) (2 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a)));
		float dist = 6371 * c;
		return dist*1000; 	//dist in meters
	}
	public float getDistanceTraveled(){return distTrav; }
	public float distanceFromStart(){return distToOrg; }
	public void newPacket(byte[] pack) {
		float lat = 0, lng = 0;
		for(int i=2;i<31;i+=4) {
			byte[] tmp = new byte[4];
			System.arraycopy(pack, i, tmp, 0, 4);
			if(i == 2)
				altAlt = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 6)
				speed = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 10)
				lat = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 14)
				lng = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 18)
				HDOP = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 22)
				sValid = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 26)
				altGPS = ByteBuffer.wrap(tmp).getFloat();
			else if(i == 30)
				battState = ByteBuffer.wrap(tmp).getFloat();
		}
		newPoint(lat, lng);
		try {
			fixType = FIX_TYPE[pack[34]];
			satelites = pack[35];
			System.out.println(fixType + " " + satelites + " " + battState);
		} catch(ArrayIndexOutOfBoundsException e) {log.severe("You fucked up... fix type doesn't exist"); }
		//update();
		//System.out.println("Parsed packet successfully.");
	}
}
