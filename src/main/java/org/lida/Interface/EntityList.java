package org.lida.Interface;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionModel;
import javafx.util.Callback;
import org.lida.Entity.AnalysisEntity;

import java.awt.*;
import java.io.File;
import java.io.IOException;


// Controller of FileDetails.fxml, used as an interactive list of AnalysisEntities
public class EntityList {

	// Reference to the ListView node
	@FXML
	private ListView<AnalysisEntity> entityListView;

	// Base ListCell class instanced inside the ListView
	public static abstract class BaseAnalysisEntityCell extends ListCell<AnalysisEntity> {
		@Override
		protected void updateItem(AnalysisEntity entity, boolean empty) {
			super.updateItem(entity, empty);
			// Rimuovi tutti gli handler precedenti
			setOnMouseClicked(null);

			if (empty || entity == null) {
				setText(null);
				setGraphic(null);
				setCursor(Cursor.DEFAULT);
				return;
			}

			// Gestione comune del click
			setOnMouseClicked(e -> {
				if (e.getClickCount() == 1) {
					LIDAController.moveToEntity(entity);
				} else {
					openPath(entity.getPath());
				}
			});

			setCursor(Cursor.HAND);
			// Metodo hook per impostare il testo specifico
			updateCellText(entity);
		}

		// Metodo astratto che deve essere implementato per impostare il testo della cella
		protected abstract void updateCellText(AnalysisEntity entity);
	}

	// --------------------- Public functions ---------------------

	// Public function to customize the CellFactory of the ListView
	public void setCellFactory(Callback<ListView<AnalysisEntity>, ListCell<AnalysisEntity>> factory) {
		entityListView.setCellFactory(factory);
	}


	// Public function to access the ListView items
	public ObservableList<AnalysisEntity> getItems() {
		return entityListView.getItems();
	}

	// Public function to access the ListView selection model
	public SelectionModel<AnalysisEntity> getSelectionModel() {
		return entityListView.getSelectionModel();
	}


	// Public function that opens a file on the user pc, base action for double-click on the ListCells
	public static void openPath(String path) {
		try {
			Desktop.getDesktop().open(new File(path));
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}
