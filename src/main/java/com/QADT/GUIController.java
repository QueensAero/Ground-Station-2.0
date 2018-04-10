package com.QADT;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.json.*;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.core.Core;

public class GUIController {
	static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
	
	//FXML declarations
	@FXML
	Pane MapPane, videoPane;
	@FXML
	GridPane mainPane;
	@FXML
	MenuButton mapList, connMenu;
	@FXML
	TextArea infoPane;
	@FXML
	Button connInfo, discButt, mapUpdate, tempButt, closeButt, conUpButt, cameraControl;

	//Status labels
	//@FXML
	Canvas testCan, mapCan;
	@FXML
	Label vHDOP, packTime, speed, bytesAvail, satNum, heightL, pointsTaken, mapTop, mapBase;
	@FXML
	Label orginDist, travDist, batLevel, hdop, connState, packetsR, packRateLabel, fixTp;
	
	//Warnings
	@FXML
	Label xbeeState, GPSState, battState, serialState, mapState, onMapStatus;
	private boolean xbeeWarn, GPSWarn, battWarn, serialWarn, warnChange, mapWarn;
	
	//Warning methods
	public void setXW(boolean st) {xbeeWarn = st; warnC(); }
	public void setGW(boolean st) {GPSWarn = st; warnC(); }
	public void setBW(boolean st) {battWarn = st; warnC(); }
	public void setSW(boolean st) {serialWarn = st; warnC(); }
	public boolean getXW() {return xbeeWarn; }
	public boolean getGW() {return GPSWarn; }
	public boolean getBW() {return battWarn; }
	public boolean getSW() {return serialWarn; }
	private void warnC() {warnChange = true; }
	
	//Map variables
	private ArrayList<tuple> path; 
	private GraphicsContext gc;
	protected int lastPoint = -1, mapRel = 0, pt = -1;
	private String mapName;
	private double baseLng, baseLat, topLng, topLat;
	private float baseXOffset = 0, baseYOffset = 0;
	
	//Video variables
	private Canvas vidCan;
	private GraphicsContext gv;
	private ImageView imV;
	private VideoCapture vc;
	private boolean videoActive = false;
	Timer videoTimer;
	
	//Communication
	SerialCommunicator comm;
	dataManager datM;
	OutputStream infOut;
	private static final Logger log = Logger.getLogger(GUIController.class.getName());
	FileHandler filehandle = null;
	
	
	
	@FXML
	public void initialize() {
		datM = new dataManager(this);	//Works with bytes from comm
		comm = new SerialCommunicator(this, datM);	//Raw communication with Xbee
		path = datM.getPath();
		vc = new VideoCapture();
		connButtSt(false);
		
		//Setup logger
		FileHandler filehandle = null;
		try {
			String fileName = new SimpleDateFormat("'log_'yyyy'_'MM'_'dd'_'HH'_'mm'.txt'").format(new Date());
			filehandle = new FileHandler(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
		TextAreaHandler taHandle = new TextAreaHandler();
		taHandle.setTextArea(infoPane);
		log.addHandler(taHandle);
		log.addHandler(filehandle);
		
		log.fine("GUIController Initializing.");
		xbeeWarn = GPSWarn = battWarn = serialWarn = mapWarn = true;
		
		//Adding listeners for camera movement with arrow keys
		cameraControl.setOnKeyPressed(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				switch(event.getCode().toString()) {
					case "W":
						camUp();
						log.fine("Camera going up\n");
						break;
					case "A":
						camLeft();
						log.fine("Camera going left\n");
						break;
					case "S":
						camDown();
						log.fine("Camera going down\n");
						break;
					case "D":
						camRight();
						log.fine("Camera going right\n");
						break;
				}
			}
		});
	}
	
	//Initializes the canvas
	public void initCanvas() {
		if(mapCan != null) return;
		mapCan = new Canvas(MapPane.getWidth(), MapPane.getHeight());
		MapPane.getChildren().add(mapCan);
		gc = mapCan.getGraphicsContext2D();
		gc.setStroke(Color.RED);
		gc.setLineWidth(5);
		log.info("Canvas initialized.");
	}
	
	//Draws the path of the aircraft from scratch
	public void drawPath() {
		if(mapCan == null)
			drawMap();
		if(lastPoint != -1) {
			updatePath();
			return;
		}
		gc.setStroke(Color.RED);
		gc.setLineWidth(3);
		tuple pt;
		gc.beginPath();
		for(tuple _pt : path) {
			pt = toCanvas(_pt);
			if(pt.x == -1)
				continue;
			/*
			else if(pt == path.get(0))
				gc.moveTo(pt.x, pt.y);
			else
				gc.lineTo(pt.x, pt.y);
				*/
			else
				gc.strokeRect(pt.x, pt.y, 2, 2);
		}
		lastPoint = path.size() - 1;
		gc.stroke();
		log.info("Path drawn.");
	}
	
	private void initVideoCan() {
		if(vidCan != null) return;
		vidCan = new Canvas(videoPane.getWidth(), videoPane.getHeight());
		videoPane.getChildren().add(vidCan);
		gv = vidCan.getGraphicsContext2D();
		gv.setStroke(Color.BLACK);
		gv.setLineWidth(5);
		imV = new ImageView();
		videoPane.getChildren().add(imV);
		imV.setFitHeight(videoPane.getHeight());
		imV.setPreserveRatio(true);
		imV.setLayoutX((vidCan.getWidth() - (imV.getFitHeight() * 4/3)) / 2);
		log.info("Canvas initialized.");
	}
	
	@FXML
	public void openStream() {
		initCanvas();
		if(!videoActive) runVideo();
		else killTimer();
	}
	protected void killTimer() {
		if(videoTimer != null && videoActive){
			videoTimer.cancel();
			videoTimer = null;
			videoActive = false;
			log.info("Video stream closed.");
		}
	}
	class frameCapture extends TimerTask {
		@Override
		public void run() {
			if(!videoActive) return;
			Mat frame = new Mat(); 
			vc.read(frame);
			if(frame.empty()){log.severe("Video capture failed."); killTimer(); return;}
			final Image newIm = matConv(frame);
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					imV.setImage(newIm);
				}
			});
		}
	}
	
	public void runVideo() {
		//Initialization
		initVideoCan();
		if(videoActive) return;
		
		//Open video stream
		vc.open(0);
		if(!vc.isOpened()) {log.severe("Video capture failed to start"); return;}
		else log.info("Video stream opened.");
		
		videoActive = true;
		if(videoTimer == null) {videoTimer = new Timer();}
		videoTimer.schedule(new frameCapture(), 0, 33);
	}
	
	private static Image matConv(Mat raw) {
		BufferedImage bim = null;
		int width = raw.width(), height = raw.height(), chan = raw.channels();
		byte[] src = new byte[width * height * chan];
		raw.get(0, 0, src);
		
		if(chan > 1) 
			bim = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		else 
			bim = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		
		final byte[] targ = ((DataBufferByte) bim.getRaster().getDataBuffer()).getData();
		System.arraycopy(src, 0, targ, 0, src.length);
		Image img = SwingFXUtils.toFXImage(bim, null);
		
		return img;
	}
	
	public void testDraw() {
		ArrayList<tuple> arr = new ArrayList<>();
		arr.add(new tuple(baseLng, baseLat));
		arr.add(new tuple(baseLng, topLat));
		arr.add(new tuple(topLng, topLat));
		arr.add(new tuple(topLng, baseLat));
		
		//ILC and Union-University
		arr.add(new tuple(-76.492984, 44.227752));
		arr.add(new tuple(-76.495606, 44.227921));
		
		for(int i=0;i<10;i++) 
			arr.add(new tuple(-76.495618 + 0.000100*i, 44.227920));
		
		if(pt >= arr.size()) {return; }
		gc.setStroke(Color.RED);
		gc.setLineWidth(5);
		tuple point = toCanvas(arr.get(++pt));
		gc.strokeRect(point.x, point.y, 10, 10);
		
		log.info("Point drawn.");
	}
	//Updates the path (as not to redraw all previous points
	public void updatePath() {
		if(path.size() == lastPoint + 1)
			return;
		gc.setStroke(Color.RED);
		gc.setLineWidth(3);
		int i;
		tuple pt;
		gc.beginPath();
		for(i=lastPoint;i<path.size();i++)  {
			pt = toCanvas(path.get(i));
			if(pt.x == -1)
				continue;
			else if(i == lastPoint)
				gc.strokeRect(pt.x, pt.y, 4, 4);
				//gc.moveTo(pt.x, pt.y);
			else
				gc.strokeRect(pt.x, pt.y, 4, 4);
				//gc.lineTo(pt.x, pt.y);
		}
		gc.stroke();
		log.finer("Path updated.");
	}
	//Draws the map onto the page
	public void drawMap() {
		if(mapName == null)  {
			log.severe("Map doesn't exist!");
			return;
		}
		if(!getMapData(mapName)) {return; }
		initCanvas();
		Image im;
		try {
			im = new Image(new FileInputStream(mapName + ".jpg"));
		} catch (FileNotFoundException e) {
			log.severe("Map file not found.");
			log.severe(e.toString());
			return;
		}
		float scl = getScale(im);
		//System.out.println(baseXOffset + " - " + baseYOffset);
		gc.drawImage(im, 0, 0, scl*im.getWidth(), scl*im.getHeight());
		log.info("Map drawn");
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				mapTop.setText("Map top: " + datM.getFormatted4(topLng) + ", " + datM.getFormatted4(topLat));
				mapBase.setText("Map base: " + datM.getFormatted4(baseLng) + ", " + datM.getFormatted4(baseLat));
			}
		}); 
	}
	@FXML
	public void checkThread() {
		if(comm.threadAlive())
			log.info("Thread is alive and well.");
		else {
			log.info("Thread is dead!");
			comm.resuscitate();
		}
	}
	@FXML
	public void close() {
		comm.killThread();
		killTimer();
		if(comm.getState() && !comm.closeConnection()) {
			log.severe("Failed to close connection");
			return;
		}
		log.info("Interface closed.");
		((Stage)(closeButt.getScene().getWindow())).close();
	}
	private float getScale(Image im) {
		double canWid = mapCan.getWidth();
		double canHt = mapCan.getHeight();
		double imWid = im.getWidth();
		double imHt = im.getHeight();
		float widFac = (float) (imWid / canWid);
		float htFac = (float) (imHt / canHt);
		log.finer("Returning scale");
		if(widFac > htFac) {
			baseYOffset = (float)(canHt - imHt / widFac);
			return  1 / widFac;
		}
		baseXOffset = (float)(canWid - imWid / htFac);
		return 1 / htFac;
	}
	public void updateCons() {
		ArrayList<String> cons = comm.getCommList();
		connMenu.getItems().clear();
		for(String con : cons) {
			MenuItem newItem = new MenuItem(con);
			connMenu.getItems().add(newItem);
			newItem.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent e) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							log.fine(((MenuItem)e.getSource()).getText() + "Prompted for connection.");
							comm.openConnection(comm.getPortId(((MenuItem)e.getSource()).getText()));
						}
					}).start();
				}
			});
		}
		log.fine("Updated list of serial devices.");
	}
	public void upMaps() {
		File fold = new File(System.getProperty("user.dir"));
		String tmp = new String();
		ArrayList<String> data = getMapDataAvail();
		MenuItem tmpItem;
		mapList.getItems().clear();
		for(File fl : fold.listFiles()) {
			if((tmp = fl.getName()).length() > 3 
					&& tmp.substring(tmp.length()-3).equals("jpg") 
					&& data.contains(tmp.substring(0, tmp.length()-4))) {
				tmpItem = new MenuItem(tmp.substring(0, tmp.length()-4));
				mapList.getItems().add(tmpItem);
				
				tmpItem.setOnAction(new EventHandler<ActionEvent>() {
					@Override 
					public void handle(ActionEvent e) {
						mapName = ((MenuItem)e.getSource()).getText();
						drawMap();
					}
				});
			}
		}
		log.fine("Updated list of maps");
	}
	public void disconnect() {
		if(!comm.getState())
			return;
		if(comm.closeConnection())
			connButtSt(false);
	}
	public void connButtSt(boolean st) {
		discButt.setDisable(!st);
		connInfo.setDisable(!st);
	}
	public ArrayList<String> getMapDataAvail() {
		ArrayList<String> data = new ArrayList<>();
		String tmp = new String();
		File fold = new File(System.getProperty("user.dir"));
		for(File fl : fold.listFiles()) {
			if((tmp = fl.getName()).length() > 4 && tmp.substring(tmp.length()-4).equals("json"))
				data.add(tmp.substring(0, tmp.length()-5));
		}
		return data;
	}
	public boolean getMapData(String fileName) {
		JSONObject obj = null;
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(fileName + ".json")));
			String st = br.readLine();
			br.close();
			obj = new JSONObject(st);
		} catch (JSONException | IOException e) {
			log.severe("Failed to load map data!");
			e.printStackTrace();
			return false;
		}
		JSONArray arr = obj.getJSONArray("data");
		try {
			baseLng = (double)((JSONObject)(arr.get(0))).get("long");
			baseLat = (double)((JSONObject)(arr.get(0))).get("lat");
			topLng = (double)((JSONObject)(arr.get(1))).get("long");
			topLat = (double)((JSONObject)(arr.get(1))).get("lat");
		} catch(JSONException e)  {
			log.severe("Error getting JSON map data.");
			log.severe(e.toString());	
			return false;
		}
		return true;
	}
	//NOTE: adjust lat conditions to work anywhere when time - lazy rn...
	private tuple toCanvas(tuple src) {
		double lng = src.x, lat = src.y;
		double imWid = mapCan.getWidth() - baseXOffset, imHt = mapCan.getHeight() - baseYOffset;
		boolean left = lng > baseLng, right = lng < topLng, bottom = lat < baseLat, top = lat > topLat;
		
		if(mapWarn = (left || right|| top || bottom)) {
			if(left) 		mapRel = 1;
			else if(right) 	mapRel = 2;
			else if(top) 	mapRel = 4;
			else if(bottom) mapRel = 3;
			
			return new tuple(-1, -1);
		}
		mapRel = 5;
		double xScale = imWid / Math.abs(Math.abs(topLng)-Math.abs(baseLng));
		double yScale = imHt / Math.abs(Math.abs(topLat)-Math.abs(baseLat));
		double x = Math.abs(Math.abs(lng) - Math.abs(baseLng))*xScale;
		double y = Math.abs(Math.abs(lat) - Math.abs(baseLat))*yScale;
		y = imHt - y;
		
		if(!Double.isFinite(x)) {x = 0; }
		if(!Double.isFinite(y)) {y = 0; }
		return new tuple(x-3, y-4);
	}
	public String getStyleStr(boolean tp) {
		if(tp)
			return "-fx-font-weight: bold;";
		return "-fx-font-weight: normal;";
	}
	public String getStateStr(boolean tp) {
		if(!tp)
			return "Normal";
		return "WARNING";
	}
	public void updateWarnText() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				serialState.setText("Serial State: " + getStateStr(serialWarn));
				xbeeState.setText("Xbee State: " + getStateStr(xbeeWarn));
				GPSState.setText("GPS State: " + getStateStr(GPSWarn));
				battState.setText("Battery State: " + getStateStr(battWarn));
				mapState.setText("Out of map: " + getStateStr(mapWarn));
			}
		});
	}
	public void updateWarnStyle() {
		if(!warnChange)
			return;
		warnChange = false;
		if(xbeeWarn)
			updateWarnText();
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				serialState.setStyle(getStyleStr(serialWarn));
				xbeeState.setStyle(getStyleStr(xbeeWarn));
				GPSState.setStyle(getStyleStr(GPSWarn));
				battState.setStyle(getStyleStr(battWarn));
				mapState.setStyle(getStyleStr(mapWarn));
			}
		});	
	}
	public void getConInfo()  {comm.getConnInfo(); }
	public void openBay() {datM.openBay();	}
	public void closeBay() {datM.closeBay(); }
	public void camLeft(){comm.sendByte((byte)'l'); }
	public void camUp(){comm.sendByte((byte)'u'); }
	public void camRight(){comm.sendByte((byte)'r'); }
	public void camDown(){comm.sendByte((byte)'d'); }
}
