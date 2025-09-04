package org.lida.Functionality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.concurrent.Task;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import org.lida.Entity.AnalysisEntity;
import org.lida.Entity.Identifier;
import org.lida.Languages.CodeReader;


// Class responsible for the handling of the directory subtree analysis
public class DirectoryAnalyzer {

	// --------------------- Tree Management ---------------------

	// VirtualThreadPerTaskExecutor for the analysis. Stored to stop it in case of analysis cancellation
	private ExecutorService analysisExecutor = null;

	// The root of the AnalysisEntity tree, corresponding to the selected directory
	private static TreeItem<AnalysisEntity> analysisTreeRoot = null;

	// Public getter function for the root of the AnalysisEntity tree, corresponding to the selected directory
	public TreeItem<AnalysisEntity> getAnalysisTreeRoot() {
		return analysisTreeRoot;
	}

	// Public function to return the root AnalysisEntity, corresponding to the selected directory's Entity
	public AnalysisEntity getRootEntity() {
		if (analysisTreeRoot == null) return null;
		return analysisTreeRoot.getValue();
	}

	// Public function to retrieve the TreeItem from its Entity value
	public TreeItem<AnalysisEntity> getTreeItemFromEntity(AnalysisEntity entity) {
		return getTreeItemFromEntityRec(entity, getAnalysisTreeRoot());
	}

	// Helper recursive function to search the tree and find the TreeItem with the Entity value
	private TreeItem<AnalysisEntity> getTreeItemFromEntityRec(AnalysisEntity entity, TreeItem<AnalysisEntity> node) {

		// Checking the current node
		if (node.getValue().equals(entity)) {

			// If found, return it
			return node;
		} else {

			// If not found, recursion on its children
			for (TreeItem<AnalysisEntity> child : node.getChildren()) {
				TreeItem<AnalysisEntity> result = getTreeItemFromEntityRec(entity, child);
				if (result != null) {
					return result;
				}
			}
			return null;
		}
	}

	// Public recursive function to count displayed Entities inside the node subtree
	public int getDisplayedEntitiesNumber(TreeItem<AnalysisEntity> node) {

		// If displayed, we add one to the count
		int count = 0;
		if (node.getValue().isDisplayed()) count++;

		// We then add the children's total counts
		for (TreeItem<AnalysisEntity> child : node.getChildren()) {
			count += getDisplayedEntitiesNumber(child);
		}

		return count;
	}

	// --------------------- Data structures ---------------------

	// List of extensions from programming languages. Used for fast compiling Entity isCode flag
	private static final ArrayList<String> codeExtensions = new ArrayList<>();

	// Mapping of every extension to its file type. Used for fast compiling Entity file type value
	private static final Map<String, String> extensionToFileType = new HashMap<>();

	// Mapping of every extension to its file type. Used for fast compiling Entity color value
	private final Map<String, String> extensionToColor = new HashMap<>();

	// Mapping of every Identifier to its AnalysisEntity. Used by CodeReader to get an Identifier's Entity after finding it in a code file
	public static final Map<Identifier, AnalysisEntity> identifierToEntity = new ConcurrentHashMap<>();

	// Mapping of every Identifier name to its Identifier. Used by CodeReader to get an Identifier by its name when searching for dependencies
	public static final Map<String, List<Identifier>> nameToIdentifiers = new ConcurrentHashMap<>();

	// --------------------- Analysis log ---------------------

	// String list used as a log for Analysis messages. It's read and displayed by LIDAController
	private final List<String> analysisLog = new ArrayList<>();

	// Maximum number of log entries
	private static final int LOG_THRESHOLD = 10;

	// Appends a new entry to the log
	private void addToLog(String message) {
		synchronized (analysisLog) {

			// The new entry is added at the beginning
			analysisLog.addFirst(message + "\n");

			// If the log exceeds the threshold after our addition, we remove the oldest entry
			if (analysisLog.size() > LOG_THRESHOLD) analysisLog.removeLast();
		}
	}

	// Public getter function for the log
	public String getLogString() {
		synchronized (analysisLog) {

			// We add every log entry in the StringBuilder and return the result string
			StringBuilder result = new StringBuilder();
			for (String line : analysisLog) result.append(line);
			return result.toString();
		}
	}

	// --------------------- Constructor ---------------------

	public DirectoryAnalyzer() {
		// We load the file "file_types.json" containing all the data for each file type
		File langsFile;
		try {
			langsFile = new File(Objects.requireNonNull(getClass().getResource("file_types.json")).toURI());
		} catch (Exception e) {

			// The langs file is requested by the program to function, so if there's a problem with it, we throw an exception
			throw new RuntimeException(e);
		}

		// We need to fill the private list and mappings with the values from the langs file
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, FileTypeData> langData;
		try {
			// We first fill the langData map with the ObjectMapper
			langData = objectMapper.readValue(langsFile, new TypeReference<>() {
			});

			// Then we iterate over each file type
			for (Map.Entry<String, FileTypeData> entry : langData.entrySet()) {
				FileTypeData fileTypeData = entry.getValue();
				String languageName = entry.getKey();

				if (fileTypeData.code()) {

					// For code files, add extension info to all maps
					for (String ext : fileTypeData.extensions()) {
						codeExtensions.add(ext);
						extensionToFileType.put(ext, languageName);
						extensionToColor.put(ext, fileTypeData.color());
					}
				} else {

					// For non-code files, add to file type and color maps only
					for (String ext : fileTypeData.extensions()) {
						extensionToFileType.put(ext, languageName);
						extensionToColor.put(ext, fileTypeData.color());
					}
				}
			}
		} catch (Exception e) {

			// The langs file is requested by the program to function, so if there's a problem with it, we throw an exception
			throw new RuntimeException(e);
		}
	}

	// --------------------- Helper functions ---------------------

	// Helper function to count the number of lines inside a file
	private static int countLinesInFile(File file) {

		// With a scanner, we iterate for each line of the file
		int lines = 0;
		try (Scanner scanner = new Scanner(file)) {
			while (scanner.hasNextLine()) {
				scanner.nextLine();
				lines++;
			}
		} catch (Exception _) {
		}

		return lines;
	}

	// Helper function to get the extension of the file
	private static String getFileExtension(File file) {
		String name = file.getName();

		// We find the position of the last dot
		int dotIndex = name.lastIndexOf('.');

		// If we didn't find any dots, the file's extension is empty, or else, it's the string following the dot
		return (dotIndex < 1) ? "" : name.substring(dotIndex + 1);
	}

	// --------------------- Public functions ---------------------

	// Clears the current analysis
	public void clear() {

		// If the analysisExecutor is running, we stop it
		if (analysisExecutor != null && !analysisExecutor.isShutdown()) analysisExecutor.shutdownNow();

		// We also clear data that changes between different analysis tasks
		identifierToEntity.clear();
		nameToIdentifiers.clear();
		analysisTreeRoot = null;
	}

	// Starts a new analysis, cancelling any running ones
	public Task<Void> startAnalysis(File directory) {
		// Clears the state for a new analysis
		clear();
		analysisExecutor = Executors.newVirtualThreadPerTaskExecutor();

		// We prepare to store the code Entities to then link only them
		List<AnalysisEntity> codeEntities = new ArrayList<>();

		// Task to scan the directory subtree
		Task<Void> scanTask = new Task<>() {
			@Override
			protected Void call() {
				try {
					// Start scanning and build the analysis tree.
					analysisTreeRoot = scan(directory, codeEntities, analysisExecutor);
				} catch (Exception e) {
					System.err.println("Cancelling scan task! " + e.getMessage());
					cancel();
				}
				return null;
			}
		};

		// Task to link code Entities creating dependencies between them
		Task<Void> linkTask = new Task<>() {
			@Override
			protected Void call() {
				try {
					link(codeEntities, analysisExecutor);
				} catch (Exception e) {
					System.err.println("Cancelling link task! " + e.getMessage());
					cancel();
				}
				return null;
			}
		};

		// When the scanning task succeeds, we start the linking task
		scanTask.setOnSucceeded(_ -> analysisExecutor.submit(linkTask));
		analysisExecutor.submit(scanTask);

		// We return the link task to the LIDAController
		return linkTask;
	}

	// --------------------- Scan functions ---------------------

	// Recursive function that scans the file subtree to create a corresponding AnalysisEntity tree
	private TreeItem<AnalysisEntity> scan(File file, List<AnalysisEntity> codeEntities, ExecutorService executor) throws InterruptedException, ExecutionException {
		// Check if the executor has been shut to cancel the analysis
		if (executor.isShutdown()) {
			throw new InterruptedException("Interrupted analysis");
		}

		// We create an AnalysisEntity for the current file or directory with some common values for both
		AnalysisEntity entity = new AnalysisEntity();
		String name = file.getName();
		entity.setName(name);
		entity.setPath(file.getAbsolutePath());

		// We then create the entity's tree node
		TreeItem<AnalysisEntity> analysisNode = new TreeItem<>(entity);

		if (!file.isDirectory()) {

			// If the file is not a directory, then other properties must be compiled:
			entity.setDirectory(false);

			// We start with its extension, to then add the scanning entry to the analysis log
			String extension = getFileExtension(file);
			entity.setExtension(extension);
			addToLog("Scanning " + name + '.' + extension);

			// Then we can read from the file its line count and storage size
			entity.setLineCount(countLinesInFile(file));
			entity.setStorageSize(file.length());

			// Finally, we use the previously compiled maps to get the file type and color of the file
			entity.setFileType(extensionToFileType.get(extension));
			entity.setColor(extensionToColor.get(extension));

			// The codeExtensions list is used to determine if this file contains a programming language
			boolean isCode = codeExtensions.contains(extension);
			entity.setCode(isCode);
			if (isCode) {
				codeEntities.add(entity);

				// If it's a code Entity, we read it to compile its Variables and Identifiers
				addToLog("Reading " + name + '.' + extension);
				readFile(entity);
			}
		} else {
			// If the file is a directory, we set the curresponding flag
			entity.setDirectory(true);

			// We then process its children recursively
			File[] fileChildren = file.listFiles();
			if (fileChildren != null) {
				List<Future<TreeItem<AnalysisEntity>>> futures = new ArrayList<>();

				// We submit to the executor the recursive call for each child
				for (File childFile : fileChildren) {
					futures.add(executor.submit(() -> scan(childFile, codeEntities, executor)));
				}

				// After waiting for each child scan, we add it to the current node's children
				for (Future<TreeItem<AnalysisEntity>> future : futures) {
					analysisNode.getChildren().add(future.get());
				}
			}
		}

		return analysisNode;
	}

	// Helper function that reads code files to compile their Variables and Identifiers
	private static void readFile(AnalysisEntity entity) {
		// First, we fill the entity's Variables, that will be used both in the Identifier reading and in the Dependency find
		CodeReader.fillVariables(entity);

		// We read the Entity's Identifiers from its code, and we iterate on each of them
		for (Identifier identifier : CodeReader.readIdentifiers(entity)) {
			Identifier newIdentifier = identifier;

			// We need to find the key value for the new Identifier:
			final int maxKey = identifierToEntity.keySet().stream()

					// We find Identifiers with the same name and different entity
					.filter(analysisEntity -> analysisEntity.name().equals(identifier.name()))

					// We then get all the keys of the Identifiers with the same name
					.map(Identifier::key)

					// Between those keys, we need to take the biggest one
					.max(Integer::compare).orElse(0);

			// So, if the new key is not 0, that means that other Identifiers have the same name of the new Identifier,
			// and so we must change it's key to be higher than any of them, thus making it different
			if (maxKey != 0) {
				newIdentifier = new Identifier(identifier.name(), identifier.ruleName(), maxKey + 1, identifier.hidden());
			}

			// We can finally add the new Identifier to its Entity and to the maps
			entity.addIdentifier(newIdentifier);
			identifierToEntity.put(newIdentifier, entity);
			nameToIdentifiers.computeIfAbsent(newIdentifier.name(), k -> new ArrayList<>()).add(newIdentifier);
		}
	}

	// --------------------- Link functions ---------------------

	// Link function that iterates on all code Entities to create Dependencies between them
	private void link(List<AnalysisEntity> codeEntities, ExecutorService executor) throws ExecutionException, InterruptedException {
		List<Future<?>> futures = new ArrayList<>();

		// For each code Entity we submit a link Entity task
		for (AnalysisEntity entity : codeEntities) {
			futures.add(executor.submit(() -> linkEntity(entity)));
		}

		// We wait for all linking tasks to finish
		for (Future<?> future : futures) {
			future.get();
		}

		executor.shutdown();
		// We also clear the Identifiers to entities mapping as it is no longer necessary
		identifierToEntity.clear();
		nameToIdentifiers.clear();
	}

	// Helper function to link a single Entity
	private void linkEntity(AnalysisEntity entity) {

		// We add the linking entry to the analysis log and add the Dependencies to the Entity
		addToLog("Linking " + entity.getName() + '.' + entity.getExtension());
		entity.addDependencies(CodeReader.findDependencies(entity, entity.getFileType()));
	}
}
