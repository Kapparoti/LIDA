package org.lida.Entity;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Class that contains the useful data for the analysis of an AnalysisEntity (both file or directory),
// and also the graph visualization data of the AnalysisEntity.
public class AnalysisEntity {

	// ---------- File variables ----------

	// File or directory name
	private String name;

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String getNameOnly() {
		return name.substring(0, name.indexOf("."));
	}

	// Flag to specify if the AnalysisEntity is a directory
	private boolean directory;

	public void setDirectory(boolean directory) {
		this.directory = directory;
		if (directory) {
			setLineCount(0);
			setStorageSize(0);
			setExtension("");
			setCode(false);
		}
	}

	public boolean isDirectory() {
		return directory;
	}

	private String path;

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

	// The number of lines in the AnalysisEntity. If directory, 0
	private int line_count;

	public void setLineCount(int line_count) {
		this.line_count = line_count;
	}

	public int getLineCount() {
		return line_count;
	}

	// The storage size of the AnalysisEntity
	private long storage_size;

	public void setStorageSize(long storage_size) {
		this.storage_size = storage_size;
	}

	public long getStorageSize() {
		return storage_size;
	}

	public String getStorageSizeString() {
		return storageSizeString(storage_size);
	}

	public static String storageSizeString(long sizeInBytes) {
		double size = sizeInBytes;
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		int unitIndex = 0;

		while (size >= 1024 && unitIndex < units.length - 1) {
			size /= 1024;
			unitIndex++;
		}

		return String.format("%.2f %s", size, units[unitIndex]);
	}

	// File extension. If directory, null
	private String extension;

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public String getExtension() {
		return extension;
	}

	// Returns the name of the file type
	private String fileType;

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public String getFileType() {
		return fileType;
	}

	// Flag to specify if the file has a programming language extension. If directory, false
	private boolean isCode = false;

	public void setCode(boolean code) {
		isCode = code;
	}

	public boolean isCode() {
		return isCode;
	}

	// ---------- Graphical variables ----------

	// Flag to specify if this AnalysisEntity is displayed by a node in the graph
	private boolean isDisplayed;

	public void setDisplayed(boolean displayed) {
		isDisplayed = displayed;
	}

	public boolean isDisplayed() {
		return isDisplayed;
	}

	// X coordinate
	private double x;

	public void setX(double x) {
		this.x = x;
	}

	public double getX() {
		return x;
	}

	// Y coordinate
	private double y;

	public void setY(double y) {
		this.y = y;
	}

	public double getY() {
		return y;
	}

	// The color of this AnalysisEntity. For files, it's the color associated with their file type,
	// for directories, is the mix of the colors from the elements inside them
	private String color = "#FFFFFF";

	public void setColor(String color) {
		if (color != null) this.color = color;
	}

	public String getColor() {
		return color;
	}

	// ---------- FileDependency variables ----------

	// Map containing the variables of this AnalysisEntity, compiled according to the language's rules
	private final List<Variable> variables = new ArrayList<>();

	public void addVariable(Variable variable) {
		variables.add(variable);
	}

	public List<Variable> getVariables() {
		return variables;
	}

	public Map<String, String> getVariablesMap() {
		return variables.stream().collect(Collectors.toMap(Variable::name, Variable::value));
	}

	// List of Identifiers that this AnalysisEntity has
	private final List<Identifier> identifiers = new ArrayList<>();

	public void addIdentifier(Identifier identifier) {
		identifiers.add(identifier);
	}

	public List<Identifier> getIdentifiers() {
		return identifiers;
	}

	public boolean hasIdentifier(Identifier identifier) {
		return identifiers.contains(identifier);
	}

	// List of FileDependency between this and other Entity on which this entity depends on
	private final List<FileDependency> dependencies = new ArrayList<>();

	public void addDependency(FileDependency dependency) {
		if (!dependencies.contains(dependency)) {
			dependencies.add(dependency);
			dependency.getEntity().addDependant(new FileDependency(this, dependency.getDependencies()));
		}
	}

	public void addDependencies(List<FileDependency> dependencies) {
		for (FileDependency dependency : dependencies) {
			addDependency(dependency);
		}
	}

	public List<FileDependency> getDependencies() {
		return dependencies;
	}

	public boolean hasDependencies() {
		return !dependencies.isEmpty();
	}


	// List of AnalysisEntities that depends on this Entity
	private final List<FileDependency> dependants = new ArrayList<>();

	public void addDependant(FileDependency dependant) {
		if (!dependants.contains(dependant)) {
			dependants.add(dependant);
		}
	}

	public List<FileDependency> getDependants() {
		return dependants;
	}

	public boolean hasDependants() {
		return !dependants.isEmpty();
	}

}
