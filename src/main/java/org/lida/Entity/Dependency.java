package org.lida.Entity;

// Record containing one of the Dependencies between two Entities
public record Dependency(Identifier identifier, String ruleName, boolean hidden) {
	/*
	The data inside the record are:
		Identifier found by the rule,
		name of the rule that found this Dependency,
		hidden flag indicating whether it is hidden from the user
	 */
}
