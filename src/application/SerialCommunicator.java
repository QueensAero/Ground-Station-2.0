package application;

/** Notes on Class:
 * Based on the code from the old Java Swing version of the ground station
 * Uses a serial library that (should) work for all operating systems and doesn't have any weird dependencies (to make it easier for people)
 * Reads and parses packets asynchronously by running a parallel thread (see readPackets class)
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class SerialCommunicator {
	private ArrayList<SerialPort> commList;
	protected SerialPort currPort;
	private InputStream in;
	private OutputStream out;
	private static final Logger log = Logger.getLogger(SerialCommunicator.class.getName());
	private boolean connected;
	private GUIController contrl;
	protected dataManager datM;
	protected String portName;
	protected CharBuffer incBuff;
	protected SynchronousQueue<Byte> byteBuff;
	private Thread readThread;
	private readPackets reader;
	
	
	//Connection constants
	private static final int BAUD_RATE = 115200;
	private static final String COMMAND_MODE_CMD = "+++", SWITCH_TO_AT_CMD = "ATAP0\r", EXIT_COMMAND_MODE_CMD = "ATCN\r", COMMAND_MODE_OK = "OK\r";
    private static final int INTO_CMD_MODE_TIMEOUT = 3000, RESPONSE_TIMEOUT = 750;
	
	//Constructor
	public SerialCommunicator(GUIController cont) {
		updateCommList();
		connected = false;
		contrl = cont;
		incBuff = CharBuffer.allocate(256);
		byteBuff = new SynchronousQueue<Byte>();
		resuscitate();
	}
	public void killThread()  {
		boolean kill = false;
		while(!kill) {
			readThread.interrupt();
			kill = !readThread.isAlive();
		}
	}
	public void resuscitate() {
		if(readThread != null && readThread.isAlive())
			return;
		reader = new readPackets(byteBuff, this);
		readThread = reader.start();
	}
	public boolean threadAlive() {return readThread.isAlive(); }
	public SerialPort getSerialPort() {return currPort; }
	protected InputStream getInputStream() {return in; }
	public void passDataM(dataManager _datM) {datM = _datM; }
	public void updateCommList() {commList = new ArrayList<SerialPort>(Arrays.asList(SerialPort.getCommPorts())); }
	public ArrayList<String> getCommList() {
		updateCommList();
		ArrayList<String> newList = new ArrayList<>();
		for(SerialPort pt : commList)
			newList.add(pt.getDescriptivePortName());
		return newList;
	}

	public int getPortId(String portName) {
		log.finer(portName + "'s index is at " + getCommList().indexOf(portName));
		return getCommList().indexOf(portName);
	}
	public boolean openConnection(int portId) {
		if(portId < 0 || commList.size() - 1 < portId) {
			log.fine("Port index out of range.");
			return false;
		}
		portName = commList.get(portId).getDescriptivePortName();
		currPort = SerialPort.getCommPort(commList.get(portId).getSystemPortName());
		if(!currPort.openPort()) {
			log.severe("Failed to open connection");
			return false;
		} 
		log.info("Connection established.");
		currPort.setBaudRate(BAUD_RATE);
		out = currPort.getOutputStream();
		in = currPort.getInputStream();
		contrl.connButtSt(true);
		flushInput(Integer.MAX_VALUE);
		initXbee();
		//initReadList();
		connected = true;
		
		return true;
	}
	public boolean closeConnection() {
		connected = false;
		if(currPort.closePort()) {
			log.info("Connection closed");
			in = null;
			out = null;
			return true;
		}
		log.severe("Failed to close connection!");
		connected = true;
		return false;
	}
	public void flushInput() {
		flushInput(200);
	}
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
	public boolean getState() {return connected; }
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
	public void sendByte(byte b) {
		if(!connected) {
			log.severe("Failed to send " + b + ", no connection.");
			return;
		}
		byte[] bA = {b};
		currPort.writeBytes(bA, 1);
		log.finest(b + " sent");
	}
	public void writeData(byte[] b) {
		if(!connected) {
			log.severe("Failed to send " + b + ", no connection.");
			return;
		}
		currPort.writeBytes(b, b.length); 
		log.finest(b + " sent");
	}
	public void initReadList() {
		currPort.addDataListener(new SerialPortDataListener() {
			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
			}
			@Override
			public void serialEvent(SerialPortEvent e) {
				if(e.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
					return;
				byte[] tmpBuff = new byte[currPort.bytesAvailable()];
				int numRead = currPort.readBytes(tmpBuff, tmpBuff.length);
				//datM.distribute(tmpBuff);
				toBuffer(tmpBuff);
				System.out.println(Integer.toString(numRead) + " bytes read.");
			}
		});
		log.info("Added listener.");
	}
	private void toBuffer(byte[] tmp) {
		int fail = -1;
		for(int i=0;i<tmp.length;i++) {
			try {
				byteBuff.put(tmp[i]);
			} catch(InterruptedException e) {
				if(fail == i) {
					log.severe("Failed to write " + tmp[i] + " to sync-buffer.");
					continue;
				}
				i--;
				fail = i;
			}
		}
	}
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
	public void markPack()  {
		System.out.println("New packet received.");
	}
}
