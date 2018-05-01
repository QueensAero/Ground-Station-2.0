package com.QADT;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class readPackets implements Runnable {
	//Utility
	private static final Logger log = Logger.getLogger(readPackets.class.getName());
	private SerialCommunicator comm;
	private Thread t;
	private InputStream in;
	private char packetType;
	
	//Reading attributes
	private boolean inPack, picPack;
	private byte[] datPack;
	private int datInd, badData;

	//Transmission constants (all private by default)
	static final char DROP_OPEN = 'o', DROP_CLOSE = 'c';
	static final char AUTO_ON = 'a', AUTO_OFF = 'n';
	static final char AUTO_ON_CONF = 'b', AUTO_OFF_CONF = 'd';
	static final char PACKET_START = '*', PACKET_END = 'e';
	static final char KILL_THREAD = 'K';
	static final char PICTURE_START = 't', DATA_START =  'p';
	protected static final int PACKET_LENGTH = 42, BAD_COUNT = 50, PICTURE_LENGTH = 24;

	//Initializes class
	public readPackets(SerialCommunicator _comm) {
		GUIController.addLogHandler(log);
		comm = _comm;
		reset();
		log.fine("Reader thread up.");
	}
	
	//Clears variables and buffer to prepare to get a fresh packet
	private void clear() {
		log.warning("Clearing");
		comm.flushInput();
		reset();
	}
	
	//Initializes thread
	public Thread start()  {
		if(t == null) {
			t = new Thread (this, this.getClass().getName());
			t.start();
			return t;
		}
		return null;
	}
	
	//Resets reading constants
	private void reset() {
		badData = BAD_COUNT;
		inPack = picPack = false;
		datInd = 0;
	}
	
	//Prints current pack (used for debugging)
	private void printPack(byte[] pack) {
		for(byte bit : pack) {
			System.out.print((char)bit);
		}
		System.out.println(" - Pack length: "  + pack.length);
	}
	
	//Initializes the start of a new packet based on identification character
	private void initPack(char tmp) {
		inPack = true;
		picPack = tmp == PICTURE_START;
		if(picPack) {
			datPack = new byte[PICTURE_LENGTH];
			packetType = PICTURE_START;
		} else {
			packetType = DATA_START;
			datPack = new byte[PACKET_LENGTH];
		}
	}
	
	//Execution loop/method
	@Override
	public void run()  {
		boolean badRun = false, pop = false, kickStart = false;
		char tmp = 'e', last = 'k';
		while(true) {
			//Checks
			if(Thread.currentThread().isInterrupted()) {	//Effectively kills the thread
				log.info("Thread killed successfully.");
				return;
			}
			if(!comm.getState()) {	//Breaks reading if there is no connection
				in = null;
				continue;
			} else if(in == null)	//Initializes input stream if connection is present
				in = comm.getInputStream();

			try {if(in.available()  <= 0) {continue; }} catch(IOException e) {} //Doesn't proceed if bytes aren't available 
			
			
			if(badData < BAD_COUNT && !badRun)
				badData = BAD_COUNT;
			badRun = false;

			//Pops the top byte off of the input queue
			pop = true;
			while(pop) {
				try {
					tmp = (char)in.read();
					pop = false;
				} catch (IOException e){}
			}
			
			//Identifies if the byte received is the start of a new packet (and which type of packet)
			if((tmp == DATA_START || tmp == PICTURE_START || tmp == 0x0) && last == PACKET_START) {
				kickStart = true;
				initPack(tmp); //Identifies the type of packet
				datPack[0] = (byte)last;
				datPack[1] = (byte)tmp;
			}
			last = tmp;
			
			
			//Behavior when looking for the beginning of a new packet
			if(!inPack) {
				if(tmp == PACKET_START) { //Start of data packet
					initPack(tmp);
					datPack[0] = (byte)tmp;
					datInd++;
				}
				else {
					log.finer("Bad data received.");	//If too many bad bytes are received input is cleared in hopes of finding a good packet
					badRun = true;
					System.out.println("Out pack clear");
					if(--badData < 1)
						clear();
				}
			} else if(inPack) { //Packet identified, parsing...
				if(kickStart) {
					datPack[0] = PACKET_START;
					datInd = 1;
					kickStart = false;
				}
				datPack[datInd] = (byte)tmp;	//Adds new byte to incoming byte array
				if(datInd == 1 && (datPack[0] != PACKET_START || datPack[1] != packetType))	//Checks if the second byte is wrong...
					reset();
				else if(datInd == 2 && datPack[0] == PACKET_START && datPack[1] == 0x0) {	//This actually makes no sense, need to review heavily
					if(tmp == DROP_OPEN)
						log.info("Drop bay open.");
					else if(tmp == DROP_CLOSE)
						log.info("Drop bay closed.");
					else if(tmp == AUTO_ON_CONF)
						log.info("Auto drop enable confirmed.");
					else if(tmp == AUTO_OFF_CONF)
						log.info("Auto drop disable confirmed.");
					else if(tmp == PACKET_START) { //Start of data packet
						/*
						inPack = true;
						datPack = new byte[PACKET_LENGTH];
						datPack[0] = (byte)tmp;
						datInd++; */
					} else {
						clear();
						continue;
					}
					clear();
				} else if(datInd > 1 && datPack[datInd] == PACKET_END && datPack[datInd-1] == PACKET_END) {	//Identify end of packet
					if(datInd == PACKET_LENGTH - 1 || datInd == PICTURE_LENGTH - 1) { //Confirm good packet
						new Thread(new Runnable() {	//Send packet to process using another thread so it doesn't delay further reading
							@Override
							public void run() {
								if(datPack[1] == PICTURE_START) {
									comm.datM.newPicturePacket(datPack);
									return;
								}
								try {
									comm.updatePackTime();
									comm.datM.newPacket(datPack, in.available());
								} catch(IOException e) {
									comm.datM.newPacket(datPack);
								}
							}
						}).start();
					} else {printPack(datPack); }
					reset();
				}
				datInd++;
			}
		}
	}
}
