package org.lida.Functionality;

import com.mxgraph.view.mxGraph;
import javafx.scene.control.TreeItem;
import org.lida.Entity.AnalysisEntity;
import org.lida.Entity.FileDependency;
import org.lida.Interface.LIDAController;
import org.lida.Settings.SettingsHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


// Class responsible for creating and exporting graphs
public class GraphCreator {

	//Sizes of the vertex created in the mxGraph
	private static final float VERTEXWIDTH = 110;
	private static final float VERTEXHEIGHT = 40;

	// --------------------- Graph exporting ---------------------

	// Supported export formats.
	public enum graphFormats {json, xml, plantuml}

	// Public function to export the given graph to a file of the specified format
	public static void exportGraph(mxGraph graph, String path, graphFormats format) {
	}

	// --------------------- File graph creation ---------------------

	// Public function to fill the mxGraph with the directory's subtree contents, according to the user visualization settings.
	public static void fillFileGraph(TreeItem<AnalysisEntity> root, mxGraph graph, Object parent, Map<AnalysisEntity, Object> entityToVertex) throws ExecutionException, InterruptedException {
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			// We begin to update the graph and call the recursive function starting at the tree's root
			graph.getModel().beginUpdate();
			insertFileVertices(root, graph, parent, entityToVertex, null, executor);
		} finally {
			// We wait for all tasks to complete and end the graph update
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.HOURS);
			graph.getModel().endUpdate();
		}
	}

	// Helper recursive function that, if it respects the requirements, inserts the AnalysisEntity node in the mxGraph
	private static Object insertFileVertices(TreeItem<AnalysisEntity> node, mxGraph graph, Object parent, Map<AnalysisEntity, Object> entityToVertex, Object parentVertex, ExecutorService executor) throws ExecutionException, InterruptedException {
		AnalysisEntity entity = node.getValue();

		// Directory and files are handled differently:
		if (entity.isDirectory()) {

			// According to the settings, we ignore hidden directories or not
			if (entity.getName().startsWith(".") && !SettingsHandler.getHiddenDirectories()) {

				// Setting the displayed flag to false is essential to draw the graph later
				entity.setDisplayed(false);
				return null;
			}

			// We create a vertex for the directory and add it to the mapping, since the graph does not permit finding a vertex given its Entity
			Object currentVertex;
			synchronized (graph) {
				currentVertex = graph.insertVertex(parent, null, entity, 0, 0, VERTEXWIDTH, VERTEXHEIGHT);

				// In a tree structure we know that every vertex has only one inbound edge (from its parent), so we add it
				graph.insertEdge(parent, null, "", parentVertex, currentVertex);
			}
			entityToVertex.put(entity, currentVertex);

			// We call the function recursively on all children
			List<Future<Object>> futures = new ArrayList<>();
			for (TreeItem<AnalysisEntity> child : node.getChildren()) {
				futures.add(executor.submit(() -> insertFileVertices(child, graph, parent, entityToVertex, currentVertex, executor)));
			}

			// Then we collect the results and keep only visible children
			List<Object> visibleChildren = new ArrayList<>();
			for (Future<Object> future : futures) {
				Object child = future.get();
				if (child != null) {
					visibleChildren.add(child);
				}
			}

			// According to the settings, we show or not directories without visible children
			int count = visibleChildren.size();
			if (SettingsHandler.getSingleEntities() || count > 0) {

				// Every directory's color is the blend of its children's colors
				int redSum = 0, greenSum = 0, blueSum = 0;
				for (TreeItem<AnalysisEntity> childResult : node.getChildren()) {
					AnalysisEntity childEntity = childResult.getValue();

					// We ignore not displayed childrens
					if (childEntity.isDisplayed()) {
						String hexColor = childEntity.getColor();

						if (hexColor.startsWith("#")) {
							hexColor = hexColor.substring(1);
						}

						// We extract the integer values of red, green and blue from the hexadecimal string
						int red = Integer.parseInt(hexColor.substring(0, 2), 16);
						int green = Integer.parseInt(hexColor.substring(2, 4), 16);
						int blue = Integer.parseInt(hexColor.substring(4, 6), 16);

						redSum += red;
						greenSum += green;
						blueSum += blue;
					}
				}

				// We check if the directory has children because for the settings count can be 0, so we avoid the division by zero
				if (count > 0) {
					// We assign the average RGB values
					entity.setColor(String.format("#%02X%02X%02X", redSum / count, greenSum / count, blueSum / count));
				}
			} else {
				// If we don't show this directory, we must remove its vertex added earlier
				synchronized (graph) {

					// We must first add the cell and then in some cases remove it because, to call the function recursively, we need the parent node
					graph.removeCells(new Object[]{currentVertex});
				}
				entity.setDisplayed(false);
				return null;
			}

			// For the directory node, no other actions are needed
			entity.setDisplayed(true);
			return currentVertex;
		} else {
			// We check the user settings to show file Entities
			if (!LIDAController.shouldDisplayEntity(entity) || (entity.getExtension().isEmpty() && !SettingsHandler.getFilesNoExt())) {
				entity.setDisplayed(false);
				return null;
			}

			// We create a vertex for the file and add it to the mapping, since the graph does not permit finding a vertex given its Entity
			Object currentVertex;
			synchronized (graph) {
				currentVertex = graph.insertVertex(parent, null, entity, 0, 0, 80, 30);

				// In a tree structure we know that every vertex has only one inbound edge (from its parent), so we add it
				graph.insertEdge(parent, null, "", parentVertex, currentVertex);
			}
			entityToVertex.put(entity, currentVertex);

			// For the file node, no other actions are needed
			entity.setDisplayed(true);
			return currentVertex;
		}
	}

	// --------------------- Dependency graph creation ---------------------

	// Public function to fill the mxGraph with the code Entities and theirs dependencies
	public static void fillDependencyGraph(TreeItem<AnalysisEntity> root, mxGraph graph, Object parent, Map<AnalysisEntity, Object> entityToVertex) throws ExecutionException, InterruptedException {
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			// We begin to update the graph and call the recursive function starting at the tree's root, that will fill the vertices with code Entities
			graph.getModel().beginUpdate();
			addDependencyVertices(root, graph, parent, entityToVertex, executor);

			// For each code Entity we added to the graph, we create an edge for each of its Dependencies

			// For each code Entity we added to the graph:
			for (AnalysisEntity entity : entityToVertex.keySet()) {
				executor.submit(() -> {

					// For each of its Dependencies:
					for (FileDependency dependency : entity.getDependencies()) {

						// We create an edge representing its Dependency
						synchronized (graph) {
							graph.insertEdge(parent, null, "", entityToVertex.get(entity), entityToVertex.get(dependency.getEntity()));
						}
					}
				});
			}
		} finally {
			// We wait for all tasks to complete and end the graph update
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.HOURS);
			graph.getModel().endUpdate();
		}
	}

	// Helper recursive function that, if it respects the requirements, inserts the code Entity node in the mx graph
	private static Void addDependencyVertices(TreeItem<AnalysisEntity> node, mxGraph graph, Object parent, Map<AnalysisEntity, Object> entityToVertex, ExecutorService executor) throws ExecutionException, InterruptedException {
		AnalysisEntity entity = node.getValue();

		// We need to show Entities that are useful to the graph (so that have Dependencies or Dependants) and respect the user's visualization settings
		if (LIDAController.shouldDisplayEntity(entity) && (SettingsHandler.getSingleEntities() || entity.hasDependencies() || entity.hasDependants())) {

			// We create a vertex for the Entity and add it to the mapping, since the graph does not permit finding a vertex given its Entity
			Object vertex;
			synchronized (graph) {
				vertex = graph.insertVertex(parent, null, entity, 0, 0, VERTEXWIDTH, VERTEXHEIGHT);
			}
			entityToVertex.put(entity, vertex);
			entity.setDisplayed(true);
		} else {

			// Setting the displayed flag to false is important for the drawing of the graph later
			entity.setDisplayed(false);
		}

		// We call the function recursively on all children
		List<Future<Void>> futures = new ArrayList<>();
		for (TreeItem<AnalysisEntity> child : node.getChildren()) {
			futures.add(executor.submit(() -> addDependencyVertices(child, graph, parent, entityToVertex, executor)));
		}

		// Then we wait for the tasks to finish
		for (Future<Void> future : futures) {
			future.get();
		}

		return null;
	}

}
