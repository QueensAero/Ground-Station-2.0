package application;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
	private static ByteBuffer buff;
	private static final Logger log = Logger.getLogger(dataManager.class.getName());
	private static final DecimalFormat df = new DecimalFormat("#.##");
	
	//Transmission constants (all private by default, not written for clarity)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c', BATT_V = 'b';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	static final String[] FIX_TYPE = {"invalid", "GPS fix (SPS)", "DGPS fix",  "PPS fix", "Real Time Kinematic", "Float RTK", "estimated (dead reckoning) (2.3 feature)", "Manual input mode", "Simulation mode"};
		
	public dataManager(GUIController _cont) {
		cont = _cont;
		distToOrg = distTrav = battState = HDOP = altAlt = altGPS = speed = sValid = satelites = btsAv = 0;
		path = new ArrayList<tuple>();
		update();
	}
	public ArrayList<tuple> getPath() {return path; }
	public void passComm(SerialCommunicator _comm) {comm = _comm; }
	public void printPackTime() {
		double packDelta = (double)comm.getPackTime() / 1000;
		Platform.runLater(new Runnable() {
			@Override
			public void run() {cont.packTime.setText("Pack Time: " + df.format(packDelta) + 's'); }
		});
	}
	private void update() {
		Platform.runLater(new Runnable()  {
			@Override
			public void run() {
				cont.orginDist.setText("Distance from origin: " + Float.toString(distToOrg) + "m");
				cont.travDist.setText("Distance travelled: " + Float.toString(distTrav) + "m");
				cont.batLevel.setText("Battery state: " + df.format(battState) + " V");
				cont.hdop.setText("HDOP: " + Float.toString(HDOP));
				cont.height.setText("Height: " + getHeight());
				cont.satNum.setText("Satellites: " + satelites);
				cont.bytesAvail.setText("Bytes availible: " + btsAv);
				cont.packetsR.setText("Packets received: " + comm.packsIn);
				cont.packRateLabel.setText("Pack rate: " + df.format(comm.packRate) + " packs/s");
				cont.speed.setText("Speed: " + speed);
				cont.vHDOP.setText("Time since valid HDOP: " + sValid + "s");
				cont.fixTp.setText("Fix Type: " + fixType);
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
	public void newPacket(byte[] pack) {newPacket(pack, -1); }
	private byte[] flipBytes(byte[] set) {
		if(set.length % 2 != 0)
			return set;
		byte[] out = new byte[set.length];
		//for(int i=0;i<set.length;i++)
		//	set[i] = (byte)((byte)(set[i] << 4) | (set[i] >> 4));
		int len = set.length;
		for(int i=0;i<len;i++)
			out[i] = set[len-i-1];
		return out;
	}
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
		btsAv = _btsAv;
		try {
			fixType = FIX_TYPE[pack[34]];
			satelites = pack[35];
		} catch(ArrayIndexOutOfBoundsException e) {log.severe("You fucked up... fix type doesn't exist"); }
		update();
	}
	public float getHeight() {return (altAlt + altGPS) / 2; }
}
