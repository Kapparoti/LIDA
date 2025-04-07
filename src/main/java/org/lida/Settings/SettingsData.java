package org.lida.Settings;


// Class to store the data inside the settings file
public class SettingsData {

	// Flag to show files without extensions
	private boolean filesNoExt;

	public boolean getFilesNoExt() {
		return filesNoExt;
	}

	public void setFilesNoExt(boolean filesNoExt) {
		this.filesNoExt = filesNoExt;
	}


	// Flag to show hidden directories
	private boolean hiddenDirectories;

	public boolean getHiddenDirectories() {
		return hiddenDirectories;
	}

	public void setHiddenDirectories(boolean hiddenDirectories) {
		this.hiddenDirectories = hiddenDirectories;
	}


	// Flag to show entities that don't have any connections
	private boolean singleEntities;

	public boolean getSingleEntities() {
		return singleEntities;
	}

	public void setSingleEntities(boolean singleEntities) {
		this.singleEntities = singleEntities;
	}


	// String containing the name of the layout used for the file graph. It is a name of LIDAController.GraphLayouts
	private String fileGraphLayout;

	public String getFileGraphLayout() {
		return fileGraphLayout;
	}

	public void setFileGraphLayout(String fileGraphLayout) {
		this.fileGraphLayout = fileGraphLayout;
	}


	// String containing the name of the layout used for the dependency graph. It is a name of LIDAController.GraphLayouts
	private String dependencyGraphLayout;

	public String getDependencyGraphLayout() {
		return dependencyGraphLayout;
	}

	public void setDependencyGraphLayout(String dependencyGraphLayout) {
		this.dependencyGraphLayout = dependencyGraphLayout;
	}
}
