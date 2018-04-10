package photoViewer;

import com.QADT.KMLStore;
import com.QADT.tuple;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class Main extends Application {
	private Button next,prev,sendCoord;
	private int curImage;
	public final static int xWidth = 800;
	public final static int yHeight = 630;
	private ArrayList<tuple> points = new ArrayList<tuple>();
	private ArrayList<String> ids = new ArrayList<String>();
	private boolean click;
	private dataManager data;
	private Group group;
	private Scene scene;

	@Override
	public void start(Stage stage) throws FileNotFoundException {

		data = new dataManager();

		ImageView imageView = new ImageView(data.getImage(0));
		imageView.setFitHeight(600);
		imageView.setPreserveRatio(true);

		next = new Button();
		prev = new Button();
		sendCoord = new Button();
		click = false;

		group = new Group(imageView,next,prev,sendCoord);

		scene = new Scene(group,xWidth,yHeight);

		next.setText("Next Picture");
		prev.setText("Previous Picture");
		sendCoord.setText("Send Coordinates");

		next.setOnAction(e -> {
			curImage = (++curImage % data.getNumIm());
			imageView.setImage(data.getImage(curImage));
			populateIm();
		});

		prev.setOnAction(e -> {
			if(--curImage<0)	curImage += data.getNumIm();
			imageView.setImage(data.getImage(curImage));
			populateIm();
		});

		sendCoord.setOnAction(e ->{
			KMLStore coordinates = new KMLStore();
			for(int i = 0;i<points.size();i++){
				coordinates.addCord(ids.get(i),(float) points.get(i).x,(float)points.get(i).y);
				System.out.println("("+ids.get(i)+","+points.get(i).x+","+points.get(i).y+")");
			}
			coordinates.exportKML();
		});

		next.setLayoutX(430);
		next.setLayoutY(600);
		sendCoord.setLayoutX(5);
		prev.setLayoutX(250);
		prev.setLayoutY(600);
		sendCoord.setLayoutY(600);

		scene.setOnMousePressed(c->{
			if(!click){
				click = true;
				next.setDisable(true);
				prev.setDisable(true);
				sendCoord.setDisable(true);

				//get GPS point of click
				tuple disp = data.getDisp(c.getX(),c.getY(), curImage);
				tuple newPoint = data.getGPSPoint(data.getLoc(curImage), disp.x, disp.y);

				if(data.getImManager(curImage).checkForPoint(c.getX(), c.getY())>=0){
					Text delete = new Text("Delete point?");
					Button yes = new Button("Confirm");
					Button no = new Button("Cancel");
					yes.setMaxSize(75, 20);
					no.setMaxSize(75,20);
					VBox a = new VBox(500);
					a.setStyle("-fx-background-color: #ffffff;");
					a.getChildren().addAll(delete,yes,no);
					a.setSpacing(5);
					group.getChildren().add(a);
					if (c.getX()<200 && c.getY()<200){
						a.setLayoutX(540);
					}
					yes.setOnAction(e ->{
						int ind = data.getImManager(curImage).checkForPoint(c.getX(), c.getY());
						data.getImManager(curImage).removePoint(ind);
						points.remove(ind);
						ids.remove(ind);
						populateIm();
						group.getChildren().remove(a);
						next.setDisable(false);
						prev.setDisable(false);
						sendCoord.setDisable(false);
						click = false;
					});
					no.setOnAction(e ->{
						group.getChildren().remove(a);
						next.setDisable(false);
						prev.setDisable(false);
						sendCoord.setDisable(false);
						click = false;
					});
				}
				else{
					//Confirm click
					Text text = new Text("To confirm click at\n" + newPoint.toString() +",\n enter point ID below:");

					Circle mark = new Circle(c.getX(),c.getY(),5);
					mark.setFill(Color.RED);
					mark.setStroke(Color.BLACK);

					Button yes = new Button("Confirm");
					Button no = new Button("Cancel");
					yes.setMaxSize(75, 20);
					no.setMaxSize(75,20);


					TextField tf = new TextField();

					VBox a = new VBox(500);
					a.setStyle("-fx-background-color: #ffffff;");
					a.getChildren().addAll(text,tf,yes,no);
					a.setSpacing(5);


					group.getChildren().addAll(a,mark);
					if (c.getX()<200 && c.getY()<200){
						a.setLayoutX(540);
					}

					yes.setOnAction(e ->{
						//make sure user has added ID based on picture
						if(tf.getText().isEmpty()){
							Text t = new Text("Please enter ID");
							t.setFill(Color.RED);
							a.getChildren().add(t);
						}
						else{
							mark.setFill(Color.BLACK);
							mark.setStroke(Color.WHITE);
							group.getChildren().remove(a);
							points.add(newPoint);
							ids.add(tf.getText());
							data.addPoint(curImage, new tuple(c.getX(),c.getY()));	//so that you can see the points when you go back to them
							next.setDisable(false);
							prev.setDisable(false);
							sendCoord.setDisable(false);
							click = false;
						}
					});
					no.setOnAction(e ->{
						group.getChildren().removeAll(a,mark);
						next.setDisable(false);
						prev.setDisable(false);
						sendCoord.setDisable(false);
						click = false;
					});
				}
			}
		});

		stage.setTitle("Photo Viewer");
		stage.setScene(scene);
		stage.show();
	}

	public void populateIm(){
		group.getChildren().removeIf(n -> n instanceof Circle);
		for(int i = 0;i<data.getImManager(curImage).numPoints();i++){
			tuple coordinates = data.getImManager(curImage).getPoint(i);
			Circle circle = new Circle(coordinates.x,coordinates.y,5);
			circle.setFill(Color.BLACK);
			circle.setStroke(Color.WHITE);
			group.getChildren().add(circle);
		}
	}

	public static void main(String args[]) {
		launch(args);
	}
}
