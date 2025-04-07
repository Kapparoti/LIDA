package org.lida.Interface;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxgraph.layout.*;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.view.mxGraph;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.lida.Entity.AnalysisEntity;
import org.lida.Functionality.DirectoryAnalyzer;
import org.lida.Functionality.GraphCreator;
import org.lida.Settings.SettingsHandler;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.shape.Polygon;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;


// Controller of LIDA.fxml, the main interface of the application
public class LIDAController {

	// --------------------- FXML nodes ---------------------

	// MenuItems inside the "File" Menu:
	@FXML
	private MenuItem selectNewDirectoryMenuItem;
	// Flag to avoid opening multiple file choosers at the same time
	private boolean isChooserOpen = false;

	@FXML
	private Menu selectRecentDirectoryMenu;
	@FXML
	private MenuItem graphImageMenuItem;
	@FXML
	private MenuItem closeCurrentMenuItem;
	@FXML
	private MenuItem exportGraphMenuItem;


	// Settings Menu and its MenuItems
	@FXML
	private Menu settingsMenu;
	@FXML
	private CheckMenuItem filesNoExtMenuItem;
	@FXML
	private CheckMenuItem hiddenDirMenuItem;
	@FXML
	private CheckMenuItem showSingleEntitiesMenuItem;

	@FXML
	private Menu graphLayoutMenu;

	// Available graph layouts
	public enum GraphLayouts {hierarchical, organic, tree, circle}


	// TabPane to switch between different graph structure views
	@FXML
	private TabPane structureTabPane;

	// Available graph structure choices
	private enum StructureChoices {File, Dependency}

	// Current chosen graph structure
	private static StructureChoices structureChoice;


	// Label showing the currently selected directory's path
	@FXML
	private Label selectedDirectoryLabel;
	// Button to return to the root of the graph
	@FXML
	private Button returnToRootButton;

	// Label showing the count of entities shown in the graph
	@FXML
	private Label shownEntitiesLabel;


	// ChoiceBox to select which type of files to show
	@FXML
	private ChoiceBox<String> showChoiceBox;

	// Available file show options
	private enum ShowChoices {ShowOnlyCode, ShowOnlyOther, ShowBoth}

	// Current choosen file show option
	private static ShowChoices selectedShowChoice;


	// Pane to contain the graph and loading messages
	@FXML
	private AnchorPane graphContainer;
	// Static reference to graphContainer for static methods
	private static AnchorPane staticGraphContainer;

	// Label showing the loading animation or error messages
	@FXML
	private Label loadingLabel;
	// Label showing the analysis log entries
	@FXML
	private Label loadingLogLabel;
	// Pane to contain file or directory details
	@FXML
	private Pane detailsContainer;

	// --------------------- Graph variables ---------------------

	// Graph's nodes radius
	private static final double NODE_RADIUS = 45;
	// Graph's arrows size
	private static final double ARROW_SIZE = 10;

	// Horizontal spacing between nodes
	private static final int HORIZONTAL_SPACING = 10;
	// Vertical spacing between nodes
	private static final int VERTICAL_SPACING = 150;

	// Variables to track the minimum and maximum coordinates of the graph
	private double minX, maxX, minY, maxY;

	// Helper function to get the graph's width
	private double getGraphWidth() {
		return maxX - minX;
	}

	// Helper function to get the graph's height
	private double getGraphHeight() {
		return maxY - minY;
	}

	// Graph's Group used for panning and zooming
	private static final Group graphGroup = new Group();

	// Graph instance
	private mxGraph graph;

	// --------------------- Animations variables ---------------------

	// Loading animation instance
	private LoadingAnimation loadingAnimation;

	// Analysis log update TimeLine instance
	private Timeline logUpdateTimeline;


	// Panning Transition instance
	private static final TranslateTransition transition = new TranslateTransition(Duration.seconds(0.5), graphGroup);

	// Helper function to check if the transition is playing
	private static boolean isInTransition() {
		return transition.getStatus().equals(Animation.Status.RUNNING);
	}

	// --------------------- Class variables ---------------------

	// Recend directory storage file
	private final File RECENT_DIRS_FILE;


	// DirectoryAnalyzer instance for the analysis
	private final DirectoryAnalyzer directoryAnalyzer = new DirectoryAnalyzer();


	// Currently selected entity
	private AnalysisEntity currentEntity;


	// Dependency root calculated for the dependency graph structure
	private AnalysisEntity dependencyRoot;

	// --------------------- Constructor ---------------------

	// Loads the RECENT_DIRS_FILE
	public LIDAController() {
		try {
			RECENT_DIRS_FILE = new File(Objects.requireNonNull(getClass().getResource("recent_dirs.json")).toURI());
		} catch (Exception e) {
			System.err.println("ERROR: Unable to load recent_dirs.json");
			throw new RuntimeException(e);
		}
	}

	// --------------------- Initialization ---------------------

	public void initialize() {
		// Set up the loading animation for the loading label with a callback to update its text
		loadingAnimation = new LoadingAnimation(() -> loadingLabel.setText("Loading" + ".".repeat(loadingAnimation.getAnimationDotCount())));

		// Initialize log update timeline to refresh log display every 100 milliseconds
		logUpdateTimeline = new Timeline(new KeyFrame(Duration.millis(100), _ -> updateLog()));
		logUpdateTimeline.setCycleCount(Timeline.INDEFINITE);

		// We clear the current entity details
		clearDetails();

		// We add the directory selection action on the select new directory menu item
		selectNewDirectoryMenuItem.setOnAction(_ -> handleDirectorySelection(selectNewDirectoryMenuItem.getText()));
		// Initial refresh of the recent directory menu
		refreshOpenRecentDirectoryMenu();

		// We set the default choice for the graph structure
		structureTabPane.getSelectionModel().selectFirst();
		structureChoice = StructureChoices.File;
		// We change the selected choice and create the graph when a new graph structure gets selected.
		// We also update the graph layout menu because it has two different stored settings, one for each structure graph
		structureTabPane.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> {
			structureChoice = StructureChoices.values()[structureTabPane.getSelectionModel().getSelectedIndex()];
			updateGraphLayoutMenu();
			createGraph();
		});

		// We fill the graph layout menu with all available graph layout options
		for (GraphLayouts layout : GraphLayouts.values()) {

			// We create and add a new MenuItem with the layout name
			MenuItem menuItem = new MenuItem(layout.name());
			graphLayoutMenu.getItems().add(menuItem);

			// We then set its action to:
			menuItem.setOnAction(_ -> {

				// Update the stored settings,
				if (structureChoice.equals(StructureChoices.File)) {
					SettingsHandler.setFileGraphLayout(layout.name());
				} else {
					SettingsHandler.setDependencyGraphLayout(layout.name());
				}

				// Update the graph layout Menu text
				updateGraphLayoutMenu();

				// And create the graph
				createGraph();
			});
		}
		// Initial graph layout Menu text update
		updateGraphLayoutMenu();

		// We set the default choice for the show option
		showChoiceBox.getSelectionModel().selectFirst();
		selectedShowChoice = ShowChoices.ShowOnlyCode;
		// When a new show option gets selected, we change the selected choice and create the graph
		showChoiceBox.setOnAction(_ -> {
			selectedShowChoice = ShowChoices.values()[showChoiceBox.getSelectionModel().getSelectedIndex()];
			createGraph();
		});

		// Then we add the graph group to the graph container
		graphContainer.getChildren().add(graphGroup);
		staticGraphContainer = graphContainer;

		// Configure zoom: we add a handler on the scroll event
		graphContainer.setOnScroll((ScrollEvent event) -> {

			// Zoom during transitions is ignored
			if (isInTransition()) return;

			// We get the base values
			double scaleFactor = (event.getDeltaY() > 0) ? 1.1 : 1 / 1.1;
			double oldScale = graphGroup.getScaleX();
			double newScale = oldScale * scaleFactor;

			// We calculate the minimum allowed scale (so the maximum zoom) based on the graph size, and it's container dimensions
			double minScale = Math.min(graphContainer.getWidth() / (getGraphWidth() * 1.5), graphContainer.getHeight() / (getGraphHeight() * 1.5));

			// If the new scale respects the boundary values:
			if (newScale > minScale && newScale < 5) {

				// We calculate the pivot point in local coordinates
				Point2D pivotInLocal = graphContainer.sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
				Point2D posInZoomGroup = graphGroup.parentToLocal(pivotInLocal);

				// Then we apply the new scale
				graphGroup.setScaleX(newScale);
				graphGroup.setScaleY(newScale);

				// Finally, we adjust translation to keep the pivot point stable during zoom
				Point2D delta = pivotInLocal.subtract(graphGroup.localToParent(posInZoomGroup));
				graphGroup.setTranslateX(graphGroup.getTranslateX() + delta.getX());
				graphGroup.setTranslateY(graphGroup.getTranslateY() + delta.getY());

				event.consume();
			}
		});

		// Configure panning: we store the last mouse position
		final ObjectProperty<Point2D> lastMouseCoordinates = new SimpleObjectProperty<>();
		// When the mouse gets presset, we update its position
		graphContainer.setOnMousePressed(event -> lastMouseCoordinates.set(new Point2D(event.getSceneX(), event.getSceneY())));
		// When the mouse gets dragged, we apply the panning:

		graphContainer.setOnMouseDragged(event -> {

			// We calculate the movement the mouse has done
			double deltaX = event.getSceneX() - lastMouseCoordinates.get().getX();
			double deltaY = event.getSceneY() - lastMouseCoordinates.get().getY();

			// And we apply it to the graph group
			graphGroup.setTranslateX(graphGroup.getTranslateX() + deltaX);
			graphGroup.setTranslateY(graphGroup.getTranslateY() + deltaY);

			// Then, we update the mouse position
			lastMouseCoordinates.set(new Point2D(event.getSceneX(), event.getSceneY()));

			// We can also change the Cursor to match the panning
			graphContainer.setCursor(Cursor.CLOSED_HAND);
		});

		// To restore the Cursor after the panning, we set the default Cursor on mouse releases
		graphContainer.setOnMouseReleased(_ -> graphContainer.setCursor(Cursor.DEFAULT));
		graphContainer.setCursor(Cursor.DEFAULT);

		// We check if there are any recent directories
		List<String> recentDirs = loadRecentDirectoriesFromFile();
		if (recentDirs.isEmpty()) {

			// If there are no recent directories, we select none
			closeCurrentDirectory();
		} else {

			// If there are, we automatically open the last one
			handleDirectorySelection(recentDirs.getFirst());
		}
	}

	// Updates the graph layout Menu text with the current setting stored, depending on the graph structure choice
	private void updateGraphLayoutMenu() {
		if (structureChoice.equals(StructureChoices.File)) {
			graphLayoutMenu.setText("Current layout: " + SettingsHandler.getFileGraphLayout());
		} else {
			graphLayoutMenu.setText("Current layout: " + SettingsHandler.getDependencyGraphLayout());
		}
	}

	// Helper function to get the current root of the graph, depending on its structure
	private AnalysisEntity getCurrentGraphRoot() {
		if (structureChoice == StructureChoices.File) {
			return directoryAnalyzer.getAnalysisTreeRoot().getValue();
		} else {
			return dependencyRoot;
		}
	}

	// --------------------- FXML functions ---------------------

	// Sets the application as no directory is selected
	@FXML
	private void closeCurrentDirectory() {

		// We clear the class variables,
		currentEntity = null;
		directoryAnalyzer.clear();

		// We show to the user that there is no directory selected
		selectedDirectoryLabel.setText("No directory selected");
		shownEntitiesLabel.setText("");
		printMessage("");
		flushLog();
		graphGroup.getChildren().clear();
		clearDetails();

		// And we disable graph options and interactions
		disableCurrentDirectoryOptions(true);
		disableGraphInteractions(true);
	}

	// Exports the graph in a supported format
	@FXML
	private void exportGraph() {

		// We use the isChooserOpen flag
		if (isChooserOpen) return;
		isChooserOpen = true;

		// FileChooser configuration
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select path to export graph");

		// For each supported format we add its extension filter
		for (GraphCreator.graphFormats format : GraphCreator.graphFormats.values()) {
			String formatString = format.toString();
			fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(formatString.toUpperCase() + " (*." + formatString + ")", "*." + formatString));
		}

		// The FileChooser is ready, so we wait for the result
		File file = fileChooser.showSaveDialog(null);

		isChooserOpen = false;
		if (file != null) {

			// We get the format depending on the chosen filter
			GraphCreator.graphFormats format = GraphCreator.graphFormats.json;
			FileChooser.ExtensionFilter selectedFilter = fileChooser.getSelectedExtensionFilter();

			if (selectedFilter != null) {
				if (selectedFilter.getExtensions().getFirst().contains(GraphCreator.graphFormats.xml.toString())) {
					format = GraphCreator.graphFormats.xml;
				} else if (selectedFilter.getExtensions().getFirst().contains(GraphCreator.graphFormats.plantuml.toString())) {
					format = GraphCreator.graphFormats.plantuml;
				}
			}

			// We then use GraphCreator to export the graph
			GraphCreator.exportGraph(graph, file.getAbsolutePath(), format);
		}
	}

	// Opens github link
	@FXML
	private void showAbout() {
		try {
			String url = "https://github.com/Kapparoti/LICA";
			Desktop.getDesktop().browse(new URI(url));
		} catch (Exception ex) {
			printMessage(ex.getMessage());
		}
	}

	// Updates the settings MenuItems selections based on the stored settings
	@FXML
	private void onSettingsShowing() {
		filesNoExtMenuItem.setSelected(SettingsHandler.getFilesNoExt());
		hiddenDirMenuItem.setSelected(SettingsHandler.getHiddenDirectories());
		showSingleEntitiesMenuItem.setSelected(SettingsHandler.getSingleEntities());
	}

	// Toggles the display of files without extensions and recreates the graph.
	@FXML
	private void toggleFilesNoExt() {
		SettingsHandler.toggleFilesNoExt();
		createGraph();
	}

	// Toggles the display of hidden directories and recreates the graph.
	@FXML
	private void toggleHiddenDir() {
		SettingsHandler.toggleHiddenDirectories();
		createGraph();
	}

	// Toggles the display of single entities (if applicable) and recreates the graph.
	@FXML
	private void toggleShowSingleEntities() {
		SettingsHandler.toggleSingleEntities();
		createGraph();
	}

	// Restores settings to default values and recreates the graph.
	@FXML
	private void settingsToDefault() {
		SettingsHandler.restoreToDefault();
		updateGraphLayoutMenu();
		createGraph();
	}

	// Moves the view back to the root of the graph.
	@FXML
	private void returnToRoot() {
		moveToEntity(getCurrentGraphRoot());
	}

	// Exports the graph as an image
	@FXML
	private void saveGraphImage() {
		// FileChooser configuration
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select where to save the new image");

		// Add supported formats
		FileChooser.ExtensionFilter pngFilter = new FileChooser.ExtensionFilter("PNG (*.png)", "*.png");
		FileChooser.ExtensionFilter jpgFilter = new FileChooser.ExtensionFilter("JPG (*.jpg)", "*.jpg");
		fileChooser.getExtensionFilters().addAll(pngFilter, jpgFilter);

		// The FileChooser is ready, so we wait for the result
		File file = fileChooser.showSaveDialog(null);

		if (file != null) {
			// We get the file extension based on the selected filter (png by default)
			String ext = "png";
			FileChooser.ExtensionFilter selectedFilter = fileChooser.getSelectedExtensionFilter();
			if (selectedFilter != null && selectedFilter.getExtensions().getFirst().contains("jpg")) {
				ext = "jpg";
			}

			// We can use the snapshot function to get an image of the graph group
			WritableImage immagine = graphGroup.snapshot(new SnapshotParameters(), null);

			try {
				// Write the image file using the chosen image format
				ImageIO.write(SwingFXUtils.fromFXImage(immagine, null), ext, file);
			} catch (IOException e) {
				printMessage(e.getMessage());
			}
		}
	}

	// --------------------- User comunication functions ---------------------

	// Helper function to display a message on the loading label
	private void printMessage(String message) {
		loadingLabel.setVisible(true);
		loadingLabel.setText(message);
	}

	// Updates the log label with the analysis log contents
	private void updateLog() {
		loadingLogLabel.setText(directoryAnalyzer.getLogString());
	}

	// Clears the analysis log label
	private void flushLog() {
		loadingLogLabel.setText("");
	}

	// --------------------- Directory management functions ---------------------

	// Loads the recent directories list from the dedicated file
	private List<String> loadRecentDirectoriesFromFile() {
		List<String> result = new ArrayList<>();

		// We check for recent directories
		if (RECENT_DIRS_FILE.length() > 0) {
			try {

				// Using an ObjectMapper, we fill the result with the contents of the recent directories file
				ObjectMapper mapper = new ObjectMapper();
				result = mapper.readValue(RECENT_DIRS_FILE, new TypeReference<>() {
				});
			} catch (Exception e) {
				printMessage(e.getMessage());
			}
		}
		return result;
	}

	// Adds a new directory to the list of the recently used ones
	private void updateRecentDirectories(String newDirectory) {

		// We first get the recently used directories
		List<String> dirs = loadRecentDirectoriesFromFile();

		// Then add or move the new directory to the top
		dirs.remove(newDirectory);
		dirs.addFirst(newDirectory);

		// If the recent directories limit is reached, we remove the last one
		if (dirs.size() > 10) dirs.removeLast();

		try {
			// Finally, we can write the list value on the recent directories file
			ObjectMapper mapper = new ObjectMapper();
			mapper.writeValue(RECENT_DIRS_FILE, dirs);
		} catch (IOException e) {
			printMessage(e.getMessage());
		}
	}

	// We read the recent directories and add them to the recent directories Menu
	private void refreshOpenRecentDirectoryMenu() {

		// We first clear the select recent directory menu and disable it in case there are no recent directories
		selectRecentDirectoryMenu.getItems().clear();
		selectRecentDirectoryMenu.setDisable(true);

		// Then, we iterate for each recent directory
		for (String recentDir : loadRecentDirectoriesFromFile()) {
			selectRecentDirectoryMenu.setDisable(false);

			// We create a MenuItem for each and add the directory selection action on it
			MenuItem item = new MenuItem(recentDir);
			item.setOnAction(_ -> handleDirectorySelection(item.getText()));
			selectRecentDirectoryMenu.getItems().add(item);
		}
	}


	// Handles the directory selection, by both the select new directory or by a recent directory MenuItem
	private void handleDirectorySelection(String selected) {

		// If the user selected the most recently used directory, we ignore it because it's already the current one
		AnalysisEntity rootEntity = directoryAnalyzer.getRootEntity();
		if (rootEntity != null && selected.equals(rootEntity.getPath())) return;

		// We check if the call comes from the select new directory MenuItem
		if (selectNewDirectoryMenuItem.getText().equals(selected)) {

			// For the select new directory MenuItem, we need to open a DirectoryChooser
			if (isChooserOpen) return;
			isChooserOpen = true;

			// DirectoryChooser configuration
			DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Select directory to analyze");

			// The DirectoryChooser is ready, so we wait for the result
			File newDir = directoryChooser.showDialog(null);

			isChooserOpen = false;
			// If the selected directory is valid, we start the analysis on it
			if (newDir != null && newDir.exists() && newDir.isDirectory() && (rootEntity == null || !newDir.getAbsolutePath().equals(rootEntity.getPath()))) startAnalysis(newDir.getAbsolutePath());
		} else {
			// Otherwise, we start the analysis on the selected recent directory
			startAnalysis(selected);
		}
	}

	// Starts the analysis on the selected directory
	private void startAnalysis(String path) {

		// First, we clear the old state of the application
		clearDetails();
		disableGraphInteractions(true);

		// We update the situation for the newly analyzed directory
		disableCurrentDirectoryOptions(false);
		selectedDirectoryLabel.setText("Selected directory: " + path);
		updateRecentDirectories(path);
		refreshOpenRecentDirectoryMenu();

		// Then, we start the loading animation
		startAnimation();

		// Then, we use the directory analyzer to analyze the new directory
		directoryAnalyzer.startAnalysis(new File(path)).setOnSucceeded(_ -> {

			// At the end of the analysis, we can stop the loading animation and create the graph
			stopAnimation();
			createGraph();
		});
	}

	// --------------------- Graph visualization functions ---------------------

	// Creates the graph based on the current analysis tree and settings
	private void createGraph() {
		if (directoryAnalyzer.getAnalysisTreeRoot() == null) return;
		currentEntity = null;

		// Reset zoom and start loading animation
		resetZoom();
		startAnimation();
		clearDetails();
		graphGroup.getChildren().clear();

		// Reset graph bounds before creating a new graph
		minX = Double.MAX_VALUE;
		maxX = Double.MIN_VALUE;
		minY = Double.MAX_VALUE;
		maxY = Double.MIN_VALUE;

		// Depending on the graph structure choice, we create the new graph
		Group result;
		try {
			if (structureChoice == StructureChoices.File) {
				result = createFileGraph(directoryAnalyzer.getAnalysisTreeRoot(), GraphLayouts.valueOf(SettingsHandler.getFileGraphLayout()));
			} else {
				result = createDependencyGraph(GraphLayouts.valueOf(SettingsHandler.getDependencyGraphLayout()));
			}
		} catch (Exception e) {

			// If we catch an error during the graph creation, we stop and display the error
			stopAnimation();
			printMessage("Error during graph creation");
			System.err.println("Error during graph creation: " + e.getMessage());
			return;
		}

		// We stop the loading animation and analyze the result:
		stopAnimation();
		if (result == null) {

			// No graph has been created, so there are zero displayed entitites
			shownEntitiesLabel.setText("");
			printMessage("Nothing found");
		} else {
			// We add the graph to the graph group
			graphGroup.getChildren().add(result);

			// We calculate and set the displayed Entities count
			shownEntitiesLabel.setText(String.format("Shown entities: %d", directoryAnalyzer.getDisplayedEntitiesNumber(directoryAnalyzer.getAnalysisTreeRoot())));

			// We click the root Entity to start a transition on it and display its details
			handleSquareClicked(getCurrentGraphRoot());

			// Finally, we enable graph interactions
			disableGraphInteractions(false);
		}
	}


	// Helper function to get the right layout for the graph, depending on the user settigns
	private static mxIGraphLayout getGraphLayout(mxGraph mxGraph, GraphLayouts layout) {

		// For each layout, we create it and apply the spacing if possible
		switch (layout) {
			case circle: {
				return new mxCircleLayout(mxGraph, HORIZONTAL_SPACING);
			}
			case organic: {
				mxFastOrganicLayout fastOrganicLayout = new mxFastOrganicLayout(mxGraph);

				// Set spacing
				fastOrganicLayout.setForceConstant(1.5 * VERTICAL_SPACING);
				fastOrganicLayout.setMinDistanceLimit(10);
				return fastOrganicLayout;
			}
			case tree: {
				mxCompactTreeLayout treeLayout = new mxCompactTreeLayout(mxGraph, false);
				treeLayout.setEdgeRouting(false);

				// Set spacing
				treeLayout.setLevelDistance(VERTICAL_SPACING);
				treeLayout.setNodeDistance(HORIZONTAL_SPACING);
				return treeLayout;
			}
			default: {
				mxHierarchicalLayout hierarchicalLayout = new mxHierarchicalLayout(mxGraph);
				hierarchicalLayout.setOrientation(SwingConstants.SOUTH);

				// Set spacing
				hierarchicalLayout.setInterRankCellSpacing(VERTICAL_SPACING);
				hierarchicalLayout.setIntraCellSpacing(HORIZONTAL_SPACING);
				return hierarchicalLayout;
			}
		}
	}

	// Helper function to determine if an Entity should be displayed, depending on the user visualization settigns
	public static boolean shouldDisplayEntity(AnalysisEntity entity) {
		if (selectedShowChoice == ShowChoices.ShowBoth) {

			// If both code and other can be shown, we check for the file's extension
			return (!entity.getExtension().isEmpty() || SettingsHandler.getFilesNoExt());
		} else if (selectedShowChoice == ShowChoices.ShowOnlyCode) {

			// If only code can be shown, we don't care for the file's extension because all code files have extensions
			return entity.isCode();
		} else if (!entity.isCode()) {

			// If only other files can be shown, se check for the file's extension
			return (!entity.getExtension().isEmpty() || SettingsHandler.getFilesNoExt());
		}
		return false;
	}


	// Creates a graph based on the file structure
	private Group createFileGraph(TreeItem<AnalysisEntity> root, GraphLayouts graphLayout) throws ExecutionException, InterruptedException {
		graph = new mxGraph();
		Object parent = graph.getDefaultParent();

		// Map each AnalysisEntity in the tree to its corresponding graph vertex
		// That's needed because the graph does not allow getting a vertex from its AnalysisEntity value.
		// And also, because we can iterate on them later
		Map<AnalysisEntity, Object> entityToVertex = new HashMap<>();

		// We use Graph Creator to fill the file graph
		GraphCreator.fillFileGraph(root, graph, parent, entityToVertex);

		// We apply the requested layout
		mxIGraphLayout treeLayout = getGraphLayout(graph, graphLayout);
		treeLayout.execute(parent);

		// We then update positions for each entity
		setEntitiesCoords(graph, entityToVertex);

		// Finally, we can draw and return the graph visualization
		return drawGraph(graph);
	}

	// Creates a graph based on the dependency structure
	private Group createDependencyGraph(GraphLayouts graphLayout) throws ExecutionException, InterruptedException {
		graph = new mxGraph();
		Object parent = graph.getDefaultParent();

		// Map each AnalysisEntity in the tree to its corresponding graph vertex
		// That's needed because the graph does not allow getting a vertex from its AnalysisEntity value.
		// And also, because we can iterate on them later
		Map<AnalysisEntity, Object> entityToVertex = new HashMap<>();

		// We use GraphCreator to fill the dependency graph
		GraphCreator.fillDependencyGraph(directoryAnalyzer.getAnalysisTreeRoot(), graph, parent, entityToVertex);

		// We apply the requested layout
		mxIGraphLayout layout = getGraphLayout(graph, graphLayout);
		layout.execute(parent);

		// We then update positions for each entity
		setEntitiesCoords(graph, entityToVertex);

		// Finally, we can determine the dependency root based on the highest number of incoming Dependencies
		dependencyRoot = entityToVertex.keySet().stream()

				// We compare the number of incoming edges in the graph, using the vertex that we get from the map
				.max(Comparator.comparingInt(entity -> graph.getIncomingEdges(entityToVertex.get(entity)).length)).orElse(null);

		// Finally, we can draw and return the graph visualization
		return drawGraph(graph);
	}


	// Creates the visualization of the mxGraph
	private Group drawGraph(mxGraph graph) {
		Object parent = graph.getDefaultParent();
		Group finalGroup = new Group();
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

		// First, we draw the vertices of the mxGraph
		Object[] vertices = graph.getChildVertices(parent);
		try {
			graph.getModel().beginUpdate();

			// We then draw each vertex
			for (Object vertex : vertices) {
				executor.submit(() -> {
					// We retrieve the corresponding AnalysisEntity
					AnalysisEntity entity = (AnalysisEntity) graph.getModel().getValue(vertex);

					// We use the getResult function to create the visual node
					Group nodeGroup = getResult(entity, entity.getY(), entity.getX());

					// And schedule the addition of the node to the visual graph
					Platform.runLater(() -> finalGroup.getChildren().add(nodeGroup));
				});
			}

			// Then, we draw the edges between the vertices
			for (Object edge : graph.getChildEdges(parent)) {
				executor.submit(() -> {

					// Each edge has a source and a target, and we get their Entity values
					AnalysisEntity sourceEntity = (AnalysisEntity) graph.getModel().getValue(graph.getModel().getTerminal(edge, true));
					AnalysisEntity targetEntity = (AnalysisEntity) graph.getModel().getValue(graph.getModel().getTerminal(edge, false));

					// From the entities, we can get the points that we need
					Point2D start = new Point2D(sourceEntity.getX(), sourceEntity.getY());
					Point2D end = new Point2D(targetEntity.getX(), targetEntity.getY());

					// We can then calculate the direction and distance between the start and end
					double xDirection = end.getX() - start.getX();
					double yDirection = end.getY() - start.getY();
					double dist = Math.sqrt(xDirection * xDirection + yDirection * yDirection);

					// Avoid division by zero
					if (dist == 0) return;

					// We use the direction and distance to make the arrow start and end on the node's circumnference
					double offsetX = xDirection / dist * NODE_RADIUS;
					double offsetY = yDirection / dist * NODE_RADIUS;
					double lineStartX = start.getX() + offsetX;
					double lineStartY = start.getY() + offsetY;
					double lineEndX = end.getX() - offsetX;
					double lineEndY = end.getY() - offsetY;

					// We can now create the arrow's line
					Line line = new Line(lineStartX, lineStartY, lineEndX, lineEndY);
					line.setStroke(Color.GRAY);

					// Then, we calculate the points for the arrow head
					double angle = Math.atan2(lineEndY - lineStartY, lineEndX - lineStartX);
					double firstX = lineEndX - ARROW_SIZE * Math.cos(angle - Math.PI / 6);
					double firstY = lineEndY - ARROW_SIZE * Math.sin(angle - Math.PI / 6);
					double secondX = lineEndX - ARROW_SIZE * Math.cos(angle + Math.PI / 6);
					double secondY = lineEndY - ARROW_SIZE * Math.sin(angle + Math.PI / 6);

					// Finally, we can create the arrow head polygon
					Polygon arrow = new Polygon(lineEndX, lineEndY, firstX, firstY, secondX, secondY);
					arrow.setFill(Color.GRAY);

					// And schedule the addition of its node to the visual graph
					Platform.runLater(() -> {
						finalGroup.getChildren().addAll(line, arrow);
						line.toBack();
						arrow.toBack();
					});
				});
			}

			// We then wait for the termination of all tasks
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.HOURS);

		} catch (InterruptedException e) {
			System.err.println("Error during graph drawing: " + e.getMessage());
		} finally {
			graph.getModel().endUpdate();
		}

		// If there aren't any vertices, we return null
		if (vertices.length < 1) return null;
		return finalGroup;
	}


	// Updates all the Entities positions based on the given mxGraph
	private void setEntitiesCoords(mxGraph mxGraph, Map<AnalysisEntity, Object> vertexMapping) {

		// We iterate for all vertexs
		for (Map.Entry<AnalysisEntity, Object> entry : vertexMapping.entrySet()) {
			AnalysisEntity entity = entry.getKey();
			Object vertex = entry.getValue();

			// Then we can get the vertex coordinates and set them to its Entity
			mxGeometry geom = mxGraph.getModel().getGeometry(vertex);
			entity.setX(geom.getX());
			entity.setY(geom.getY());

			// Update global graph bounds
			minX = Math.min(minX, geom.getX() - NODE_RADIUS);
			maxX = Math.max(maxX, geom.getX() + NODE_RADIUS);
			minY = Math.min(minY, geom.getY() - NODE_RADIUS);
			maxY = Math.max(maxY, geom.getY() + NODE_RADIUS);
		}
	}


	// Helper function to create the visual node of an AnalysisEntity
	private Group getResult(AnalysisEntity entity, double y, double x) {

		// We create the circle representing the entity, with its representing color
		Circle circle = new Circle(NODE_RADIUS);
		circle.setFill(Color.web(entity.getColor()));
		circle.setStroke(Color.BLACK);
		circle.setStrokeWidth(4);

		// We create a label for the entity's name
		Label label = new Label(entity.getName());
		label.setPadding(new Insets(7.5));
		label.setStyle("-fx-font-size: 13.5;");
		label.setWrapText(true);

		// We add a drop shadow effect to make labels visible also above dark colors
		DropShadow dropShadow = new DropShadow();
		dropShadow.setColor(Color.WHITE);
		dropShadow.setRadius(1.0);
		dropShadow.setSpread(0.5);
		label.setEffect(dropShadow);

		// We create a StackPane to hold the circle and the label
		StackPane circlePane = new StackPane();
		circlePane.setLayoutX(x - NODE_RADIUS);
		circlePane.setLayoutY(y - NODE_RADIUS);
		circlePane.setPrefSize(2 * NODE_RADIUS, 2 * NODE_RADIUS);
		circlePane.setCursor(Cursor.HAND);

		circlePane.getChildren().addAll(circle, label);

		// Then we can set a click handler to show details and transition to the node when it's clicked
		circlePane.setOnMouseClicked(_ -> handleSquareClicked(entity));

		// Then we set a hover listener to change the stroke color
		circlePane.hoverProperty().addListener((_, _, hovering) -> circle.setStroke(hovering ? Color.YELLOW : Color.BLACK));

		// Finally, we can return the circle pane's group
		return new Group(circlePane);
	}

	// Handles the user's click on an Entity's graph node
	private void handleSquareClicked(AnalysisEntity entity) {

		// First, we clear the old details
		clearDetails();

		// If the user clicked again on the same entity, we remove it from the current one and return
		if (entity.equals(currentEntity)) {
			currentEntity = null;
			return;
		}

		// We set the entity as the current one, and we move the view to it
		currentEntity = entity;
		moveToEntity(currentEntity);

		// We then need to show the details for the current Entity
		try {
			Node detailsNode;
			if (currentEntity.isDirectory()) {

				// If the Entity is a directory, we load the directory details
				FXMLLoader loader = new FXMLLoader(getClass().getResource("DirectoryDetails.fxml"));
				detailsNode = loader.load();

				// We then provide to the controller the data needed to work
				DirectoryDetails controller = loader.getController();
				controller.setCurrentTreeItem(directoryAnalyzer.getAnalysisTreeRoot(), directoryAnalyzer.getTreeItemFromEntity(currentEntity));
			} else {
				// If the Entity is a file, we load the file details
				FXMLLoader loader = new FXMLLoader(getClass().getResource("FileDetails.fxml"));
				detailsNode = loader.load();

				// We then provide to the controller the data needed to work
				FileDetails controller = loader.getController();
				controller.setFileEntity(directoryAnalyzer.getRootEntity(), currentEntity);
			}

			// Finally, we can add the details node to the details container
			detailsContainer.getChildren().add(detailsNode);
			graphContainer.setPrefHeight(565);
			detailsContainer.setPrefHeight(310);
		} catch (IOException ex) {

			// If we didn't find a file, we show a respective error message
			System.err.println("ERROR: unable to load " + (entity.isDirectory() ? "DirectoryDetails.fxml" : "FileDetails.fxml"));
			throw new RuntimeException(ex);
		}
	}

	// --------------------- Interface functions ---------------------

	// Disables the user interactions with the graph
	private void disableGraphInteractions(boolean disabled) {
		returnToRootButton.setDisable(disabled);
		graphImageMenuItem.setDisable(disabled);
		exportGraphMenuItem.setDisable(true);
	}

	// Disables options related to the current directory
	private void disableCurrentDirectoryOptions(boolean disabled) {
		closeCurrentMenuItem.setDisable(disabled);
		structureTabPane.setDisable(disabled);
		showChoiceBox.setDisable(disabled);
	}


	// Resets the zoom to default
	private void resetZoom() {
		graphGroup.setScaleX(1);
		graphGroup.setScaleY(1);
	}


	// Starts the loading animation
	private void startAnimation() {

		// We disable graph visual choices
		showChoiceBox.setDisable(true);
		structureTabPane.setDisable(true);
		settingsMenu.setDisable(true);

		// We clear the graph
		graphGroup.getChildren().clear();
		shownEntitiesLabel.setText("");

		// Then we start the loading and log animation
		loadingLabel.setVisible(true);
		loadingAnimation.start();
		logUpdateTimeline.play();

		// And also set the cursor as wait
		graphContainer.setCursor(Cursor.WAIT);
	}

	// Stops the loading animation
	private void stopAnimation() {

		// We enable graph visual choices
		showChoiceBox.setDisable(false);
		structureTabPane.setDisable(false);
		settingsMenu.setDisable(false);

		// We stop the loading animations
		loadingLabel.setVisible(false);
		loadingAnimation.stop();
		logUpdateTimeline.stop();

		// And also clear the log and set the cursor to default
		flushLog();
		graphContainer.setCursor(Cursor.DEFAULT);
	}


	// Starts a view transition to the given coordinates
	private static void moveToCoords(double x, double y) {
		if (isInTransition()) return;

		transition.setToX(x);
		transition.setToY(y);
		transition.play();
	}

	// Stars a view transition to the given Entity
	public static void moveToEntity(AnalysisEntity entity) {
		// We convert the Entity's coordinates to scene coordinates
		Point2D entityInScene = graphGroup.localToScene(entity.getX(), entity.getY());

		// And we determine the center point of the container in scene coordinates
		Point2D containerCenterInScene = staticGraphContainer.localToScene(2000.0 / 2.0, 565.0 / 2.0);

		// Then, we calculate the translation delta place the Entity in the center of the container
		double deltaX = containerCenterInScene.getX() - entityInScene.getX();
		double deltaY = containerCenterInScene.getY() - entityInScene.getY();

		// Finally, we can call the move to coords function
		moveToCoords(graphGroup.getTranslateX() + deltaX, graphGroup.getTranslateY() + deltaY);
	}

	// Clears the detauls container
	private void clearDetails() {
		detailsContainer.getChildren().clear();
		graphContainer.setPrefHeight(870);
		detailsContainer.setPrefHeight(0);
	}

}
