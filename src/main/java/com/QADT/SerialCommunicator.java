package com.QADT;

/** Notes on Class:
 * Based on the code from the old Java Swing version of the ground station
 * Uses a new serial library that (should) work for all operating systems and doesn't have any weird dependencies (to make it easier for people)
 * Reads and parses packets mostly asynchronously by running a parallel thread (see readPackets class)
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import com.fazecast.jSerialComm.SerialPort;

public class SerialCommunicator {
	//Utility constants
	private static final Logger log = Logger.getLogger(SerialCommunicator.class.getName());
	private GUIController contrl;
	protected dataManager datM;
	private readPackets reader;
	private static Thread readThread;
	private Timer timer;
	
	//Communication objects
	private ArrayList<SerialPort> commList;
	protected SerialPort currPort;
	private InputStream in;
	private OutputStream out;
	
	//Misc global variables
	private boolean connected, readFirstLaunch = true;
	protected String portName;
	protected long packsIn, packetTime;
	protected float packRate;
	private static boolean hold;

	//Connection constants
	private static final int BAUD_RATE = 115200;
	private static final String COMMAND_MODE_CMD = "+++", SWITCH_TO_AT_CMD = "ATAP0\r", EXIT_COMMAND_MODE_CMD = "ATCN\r", COMMAND_MODE_OK = "OK\r";
    private static final int INTO_CMD_MODE_TIMEOUT = 3000, RESPONSE_TIMEOUT = 750;

	//Constructor
	public SerialCommunicator(GUIController cont, dataManager _datM) {
		GUIController.addLogHandler(log);
		hold = false;
		datM = _datM;
		contrl = cont;
		datM.passComm(this);
		connected = false;
		resuscitate();
		packsIn = -1;
		updatePackTime();
	}
	
	//Extension of default timer to periodically check status of connection and print data
    class ThreadCheck extends TimerTask  {
    	public boolean running;
    	
    	@Override
    	public void run() {
    		//Tries to print the time since the last packet
    		try {
    			datM.printPackTime();
    		} catch(Exception e) {
    			log.severe("Pack time error!");
    			e.printStackTrace();
    		}
    		
    		//Tries to rescue the reader thread in the case that it crashes
    		try {
        		resuscitate();
    		} catch(Exception e) {
    			log.severe("Reader thread error!");
    			e.printStackTrace();
    		}
    		
    		//Updates warnings
    		contrl.updateWarnStyle();
    	}
    }
    
    //Method to kill the thread on exit
	public void killThread()  {
		timer.cancel();
		timer = null;
		boolean kill = false;
		while(!kill) {
			readThread.interrupt();
			kill = !readThread.isAlive();
		}
	}
	
	//Initializes reader and/or saves it if it crashess
	public void resuscitate() {
		if(hold) return;	//Make sure threads aren't double launched
		hold = true;
		
		//Get Status of threads
		boolean read = readThread == null, time = timer == null;
		
		//Launch timer if its not started
		if(time) {
			timer = new Timer();
			timer.schedule(new ThreadCheck(), 0, 100);
			log.info("Timer was started!");
		}
		//Launch reader if its not alive
		if(read || !readThread.isAlive()) {
			reader = new readPackets(this);
			readThread = reader.start();
			if(read && readFirstLaunch)
				log.info("Packet reader started!");
			else if(read)
				log.severe("Thread was just resuscitated!");
		}
		hold = false;
	}
	//Updates pack time when a new packet is received
    protected void updatePackTime() {
    	packRate = (float)1000/getPackTime();
    	packetTime = System.currentTimeMillis();
    	packsIn++;
	}
    //Calculates time since last data packet
    protected long getPackTime() {return System.currentTimeMillis() - packetTime; }
	
    //Returns whether the reader is alive
    public boolean threadAlive() {return readThread.isAlive(); }
	
    //Returns the serial port object
    public SerialPort getSerialPort() {return currPort; }
    
    //Returns input stream
	protected InputStream getInputStream() {return in; }
	
	//Returns port status
	public boolean getPortStatus() {
		if(currPort != null)
			return currPort.isOpen();
		return false;
	}
	
	//Updates the list of active serial ports
	public void updateCommList() {commList = new ArrayList<SerialPort>(Arrays.asList(SerialPort.getCommPorts())); }
	
	//Returns list of active serial devices
	public ArrayList<String> getCommList() {
		updateCommList();
		ArrayList<String> newList = new ArrayList<>();
		for(SerialPort pt : commList)
			newList.add(pt.getDescriptivePortName());
		return newList;
	}
	
	//Get ID of a given serial port
	public int getPortId(String portName) {
		log.finer(portName + "'s index is at " + getCommList().indexOf(portName));
		return getCommList().indexOf(portName);
	}
	
	//Establishes connection with a given serial device
	public boolean openConnection(int portId) {
		if(portId < 0 || commList.size() - 1 < portId) { //Ensure device ID is valid
			log.fine("Port index out of range.");
			return false;
		}
		portName = commList.get(portId).getDescriptivePortName();
		currPort = SerialPort.getCommPort(commList.get(portId).getSystemPortName());
		if(!currPort.openPort()) {	//Attempt to open connection
			log.severe("Failed to open connection");
			return false;
		}
		log.info("Connection established.");
		currPort.setBaudRate(BAUD_RATE);	//Sets connection speed
		out = currPort.getOutputStream();
		in = currPort.getInputStream();
		contrl.connButtSt(true);	//Enables ability to close connection
		flushInput(Integer.MAX_VALUE);	//Flushes any data that had tried to be pushed to the port previously
		initXbee();		//Sends specific constants to configure connection to Xbee
		connected = true;

		return true;
	}
	
	//Closes serial connection
	public boolean closeConnection() {
		if(!connected) {	//Breaks if there is no connection established
			log.warning("Port was already closed.");
			return true;
		}
		connected = false;
		if(currPort.closePort()) {	//Closes connection
			log.info("Connection closed");
			in = null;
			out = null;
			return true;
		}
		log.severe("Failed to close connection!");
		connected = true;
		return false;
	}
	
	//Clears bytes in input buffer
	public void flushInput() {
		flushInput(200);
	}
	
	//Clears a specific number of bytes from the input buffer
	public void flushInput(int lim) {
		int limit = 0;
		try {
			while(in.available() > 0 && ++limit < lim)
				in.read();
		} catch (IOException e) {
			log.severe("Failed to read input buffer");
			log.severe(e.toString());
		}
		log.finer("Successfully flushed input buffer");
	}
	
	//Returns the state of the serial connection
	public boolean getState() {return connected; }
	
	//Returns information about the current serial connection
	public void getConnInfo() {
		if(!connected) {
			log.info("Not connected.");
			return;
		}
		String info = "Connected to: " + portName;
		info += ", running at "  + currPort.getBaudRate() + " baud";
		info += ", with " + currPort.getNumDataBits() + " data bits";
		info += ", with a timeout of " + currPort.getReadTimeout() + "ms for read, and ";
		info += currPort.getWriteTimeout() + "ms for write";
		log.info(info);
	}
	
	//Sends a given byte
	public void sendByte(byte b) {
		if(!connected) {
			log.severe("Failed to send " + b + ", no connection.");
			return;
		}
		byte[] bA = {b};
		currPort.writeBytes(bA, 1);
		log.finest(b + " sent");
	}
	
	//Sends an array of bytes to serial device
	public void writeData(byte[] b) {
		if(!connected) {
			log.severe("Failed to send " + b + ", no connection.");
			return;
		}
		currPort.writeBytes(b, b.length);
		log.finest(b + " sent");
	}
	
	//Write a byte to the device then waits (used to initialize Xbee)
	public boolean writeAndWait(String cmd, int time) {
		int tries = 3;
		try {
			while(tries-- > 0) {
				flushInput();
				String bf = "";
				long t = System.currentTimeMillis();
				for(byte bt : cmd.getBytes())
					out.write(bt);
				while(System.currentTimeMillis() - t < time) {
					if(currPort.bytesAvailable() > 0) {
						bf += Character.toString((char)in.read());
						if(bf.contains(COMMAND_MODE_OK))
							return true;
					}
				}
			}
		} catch(Exception e)  {
			log.severe("Failed to write " + cmd + " to Xbee");
			log.severe(e.toString());
		}
		return false;
	}
	
	//Initializes Xbee by sending a specific sequence of bytes seen below
	//See Xbee manual for meaning of each command (essentially just changing the operating mode of it)
	private boolean initXbee() {
		if(!writeAndWait(COMMAND_MODE_CMD, INTO_CMD_MODE_TIMEOUT)) {
			log.severe("Failed to enter command mode.");
			return false;
		}
		log.finer("Xbee in command mode.");
		if(!writeAndWait(SWITCH_TO_AT_CMD, RESPONSE_TIMEOUT)) {
			log.severe("Failed to enter transparent mode.");
			return false;
		}
		log.finer("Xbee in transparent mode");
		if(!writeAndWait(EXIT_COMMAND_MODE_CMD, RESPONSE_TIMEOUT)) {
			log.severe("Failed to exit command mode.");
			return false;
		}
		log.finer("Xbee successfully out of command mode.");
		log.info("Xbee successfully initialized.");
		return true;
	}
}
