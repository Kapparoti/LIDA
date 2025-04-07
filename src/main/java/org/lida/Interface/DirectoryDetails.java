package org.lida.Interface;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;

import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.lida.Entity.AnalysisEntity;

import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class DirectoryDetails {

	// --------------------- FXML nodes ---------------------

	// TableView filled with the directory Entity's FileTypeResults
	@FXML
	private TableView<FileTypeResults> resultTable;
	// Column for the file type of the FileTypeResults
	@FXML
	private TableColumn<FileTypeResults, String> fileTypeColumn;
	// Column for the file count of the FileTypeResults
	@FXML
	private TableColumn<FileTypeResults, Integer> fileCountColumn;
	// Column for the line count of the FileTypeResults
	@FXML
	private TableColumn<FileTypeResults, Integer> lineCountColumn;
	// Column for the storage size of the FileTypeResults
	@FXML
	private TableColumn<FileTypeResults, Long> storageSizeColumn;

	// TabPane used to choose between the value shown on the percentage bar
	@FXML
	private TabPane percentChoiceTabPane;
	// Tab to choose the file count of the FileTypeResults
	@FXML
	private Tab fileCountTab;
	// Tab to choose the line count of the FileTypeResults
	@FXML
	private Tab lineCountTab;
	// Tab to choose the storage size of the FileTypeResults
	@FXML
	private Tab storageSizeTab;

	// HBox used to create the percentage bar
	@FXML
	private HBox percentageBar;
	// GridPane used to create the legend for the percentage bar
	@FXML
	private GridPane legendGridPane;

	// Right VBox containing the top gray panel and the EntityList
	@FXML
	private VBox rightVBox;
	// Label inside the gray panel for the path of the selected directory Entity
	@FXML
	private Label selectedPathLabel;
	// EntityList of the files with the selected file type
	private EntityList entityList;

	// --------------------- Class variables ---------------------

	// Root of the file tree, used as reference for it's path
	private TreeItem<AnalysisEntity> treeRoot;
	// The TreeItem of the current AnalysisEntity,
	// we need the TreeItem to perform the search navigating the tree
	private TreeItem<AnalysisEntity> currentTreeItem;

	// The LoadingAnimation showed inside the resultTable PlaceHolder, shown during search
	private LoadingAnimation loadingAnimation;

	// Selected file type, by clicking the percentage bar, the percentage bar legend or the resultTable
	private String selectedFileType = "";
	// We store the handler to use it on the percentage bar segments, the percentage bar legend entries or a resultTable's row
	private final EventHandler<MouseEvent> selectFileTypeHandler = mouseEvent -> {

		// We first need to retrieve the selected file type
		String fileType = null;
		Node source = (Node) mouseEvent.getSource();
		// During the creation of the Nodes, we stored the file type inside their UserData, so we can just read it
		if (source.getUserData() != null) {
			fileType = source.getUserData().toString();
		} else {
			Node parent = source.getParent();
			if (parent != null && parent.getUserData() != null) {
				fileType = parent.getUserData().toString();
			}
		}

		// Flag to see if the user selected the already selected file type
		boolean sameFileType = selectedFileType.equals(fileType);

		// We set the Y position of the percentageBar's segments:
		for (Node segment : percentageBar.getChildren()) {

			// We check on the fileType and not on the source because the handler can be called by the resultTable too
			if (segment.getUserData().toString().equals(fileType)) {

				// For the file type selected by the user:
				if (sameFileType) {

					// If the user selected the same file type, we restore the highlighted segment to default
					segment.setTranslateY(0);
					// And reset the selected file type
					selectedFileType = "";
				} else {

					// If the user selected a new file type, we highlighted the file type segment
					segment.setTranslateY(-10);
					// And update selectedFileType
					selectedFileType = fileType;
				}
			} else {
				// For the other not selected segments:
				segment.setTranslateY(0);
			}
		}

		// Then, we handle the resultTable rows:
		if (sameFileType) {

			// If the user selected the same file type, we remove the selection of it
			resultTable.getSelectionModel().clearSelection();
		} else {

			// If the user selected a new file type, we select the file type row
			for (FileTypeResults result : resultTable.getItems()) {
				if (result.fileType().equals(fileType)) {
					resultTable.getSelectionModel().select(result);
					resultTable.scrollTo(result);
					break;
				}
			}
		}

		// Finally, we handle the entityList:
		entityList.getSelectionModel().clearSelection();
		if (sameFileType) {

			// If the user selected the same file type, we clear the list
			entityList.getItems().clear();
		} else {

			// If the user selected a new file type, we search the corresponding files
			List<AnalysisEntity> newFiles = getFilesForType(currentTreeItem, fileType);
			entityList.getItems().setAll(newFiles);
		}
	};

	// --------------------- Initialization ---------------------

	public void initialize() throws IOException {

		// We set the resultTable's RowFactory
		resultTable.setRowFactory(_ -> new TableRow<>() {
			protected void updateItem(FileTypeResults fileTypeResults, boolean empty) {
				super.updateItem(fileTypeResults, empty);
				if (fileTypeResults != null) {

					// If the row has a value:
					if ("Total".equals(fileTypeResults.fileType())) {

						// If the row is the one displaying the total amounts, we disable it
						setDisable(true);
						setUserData(null);
						setCursor(Cursor.DEFAULT);
					} else {

						// If the row is not the one displaying the total amounts, we add the UserData and the Handler
						setDisable(false);
						setUserData(fileTypeResults.fileType());
						setCursor(Cursor.HAND);
						addEventHandler(MouseEvent.MOUSE_CLICKED, selectFileTypeHandler);
					}
				}
			}
		});

		// We then set the ValueFactories for the resultTable's columns
		fileTypeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().fileType()));
		fileCountColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().fileCount()).asObject());
		lineCountColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().lineCount()).asObject());
		storageSizeColumn.setCellValueFactory(cellData -> new SimpleLongProperty(cellData.getValue().storageSize()).asObject());

		// For the storageSizeColumn, we also need to format the long value of the storage size
		storageSizeColumn.setCellFactory(_ -> new TableCell<>() {
			protected void updateItem(Long storageSize, boolean empty) {
				super.updateItem(storageSize, empty);
				if (empty || storageSize == null) {

					// If the cell is empty, we disable it
					setText(null);
					setCursor(Cursor.DEFAULT);
				} else {

					// If the cell is not empty, we fill it with the formatted storage type string
					setText(AnalysisEntity.storageSizeString(storageSize));
					setCursor(Cursor.HAND);
				}
			}
		});

		// We load the EntityList
		FXMLLoader loader = new FXMLLoader(getClass().getResource("EntityList.fxml"));
		Node entityListNode = loader.load();

		entityList = loader.getController();
		rightVBox.getChildren().add(entityListNode);

		// We then set it's CellFactory
		entityList.setCellFactory(_ -> new EntityList.BaseAnalysisEntityCell() {
			@Override
			protected void updateCellText(AnalysisEntity entity) {

				// We set the text of the cell:
				if (treeRoot.equals(currentTreeItem)) {

					// If the selected directory is the root of the tree, we show only it's name
					setText(entity.getName());
				} else {

					// If the selected directory is not the root of the tree, we show its relative path
					setText(entity.getPath().substring(treeRoot.getValue().getPath().length()));
				}
			}
		});

		// We initialize the loadingAnimation with its animation update
		loadingAnimation = new LoadingAnimation(() -> resultTable.setPlaceholder(new Label("Analyzing" + ".".repeat(Math.max(0, loadingAnimation.getAnimationDotCount())))));
	}


	// --------------------- FXML functions ---------------------

	// Function called by the "Open" Button
	@FXML
	private void openCurrentPath() {
		EntityList.openPath(currentTreeItem.getValue().getPath());
	}


	// --------------------- Public functions ---------------------

	// Public function for DirectoryDetails setup
	public void setCurrentTreeItem(TreeItem<AnalysisEntity> treeRoot, TreeItem<AnalysisEntity> currentTreeItem) {
		this.treeRoot = treeRoot;
		this.currentTreeItem = currentTreeItem;

		// We set the current path:
		String currentPath = currentTreeItem.getValue().getPath();
		if (treeRoot.equals(currentTreeItem)) {

			// If the currentTreeItem is the root of the file tree, we show its full path
			selectedPathLabel.setText(currentPath);
		} else {

			// If the currentTreeItem is not the root of the file tree, we show its relative path
			selectedPathLabel.setText(currentPath.substring(treeRoot.getValue().getPath().length()));
		}

		// Finally, we start the search to get the FileTypeResults
		startSearch();
	}


	// --------------------- Private functions ---------------------

	// Function called at the start of the search
	private void startAnimation() {
		loadingAnimation.start();

		// We also clear the previous results, even if there should not be any
		resultTable.getItems().clear();
		percentageBar.getChildren().clear();
		entityList.getItems().clear();
	}

	// Function called at the end of the search
	private void stopAnimation() {
		loadingAnimation.stop();

		// We also reset the resultTable placeholder, used for the animation
		resultTable.setPlaceholder(new Label(""));
	}


	// Parallel search function
	private void startSearch() {
		startAnimation();

		// We will store the FileTypeResults and their relative file type concurrently
		ConcurrentHashMap<String, FileTypeResults> fileTypeToResult = new ConcurrentHashMap<>();
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

		try {
			// We search the current directory subtree
			searchSubTree(currentTreeItem, fileTypeToResult, executor);
		} catch (Exception e) {
			System.err.println(e.getMessage());
		} finally {
			// When the search concluded:
			stopAnimation();

			// We calculate the totals to calculate the percentages and show them on the "Total" resultTable's row
			int totalFileCount = fileTypeToResult.values().stream().mapToInt(FileTypeResults::fileCount).sum();
			int totalLineCount = fileTypeToResult.values().stream().mapToInt(FileTypeResults::lineCount).sum();
			long totalStorageSize = fileTypeToResult.values().stream().mapToLong(FileTypeResults::storageSize).sum();

			// We build the percentageBar
			buildPercentageBar(fileTypeToResult, totalFileCount, totalLineCount, totalStorageSize);
			// And add a listener to the percentChoiceTabPane, so when the value to show is changed, the percentageBar will be updated
			percentChoiceTabPane.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> buildPercentageBar(fileTypeToResult, totalFileCount, totalLineCount, totalStorageSize));

			// We then fill the resultTable with the results of the file types
			resultTable.getItems().setAll(fileTypeToResult.values());
			// And add the "Total" row at the bottom containing the already calculated sums of the values
			resultTable.getItems().addLast(new FileTypeResults("Total", totalFileCount, totalLineCount, totalStorageSize, "#FFFFFF"));
		}
	}

	// Recursive search function
	private void searchSubTree(TreeItem<AnalysisEntity> node, ConcurrentHashMap<String, FileTypeResults> fileTypeToResult, ExecutorService executor) throws Exception {
		AnalysisEntity entity = node.getValue();

		// If the entity is not displayed on the graph, then we don't search it and his children
		if (!entity.isDisplayed()) {
			return;
		}

		if (!entity.isDirectory()) {

			// If the entity isn't a directory, we can add its data to the results:
			String language = entity.getFileType();
			if (language != null) {

				// If the file type is supported, so it has data inside file_types.json:
				fileTypeToResult.compute(language, (_, result) -> {
					if (result == null) {

						// If the file type isn't already in the map, we add its result
						return new FileTypeResults(language, 1, entity.getLineCount(), entity.getStorageSize(), entity.getColor());
					} else {

						// If the file type is already in the map, we replace its result with a new one, adding the entity data
						return new FileTypeResults(language, result.fileCount() + 1, result.lineCount() + entity.getLineCount(), result.storageSize() + entity.getStorageSize(), entity.getColor());
					}
				});
			}
		}

		// After processing the Entity, we iterate on its children
		List<Future<Void>> futures = new ArrayList<>();
		for (TreeItem<AnalysisEntity> child : node.getChildren()) {

			// For each child, we add a searchTask to the executor
			Future<Void> future = executor.submit(() -> {
				try {
					searchSubTree(child, fileTypeToResult, executor);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				return null;
			});
			futures.add(future);
		}

		// We then wait for the searches to finish
		for (Future<Void> future : futures) {
			future.get();
		}
	}


	// Function to fill the percentageBar and its legend
	private void buildPercentageBar(Map<String, FileTypeResults> fileTypeToResult, int totalFileCount, int totalLineCount, long totalStorageSize) {

		// We clear the previous results
		percentageBar.getChildren().clear();
		legendGridPane.getChildren().clear();

		// We will need to remember the current place in the legend
		int legendLines = 0;
		int legendColumns = 0;

		// We iterate the file types:
		for (Map.Entry<String, FileTypeResults> entry : fileTypeToResult.entrySet()) {
			String language = entry.getKey();
			FileTypeResults result = entry.getValue();
			double percentage = 0;

			// Depending on the percentage choice, we calculate the percentage of the file type
			if (percentChoiceTabPane.getSelectionModel().getSelectedItem().equals(fileCountTab)) {
				percentage = (result.fileCount() * 100.0) / (totalFileCount == 0 ? 1 : totalFileCount);
			} else if (percentChoiceTabPane.getSelectionModel().getSelectedItem().equals(lineCountTab)) {
				percentage = (result.lineCount() * 100.0) / (totalLineCount == 0 ? 1 : totalLineCount);
			} else if (percentChoiceTabPane.getSelectionModel().getSelectedItem().equals(storageSizeTab)) {
				percentage = (result.storageSize() * 100.0) / (totalStorageSize == 0 ? 1 : totalStorageSize);
			}

			// We ignore null values, like the number of lines inside image files
			if (percentage != 0) {
				double segmentWidth = (percentage / 100.0) * 735; // 735 is the percentageBar width
				String color = result.color();

				// We create the percentageBar segment container:
				Pane block = new Pane();
				block.setPrefWidth(segmentWidth);
				block.setMinWidth(segmentWidth);
				block.setMaxWidth(segmentWidth);
				block.setMaxHeight(30);
				block.setStyle("-fx-border-color: black; -fx-border-width: 0.3px; -fx-background-color: " + color + ";");
				block.setCursor(Cursor.HAND);

				// We then set the user data and click handler, both for the file type selection
				block.setUserData(language);
				block.addEventHandler(MouseEvent.MOUSE_CLICKED, selectFileTypeHandler);

				// The block is ready to be added to the percentageBar
				percentageBar.getChildren().add(block);

				// We then create the legend entry:
				HBox legendEntry = new HBox(10); // spacing di 10 pixel
				legendEntry.setAlignment(Pos.CENTER_LEFT);
				legendEntry.setMaxHeight(15);
				legendEntry.setPadding(new Insets(1));
				legendEntry.setCursor(Cursor.HAND);

				// We then set the user data and click handler, both for the file type selection
				legendEntry.setUserData(language);
				legendEntry.addEventHandler(MouseEvent.MOUSE_CLICKED, selectFileTypeHandler);

				// We add the square containing the file type color
				Pane colorSquare = new Pane();
				colorSquare.setPrefSize(15, 15);
				colorSquare.setStyle("-fx-border-color: black; -fx-border-width: 1px; -fx-background-color: " + color + ";");
				legendEntry.getChildren().add(colorSquare);

				// We add the label for the file type name and percentage
				Label nameLabel = new Label(language + String.format("   %.3f%%", percentage));
				nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
				legendEntry.getChildren().add(nameLabel);

				// The entry is ready to be added to the percentageBar
				legendGridPane.add(legendEntry, legendLines, legendColumns);

				// We update the line and column numbers
				legendLines++;
				// 3 is the max number of columns
				if (legendLines == 3) {
					legendLines = 0;
					legendColumns++;
				}
			}
		}
	}


	// Recursive helper function to find Entities with the same fileType in the TreeItem subtree
	private List<AnalysisEntity> getFilesForType(TreeItem<AnalysisEntity> node, String fileType) {
		List<AnalysisEntity> results = new ArrayList<>();

		AnalysisEntity entity = node.getValue();
		if (!entity.isDirectory()) {

			// If the entity is not a directory, we check for its file type and if it is displayed on the graph
			if (fileType.equals(entity.getFileType()) && entity.isDisplayed()) {
				results.add(entity);
			}
		}

		// After adding the node result, we iterate on its children calling the recursion
		for (TreeItem<AnalysisEntity> child : node.getChildren()) {
			results.addAll(getFilesForType(child, fileType));
		}

		return results;
	}

}
