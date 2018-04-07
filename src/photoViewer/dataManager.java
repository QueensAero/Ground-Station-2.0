package photoViewer;

import application.tuple;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javafx.scene.image.Image;

public class dataManager {
	private ArrayList<tuple> gpsCoordinates = new ArrayList<tuple>();
	private ArrayList<imManager> images = new ArrayList<imManager>();
	private ArrayList<Double> heights = new ArrayList<Double>();
	private ArrayList<Double> headings = new ArrayList<Double>();
	private int numImages;
	private String filepath;

	//TODO: change filepath to currently commented out filepath
	public dataManager() throws FileNotFoundException{
		numImages = 0;
		filepath = System.getProperty("user.dir");
		//filepath = "C:\\Users\\Michael\\Documents\\AERO1\\Photo_Viewer";
		fetchImages();
		fetchCoordinates(filepath);	//gets gps coordinates, heights and headings
	}

	private void fetchImages() throws FileNotFoundException{
		File fold = new File(filepath);

		for(File fl : fold.listFiles()) {
			String tmp = fl.getName().substring(fl.getName().length()-3);
			if(tmp.equalsIgnoreCase("JPG")){
				images.add(new imManager(new Image(new FileInputStream(filepath + "\\" + fl.getName()))));
				numImages++;
			}
		}
	}

	private boolean fetchCoordinates(String fileName){
		JSONObject obj = null;
		try {
			//TODO: Update to match actual JSON file name
			BufferedReader br = new BufferedReader(new FileReader(new File(fileName + "\\queens_3.json")));
			String st = br.readLine();
			br.close();
			obj = new JSONObject(st);
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			return false;
		}
		JSONArray arr = obj.getJSONArray("data");

		for(int i=0;i<arr.length();i++){
			try{
				double lat = arr.getJSONObject(i).getDouble("lat");
				double lng = arr.getJSONObject(i).getDouble("long");
				gpsCoordinates.add(new tuple(lat,lng));
				heights.add(arr.getJSONObject(i).getDouble("height"));
				headings.add(Math.toRadians(arr.getJSONObject(i).getDouble("bearing")));
			}catch(JSONException e){
				e.printStackTrace();
			}
		}
		return true;
	}

	public Image getImage(int ind){
		return images.get(ind % numImages).getImage();
	}


	public tuple getGPSPoint(tuple start, double dist, double bearing){

		dist = dist / 1000;
		double lat1 = Math.toRadians(start.x);
		double lon1 = Math.toRadians(start.y);

		double lat2 = Math.asin(Math.sin(lat1)*Math.cos(dist/6378) + Math.cos(lat1)*Math.sin(dist/6378)*Math.cos(bearing));
		double lon2 = lon1 + Math.atan2(Math.sin(bearing)*Math.sin(dist/6378)*Math.cos(lat1),Math.cos(dist/6378)-Math.sin(lat1)*Math.sin(lat2));

		tuple point = new tuple(Math.toDegrees(lat2),Math.toDegrees(lon2));

		return point;
	}

	public int getNumIm() {
		return numImages;
	}

	public tuple getDisp(double xPix, double yPix,int ind){
		//Currently assumes camera FOV is 120 deg x 90
		//TODO: make sure this is accurate^
		double dx = ((xPix - 400) * heights.get(ind)*Math.tan(Math.PI/3)/400);
		double dy = ((300 - yPix) * heights.get(ind)*Math.tan(Math.PI/4)/300);
		double dist = Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
		double angle = (Math.atan2(dx,dy)+headings.get(ind))%(Math.PI*2);

		return new tuple(dist,angle);
	}

	public tuple getLoc(int ind) {
		return gpsCoordinates.get(ind);
	}
	public void addPoint(int ind,tuple point){
		images.get(ind).addPoint(point);
	}
	public imManager getImManager(int index){
		return images.get(index);
	}
	public class imManager{
		private ArrayList<tuple> points = new ArrayList<tuple>();;
		private Image image;
		imManager(Image im){
			image = im;
		}
		public void addPoint(tuple point){points.add(point);}
		public int numPoints(){return points.size();}
		public Image getImage(){return image;}
		public tuple getPoint(int ind){return points.get(ind);}
		public int checkForPoint(double xPix,double yPix){
			for(int i = 0;i<points.size();i++){
				if(Math.abs(points.get(i).x-xPix)<=5 && Math.abs(points.get(i).y-yPix)<=5)	return i;
			}
			return -1;
		}

		public void removePoint(int ind){
			points.remove(ind);
		}

	}
}
