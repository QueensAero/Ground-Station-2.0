package application;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.json.*;

public class GUIController {
	//FXML declarations
	@FXML
	Pane MapPane;
	@FXML
	GridPane mainPane;
	@FXML
	MenuButton mapList, connMenu;
	@FXML
	TextArea infoPane;
	@FXML
	Button connInfo, discButt, mapUpdate, tempButt, closeButt, conUpButt;
	@FXML
	Button cameraControl;
	
	//Status labels
	@FXML
	Label vHDOP, packTime, speed, bytesAvail, satNum, heightL;
	@FXML
	Label orginDist, travDist, batLevel, hdop, connState, packetsR, packRateLabel, fixTp;
	//Warnings
	@FXML
	Label xbeeState, GPSState, battState, serialState;
	private boolean xbeeWarn, GPSWarn, battWarn, serialWarn, warnChange;
	
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
	public Canvas mapCan;
	private ArrayList<tuple> path; 
	private GraphicsContext gc;
	private int lastPoint;
	private String mapName;
	private double baseLng, baseLat, topLng, topLat;
	
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
		connButtSt(false);
		FileHandler filehandle = null;
		try {
			filehandle = new FileHandler("output.log");
		} catch (IOException e) {
			e.printStackTrace();
		}
		TextAreaHandler taHandle = new TextAreaHandler();
		taHandle.setTextArea(infoPane);
		log.addHandler(taHandle);
		log.addHandler(filehandle); 
		log.fine("Initializing.");
		xbeeWarn = GPSWarn = battWarn = serialWarn = true;
		
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
		if(mapCan != null)
			return;
		mapCan = new Canvas(MapPane.getWidth(), MapPane.getHeight());
		MapPane.getChildren().add(mapCan);
		mapCan.setTranslateX(MapPane.getLayoutX());
		mapCan.setTranslateY(MapPane.getLayoutY());
		gc = mapCan.getGraphicsContext2D();
		gc.setStroke(Color.BLACK);
		gc.setLineWidth(3);
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
		gc.beginPath();
		for(tuple pt : path) {
			if(pt == path.get(0))
				gc.moveTo(pt.x, pt.y);
			else
				gc.lineTo(pt.x, pt.y);
		}
		lastPoint = path.size() - 1;
		gc.stroke();
		log.fine("Path drawn.");
	}
	//Updates the path (as not to redraw all previous points
	public void updatePath() {
		if(path.size() == lastPoint + 1)
			return;
		int i;
		gc.beginPath();
		for(i=lastPoint;i<path.size();i++)  {
			if(i == lastPoint)
				gc.moveTo(path.get(i).x, path.get(i).y);
			else
				gc.lineTo(path.get(i).x, path.get(i).y);
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
		gc.drawImage(im, 0, 0, scl*im.getWidth(), scl*im.getHeight());
		log.info("Map drawn");
		getMapData(mapName);
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
		if(widFac > htFac)
			return  1 / widFac;
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
		JSONObject obj = null, tmp;
		tuple[] tp = new tuple[2];
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
			baseLng = (double)((JSONObject)(arr.get(0))).get("lat");
			topLng = (double)((JSONObject)(arr.get(1))).get("long");
			topLng = (double)((JSONObject)(arr.get(1))).get("lat");
		} catch(JSONException e)  {
			log.severe("Error getting JSON map data.");
			log.severe(e.toString());	
			return false;
		}
		return true;
	}
	private tuple toCanvas(float lng, float lat) {
		if(lng < baseLng || lng > topLng || lat < baseLat || lat > topLat)
			return new tuple(-1, -1);
		double xScale = (float) (Math.abs(topLng - baseLng) / mapCan.getWidth());
		double yScale = (float) (Math.abs(topLat - baseLat) / mapCan.getHeight());
		
		return new tuple(Math.abs(lng - baseLng)*xScale, Math.abs(lat - baseLat)*yScale);
	}
	public String getStyleStr(boolean tp) {
		if(tp)
			return "-fx-font-weight: bold;";
		return "-fx-font-weight: normal;";
	}
	public String getStateStr(boolean tp) {
		if(tp)
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
			}
		});
	}
	public void updateWarnStyle() {
		if(!warnChange)
			return;
		warnChange = false;
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				serialState.setStyle(getStyleStr(serialWarn));
				xbeeState.setStyle(getStyleStr(xbeeWarn));
				GPSState.setStyle(getStyleStr(GPSWarn));
				battState.setStyle(getStyleStr(battWarn));
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
