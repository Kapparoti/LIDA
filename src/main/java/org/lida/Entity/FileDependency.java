package org.lida.Entity;

import java.util.List;

// Class to represent the dependency between two files
public class FileDependency {

	// In all the examples, we will call the entity containing this example FileDependency "entity1"

	// The entity this FileDependency is directed to. So, entity1 depends on entity
	private AnalysisEntity entity;

	public AnalysisEntity getEntity() {
		return entity;
	}

	public void setEntity(AnalysisEntity entity) {
		this.entity = entity;
	}


	// List of Dependencies that entity1 has on entity
	private final List<Dependency> dependencies;

	public final List<Dependency> getDependencies() {
		return dependencies;
	}

	public void addUniqueDependency(Dependency dependency) {
		for (Dependency oldDependency : dependencies) {
			if (oldDependency.equals(dependency)) {
				return;
			}
		}
		dependencies.add(dependency);
	}

	public void addUniqueRuleDependency(Dependency dependency) {
		for (Dependency oldDependency : dependencies) {
			if (oldDependency.ruleName().equals(dependency.ruleName())) {
				return;
			}
		}
		dependencies.add(dependency);
	}

	// --------------------- Constructor ---------------------

	public FileDependency(AnalysisEntity entity, List<Dependency> dependencies) {
		this.entity = entity;
		this.dependencies = dependencies;
	}

}
