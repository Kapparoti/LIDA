package org.lida.Settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Objects;


// Singleton class for storing, getting and modifying the application settings
public class SettingsHandler {

	// --------------------- Class variables ---------------------

	// Variables to store the settings files. They are de-facto finals
	private static File DEFAULT_SETTINGS_FILE;
	private static File SETTINGS_FILE;

	// Variable containing user settings, to avoid reading them every time
	private static SettingsData settings;

	// --------------------- Singleton setup ---------------------

	private static SettingsHandler uniqueInstance;

	private static void checkForInstance() {
		if (uniqueInstance == null) {
			uniqueInstance = new SettingsHandler();
		}
	}

	// --------------------- Constructor ---------------------

	private SettingsHandler() {

		// When the class is instantiated, we fill the settings
		try {
			DEFAULT_SETTINGS_FILE = new File(Objects.requireNonNull(SettingsHandler.class.getResource("default_settings.json")).toURI());
			File settingsFile = new File(DEFAULT_SETTINGS_FILE.getParentFile().getAbsolutePath() + File.separator + "settings.json");

			if (settingsFile.createNewFile()) {
				SETTINGS_FILE = settingsFile;

				// If createNewFile() returns true, the file containing the user settings wasn't present. So, we restore it to default
				restoreToDefault();
			} else {

				// If createNewFile() failed, the settings file is already there, so we load it inside it's variable
				SETTINGS_FILE = new File(Objects.requireNonNull(getClass().getResource("settings.json")).toURI());
			}

			// We now have SETTINGS_FILE ready to go, so we load its values inside the settings variable
			ObjectMapper mapper = new ObjectMapper();
			settings = mapper.readValue(SETTINGS_FILE, SettingsData.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// --------------------- Helper functions ---------------------

	// Write settings stored in the settings variable on the user settings file
	private static void writeSettings() {
		// We always check for the singleton instance
		checkForInstance();

		// We write the values inside the settings variable on the user settings file
		ObjectMapper mapper = new ObjectMapper();
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(SETTINGS_FILE, settings);
		} catch (IOException _) {
		}
	}

	// --------------------- Public functions ---------------------

	// Function to restore settings by their default values
	public static void restoreToDefault() {
		try {
			// To restore the settings to default, we read them from the default settings file
			ObjectMapper mapper = new ObjectMapper();
			settings = mapper.readValue(DEFAULT_SETTINGS_FILE, SettingsData.class);
			writeSettings();
		} catch (Exception _) {
		}
	}


	// Setting used for showing files without extensions
	public static boolean getFilesNoExt() {
		checkForInstance();
		return settings.getFilesNoExt();
	}

	public static void toggleFilesNoExt() {
		checkForInstance();
		settings.setFilesNoExt(!settings.getFilesNoExt());
		writeSettings();
	}


	// Setting used for showing hidden directories (starting with '.')
	public static boolean getHiddenDirectories() {
		checkForInstance();
		return settings.getHiddenDirectories();
	}

	public static void toggleHiddenDirectories() {
		checkForInstance();
		settings.setHiddenDirectories(!settings.getHiddenDirectories());
		writeSettings();
	}


	// Setting used for showing entities without connections
	public static boolean getSingleEntities() {
		checkForInstance();
		return settings.getSingleEntities();
	}

	public static void toggleSingleEntities() {
		checkForInstance();
		settings.setSingleEntities(!settings.getSingleEntities());
		writeSettings();
	}


	// Setting for the layout used for the file graph
	public static String getFileGraphLayout() {
		checkForInstance();
		return settings.getFileGraphLayout();
	}

	public static void setFileGraphLayout(String layout) {
		checkForInstance();
		settings.setFileGraphLayout(layout);
		writeSettings();
	}


	// Setting for the layout used for the dependency graph
	public static String getDependencyGraphLayout() {
		checkForInstance();
		return settings.getDependencyGraphLayout();
	}

	public static void setDependencyGraphLayout(String layout) {
		checkForInstance();
		settings.setDependencyGraphLayout(layout);
		writeSettings();
	}
}
