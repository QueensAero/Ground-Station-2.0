package application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class GUIController {
	//FXML declarations
	@FXML
	Pane MapPane;
	@FXML
	GridPane mainPane;
	@FXML
	Button closeButt;
	@FXML
	Button tempButt;
	@FXML
	MenuButton connMenu;
	@FXML 
	Button conUpButt;
	@FXML
	Button mapUpdate;
	@FXML
	MenuButton mapList;
	@FXML
	TextArea infoPane;
	@FXML
	Button discButt;
	@FXML
	Button connInfo;
	
	//Status labels
	@FXML
	protected Label orginDist;
	@FXML
	protected Label travDist;
	@FXML
	protected Label batLevel;
	@FXML
	protected Label hdop;
	@FXML
	protected Label connState;
	
	//Map variables
	public Canvas mapCan;
	private ArrayList<tuple> path; 
	private GraphicsContext gc;
	private int lastPoint;
	
	//Communication
	SerialCommunicator comm;
	dataManager datM;
	OutputStream infOut;
	private static final Logger log = Logger.getLogger(GUIController.class.getName());
	
	@FXML
	public void initialize() {
		comm = new SerialCommunicator(this);	//Raw communication with Xbee
		datM = new dataManager(this);	//Works with bytes from comm
		datM.passComm(comm);	//Passes comm..
		comm.passDataM(datM);
		path = new ArrayList<>();
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
		log.fine("Canvas initialized.");
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
		initCanvas();
		Image im;
		try {
			im = new Image(new FileInputStream("queens.jpg"));
		} catch (FileNotFoundException e) {
			log.severe("Map file not found.");
			log.severe(e.toString());
			return;
		}
		float scl = getScale(im);
		gc.drawImage(im, 0, 0, scl*im.getWidth(), scl*im.getHeight());
		log.fine("Map drawn");
	}
	public void close() {
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
					log.fine(((MenuItem)e.getSource()).getText() + "Prompted for connection.");
					comm.openConnection(comm.getPortId(((MenuItem)e.getSource()).getText()));
					//datM.initReadList();
				}
			});
		}
		log.fine("Updated list of serial devices.");
	}
	public void upMaps() {
		File fold = new File(System.getProperty("user.dir"));
		String tmp = new String();
		mapList.getItems().clear();
		for(File fl : fold.listFiles()) {
			tmp = fl.getName().substring(fl.getName().length()-3);
			if(tmp.equals("jpg"))
				mapList.getItems().add(new MenuItem(fl.getName()));
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
	public void getConInfo()  {comm.getConnInfo(); }
	public void openBay() {datM.openBay();	}
	public void closeBay() {datM.closeBay(); }
	public void camLeft(){comm.sendByte((byte)'l'); }
	public void camUp(){comm.sendByte((byte)'u'); }
	public void camRight(){comm.sendByte((byte)'r'); }
	public void camDown(){comm.sendByte((byte)'d'); }
}
