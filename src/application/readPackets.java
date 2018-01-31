package application;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

public class readPackets implements Runnable {
	private static final Logger log = Logger.getLogger(readPackets.class.getName());
	private SynchronousQueue<Byte> byteBuff;
	private SerialCommunicator comm;
	private boolean inPack;
	private byte[] datPack;
	private int datInd, badData;
	private Thread t;
	private InputStream in;
	
	//Transmission constants (all private by default, not written for clarity)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	static final char KILL_THREAD = 'K';
	static final int PACKET_LENGTH = 38, BAD_COUNT = 50;
	
	public readPackets(SynchronousQueue<Byte> buff, SerialCommunicator _comm) {
		byteBuff = buff;
		comm = _comm;
		reset();
		datPack = new byte[PACKET_LENGTH];
		log.info("Started reader thread.");
	}
	private void clear() {
		log.severe("Clearing");
		byteBuff.clear();
		comm.flushInput();
		reset();
	}
	public Thread start()  {
		if(t == null) {
			t = new Thread (this, this.getClass().getName());
			t.start();
			return t;
		}
		return null;
	}
	private void reset() {
		badData = BAD_COUNT;
		inPack = false;
		datInd = 0;
	}
	@Override
	public void run()  {
		boolean badRun = false, pop = false, kickStart = false;
		char tmp = 'e', last = 'k';
		int st = 0;
		while(true) {
			//Checks
			if(Thread.currentThread().isInterrupted()) {
				log.info("Thread killed successfully.");
				return;
			}
			if(!comm.getState()) {
				in = null;
				continue;
			} else if(in == null)
				in = comm.getInputStream();
			
			try {if(in.available()  <= 0) {continue; }} catch(IOException e) {} //Don't proceed if there are not bytes availible to read.
			
			//Collection
			if(badData < 50 && !badRun)
				badData = 5;
			badRun = false;
			
			pop = true;
			while(pop) {
				try {
					tmp = (char)in.read();
					pop = false;
				} catch (IOException e){}
			}
			if(tmp == 'p' && last == '*') {
				kickStart = true;
				inPack = true;
				datPack = new byte[PACKET_LENGTH];
			}
			last = tmp;
			if(!inPack) {
				if(tmp == DROP_OPEN)
					log.info("Drop bay open.");
				else if(tmp == DROP_CLOSE)
					log.info("Drop bay closed.");
				else if(tmp == AUTO_ON_CONF)
					log.info("Auto drop enable confirmed.");
				else if(tmp == AUTO_OFF_CONF)
					log.info("Auto drop disable confirmed.");
				else if(tmp == PACKET_START) { //Start of data packet
					inPack = true;
					datPack = new byte[PACKET_LENGTH];
					datPack[0] = (byte)tmp;
					datInd++;
				}
				else {
					log.finer("Bad data received.");
					badRun = true;
					if(--badData < 1)
						clear();
				}
			} else if(inPack) { //Data packet
				if(kickStart) {
					datPack[0] = '*';
					datInd = 1;
					kickStart = false;
				}
				datPack[datInd] = (byte)tmp;
				if(datInd == 1 && (datPack[0] != '*' || datPack[1] != 'p'))
					reset();
				if(datPack[datInd] == 'e' && datInd > 1 && datPack[datInd-1] == 'e') {
					if(datInd == PACKET_LENGTH - 1) {
						//System.out.println("Good packet received.");
						new Thread(new Runnable() {
							@Override
							public void run() {
								comm.datM.newPacket(datPack);
							}
						}).start();
					}
					reset();
				}
				datInd++;
			}	
		}
	}
	/*
	@Override
	public void run() {
		boolean badRun = false, pop = false;
		int badData = 5;
		char tmp = 'e';
		while(true) {
			if(Thread.currentThread().isInterrupted()) {
				log.info("Thread killed successfully.");
				return;
			}
			if(badData < 5 && !badRun)
				badData = 5;
			badRun = false;
			if(comm.getState() && currPort != null && currPort.bytesAvailable() > 0) {
				pop = true;
				while(pop) {
					try {
						tmp = (char)byteBuff.remove().byteValue();
						pop = false;
					} catch (NoSuchElementException e){}
				}
				if(!inPack) {
					if(tmp == DROP_OPEN)
						log.info("Drop bay open.");
					else if(tmp == DROP_CLOSE)
						log.info("Drop bay closed.");
					else if(tmp == AUTO_ON_CONF)
						log.info("Auto drop enable confirmed.");
					else if(tmp == AUTO_OFF_CONF)
						log.info("Auto drop disable confirmed.");
					else if(tmp == PACKET_START) { //Start of data packet
						System.out.println("Pack start");
						inPack = true;
						datPack[0] = (byte)tmp;
						datInd++;
					}
					else {
						log.severe("Bad data received.");
						badRun = true;
						if(--badData < 1)
							clear();
					}
				} else if(inPack) { //Data packet
					datPack[datInd] = (byte)tmp;
					if(datInd == 1 && (datPack[datInd - 1] != '*' || datPack[datInd] != 'p'))
						clear();
					if(datPack[datInd] == 'e' && datInd > 1 && datPack[datInd-1] == 'e') {
						if(datInd != datPack.length - 1) {
							log.info("Good packet received.");
							new Thread(new Runnable() {
								@Override
								public void run() {
									comm.markPack();
									comm.datM.newPacket(datPack);
								}
							}).start();
						}
						else
							reset();
					}
					datInd++;
				}
			}
		}
	} */
}
