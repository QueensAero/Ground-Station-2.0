package application;

import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

public class readPackets implements Runnable {
	private static final Logger log = Logger.getLogger(readPackets.class.getName());
	private SynchronousQueue<Byte> byteBuff;
	private SerialCommunicator comm;
	private boolean inPack, inBatt;
	private char[] datPack, battPack;
	private int datInd, battInd;
	
	//Transmission constants (all private by default, not written for clarity)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c', BATT_V = 'b';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	
	public readPackets(SynchronousQueue<Byte> buff, SerialCommunicator _comm) {
		byteBuff = buff;
		comm = _comm;
		inPack = inBatt = false;
		datPack = new char[5];
		battPack = new char[27];
	}
	private void clear() {
		byteBuff.clear();
		comm.flushInput();
		inPack = inBatt = false;
	}
	@Override
	public void run() {
		boolean badRun = false;
		int badData = 5;
		char tmp;
		while(true) {
			if(badRun && badData < 5)
				badData = 5;
			badRun = false;
			if(byteBuff.size() > 0) {
				tmp = (char)byteBuff.poll().byteValue();
				if(!inPack && !inBatt) {
					if(tmp == DROP_OPEN)
						log.info("Drop bay open.");
					else if(tmp == DROP_CLOSE)
						log.info("Drop bay closed.");
					else if(tmp == AUTO_ON_CONF)
						log.info("Auto drop enable confirmed.");
					else if(tmp == AUTO_OFF_CONF)
						log.info("Auto drop disable confirmed.");
					else if(tmp == BATT_V) {
						inBatt = true;
						battPack[0] = tmp;
					}
					else if(tmp == PACKET_START) {
						inPack = true;
						datPack[0] = tmp;
						datInd++;
					}
					else {
						log.severe("Bad data received.");
						badRun = true;
						if(--badData < 1)
							clear();
					}
				} else if(inPack) { //Data packet
					datPack[datInd] =  tmp;
					if(datPack[datInd] == 'e' && datInd > 1 && datPack[datInd-1] == 'e')
						clear();
					datInd++;
				} else { //In battery packet
					
				}
			}
		}
	}
	/*
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
	*/
}
