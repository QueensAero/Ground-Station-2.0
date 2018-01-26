package application;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;
import com.fazecast.jSerialComm.SerialPort;

public class dataManager {
	private float distToOrg;
	private float distTrav;
	private ArrayList<tuple> path;
	private float battState;
	private float HDOP;
	private GUIController cont;
	private SerialCommunicator comm;
	private SerialPort actPort;
	private static final Logger log = Logger.getLogger(dataManager.class.getName());
	
	//Transmission constants (all private by default, not written for clarity)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c', BATT_V = 'b';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
		
	public dataManager(GUIController _cont) {
		cont = _cont;
		distToOrg = distTrav = battState = HDOP = 0;
		path = new ArrayList<tuple>();
	}
	public void passComm(SerialCommunicator _comm) {comm = _comm; }
	private void update() {
		cont.orginDist.setText("Distance from origin: " + Float.toString(distToOrg) + "m");
		cont.travDist.setText("Distance travelled: " + Float.toString(distTrav) + "m");
		cont.batLevel.setText("Battery state: " + Float.toString(battState) + "%");
		cont.hdop.setText("HDOP: " + Float.toString(HDOP));
		if(comm != null && comm.getState())
			cont.connState.setText("Connection state: Connected to " + comm.portName);
		else
			cont.connState.setText("Disconnected.");
	}
	public void addPort(SerialPort _actPort) {
		actPort = _actPort;
	}
	public void distribute(byte[] incom) {
		for(int i=0;i<incom.length;i++)  {
			if(incom[i] == DROP_OPEN)
				log.info("Drop bay open.");
			else if(incom[i] == DROP_CLOSE)
				log.info("Drop bay closed.");
			else if(incom[i] == AUTO_ON_CONF)
				log.info("Auto drop enable confirmed.");
			else if(incom[i] == AUTO_OFF_CONF)
				log.info("Auto drop disable confirmed");
			else if(incom[i] == BATT_V) {
				byte[] bt = new byte[4];
				try {
				for(int j=0;j<8;j++)
					bt[j] = incom[i+j];
				} catch(ArrayIndexOutOfBoundsException e) {
					log.severe("Could not read battery level.");
				}
				battState =  byteToFloat(bt);
				log.fine("Battery level at: " + battState);
				i += 4;
			}
			else if(incom[i] == PACKET_START) {
				int j;
				String packet = "";
				try {
					for(j=0;incom[i+j]!='e';j++)  {
						packet += incom[i+j];
						
					}
					log.info("Packet = " + packet);
					i += j;
				} catch(ArrayIndexOutOfBoundsException e){
					log.severe("Failure in reading packet.");
				}
			}
			else if(incom[i]== PACKET_END)
				log.finest("End of packet.");
			else
				log.severe("Bad data received.");
		}
		update();
	}
	private float byteToFloat(byte[] bt) {return ByteBuffer.wrap(bt).getFloat(); }
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
		if(distFromLast<=200){
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
	public float getDistanceTraveled(){
		return distTrav;
	}
	public float distanceFromStart(){
		return distToOrg;
	}
}
