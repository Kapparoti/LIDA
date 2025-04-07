package org.lida.Interface;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.lida.Entity.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

// Controller of FileDetails.fxml, used to display details about a file AnalysisEntity
public class FileDetails {

	// --------------------- FXML nodes ---------------------

	// Container for the detail elements
	@FXML
	private GridPane containerGridPane;

	// List of the file Identifiers and Variables
	@FXML
	private ListView<String> namesListView;

	// Label to show the file's path
	@FXML
	private Label pathLabel;
	// Rectangle containing the color of the file's programming language
	@FXML
	private Rectangle programmingColorRect;
	// Label for the name of the file's programming language
	@FXML
	private Label programmingLanguageLabel;
	// Label for the number of lines inside the file
	@FXML
	private Label linesCountLabel;
	// Label for the memory size of the file
	@FXML
	private Label memorySizeLabel;

	// TabPane used to choose Dependencies or Dependants
	@FXML
	private TabPane depensTapPane;
	// The EntityList used to show interactive Dependencies or Dependants
	private EntityList entityList;

	// --------------------- Class variables ---------------------

	// The selected file entity
	private AnalysisEntity fileEntity;

	// --------------------- Initialization ---------------------

	public void initialize() throws IOException {
		// We add a listener to the dependsTabPane to update the entities displayed
		depensTapPane.getSelectionModel().selectedItemProperty().addListener((_, _, newTab) -> fillEntityList(newTab.getText()));

		// We load the EntityList
		FXMLLoader loader = new FXMLLoader(getClass().getResource("EntityList.fxml"));
		Node entityListNode = loader.load();
		entityList = loader.getController();

		// We set its CellFactory to show FileDependencies correctly
		entityList.setCellFactory(_ -> new EntityList.BaseAnalysisEntityCell() {
			@Override
			protected void updateCellText(AnalysisEntity entity) {

				// Each cell represents a FileDependency, so we prepare to store all of it's rules
				StringBuilder rulesString = new StringBuilder();

				// Depending on what we need to show, we fill the values accordingly
				List<FileDependency> values;
				if (depensTapPane.getSelectionModel().getSelectedItem().getText().equals("Dependencies")) {
					values = fileEntity.getDependencies();
				} else {
					values = fileEntity.getDependants();
				}

				for (FileDependency fileDependency : values) {
					if (fileDependency.getEntity().equals(entity)) {
						// If the entity of the FileDependency equals to the one of the cell, we proceed
						Iterator<Dependency> iterator = fileDependency.getDependencies().iterator();
						while (iterator.hasNext()) {

							// For each Dependency of the FileDependency, we check if it's hidden
							Dependency dependency = iterator.next();

							// We add the Dependency's rule and identifier
							rulesString.append(dependency.ruleName());
							if (!dependency.hidden()) rulesString.append(": \"").append(dependency.identifier().name()).append("\"");

							if (iterator.hasNext()) rulesString.append(", ");
						}
					}
				}

				// Then, for each cell, we set its text with the finished result. If there is any
				if (!rulesString.isEmpty()) setText(entity.getName() + " (" + rulesString + ")");
			}
		});

		// We finally add the dependencies EntityList to the container
		containerGridPane.add(entityListNode, 2, 1);
	}

	// --------------------- FXML functions ---------------------

	// Return to the selected file entity, called upon clicking on the file details
	@FXML
	private void returnToFile() {
		LIDAController.moveToEntity(fileEntity);
	}

	// Open the selected file entity on the user pc, called from its respective button
	@FXML
	private void openCurrentPath() {
		EntityList.openPath(fileEntity.getPath());
	}

	// --------------------- Public functions ---------------------

	// Public function for FileDetails setup
	public void setFileEntity(AnalysisEntity rootEntity, AnalysisEntity fileEntity) {
		this.fileEntity = fileEntity;

		// We fill the identifiers ListView with their names and respective rule
		for (Identifier identifier : fileEntity.getIdentifiers()) {
			if (identifier.hidden()) continue;

			namesListView.getItems().add("Identifier \"" + identifier.name() + "\" (" + identifier.ruleName() + ")");
		}
		// We add the visible Variables as well
		for (Variable variable : fileEntity.getVariables()) {
			if (variable.hidden()) continue;

			namesListView.getItems().add("Variable: \"" + variable.name() + "\"   Value: \"" + variable.value() + "\"");
		}

		// We fill the details with the file entity data
		pathLabel.setText(fileEntity.getPath().substring(rootEntity.getPath().length()));
		programmingColorRect.setFill(Color.web(fileEntity.getColor()));

		if (fileEntity.isCode()) {
			programmingLanguageLabel.setText("Programming language: " + fileEntity.getFileType());
		} else {
			programmingLanguageLabel.setText("File type: " + fileEntity.getFileType());
		}

		linesCountLabel.setText("Lines number: " + fileEntity.getLineCount());
		memorySizeLabel.setText("Memory size: " + fileEntity.getStorageSizeString());

		fillEntityList(depensTapPane.getSelectionModel().getSelectedItem().getText());
	}

	private void fillEntityList(String type) {
		entityList.getItems().clear();

		// We fill the entityList depending on the type
		if (type.equals("Dependencies")) {
			for (FileDependency dependency : fileEntity.getDependencies()) {
				entityList.getItems().addAll(dependency.getEntity());
			}
		} else {
			for (FileDependency dependency : fileEntity.getDependants()) {
				entityList.getItems().addAll(dependency.getEntity());
			}
		}
	}
}
