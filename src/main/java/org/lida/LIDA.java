package org.lida;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class LIDA extends Application {

	@Override
	public void start(Stage primaryStage) throws Exception {
		// We load the main scene
		FXMLLoader loader = new FXMLLoader(getClass().getResource("Interface/LIDA.fxml"));
		Parent root = loader.load();

		// Stage setup
		primaryStage.setTitle("Language Independent Codebase Analyzer");
		primaryStage.setScene(new Scene(root));
		primaryStage.setResizable(false);
		primaryStage.show();
	}
}