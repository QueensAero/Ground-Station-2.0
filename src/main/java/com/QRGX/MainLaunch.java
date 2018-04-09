package com.QRGX;
	
import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import sun.reflect.Reflection;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;


public class MainLaunch extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {	
			GUIController cont = new GUIController();
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(getClass().getResource("/fxml/MainScreen.fxml"));
			loader.setController(cont);
			GridPane root = loader.load();
			
			Scene scene = new Scene(root);
			//scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.setMaximized(true);
			primaryStage.centerOnScreen();
			primaryStage.setTitle("Queen's Aero Design Team");
			primaryStage.setResizable(false);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		launch(args);
		
	}
}
