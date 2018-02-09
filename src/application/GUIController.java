package application;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;
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
	Label vHDOP, packTime, speed, bytesAvail, satNum, height;
	@FXML
	Label orginDist, travDist, batLevel, hdop, connState, packetsR, packRateLabel, fixTp;
	@FXML
	Tab connTab;	
	
	//Map variables
	public Canvas mapCan;
	private ArrayList<tuple> path; 
	private GraphicsContext gc;
	private int lastPoint;
	private JSONObject mapData;
	private String mapName;
	
	//Communication
	SerialCommunicator comm;
	dataManager datM;
	OutputStream infOut;
	private static final Logger log = Logger.getLogger(GUIController.class.getName());
	
	@FXML
	public void initialize() {
		datM = new dataManager(this);	//Works with bytes from comm
		comm = new SerialCommunicator(this, datM);	//Raw communication with Xbee
		datM.passComm(comm);	//Passes comm..
		path = datM.getPath();
		connButtSt(false);
		log.fine("Initializing.");
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
		gc.moveTo(0,  0);
		gc.lineTo(100, 100);
		gc.stroke();
		System.out.println("Canvas: " + mapCan.isVisible());
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
		System.out.println(data);
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
	public void getMapData(String fileName) {
		JSONObject obj = null;
		double lat, lng;
		try {
			obj = new JSONObject(new BufferedReader(new FileReader(new File(fileName))).readLine());
		} catch (JSONException | IOException e) {
			log.severe("Failed to load map data!");
			e.printStackTrace();
		}
		
	}
	public void getConInfo()  {comm.getConnInfo(); }
	public void openBay() {datM.openBay();	}
	public void closeBay() {datM.closeBay(); }
	public void camLeft(){comm.sendByte((byte)'l'); }
	public void camUp(){comm.sendByte((byte)'u'); }
	public void camRight(){comm.sendByte((byte)'r'); }
	public void camDown(){comm.sendByte((byte)'d'); }
}
