package org.lida.Entity;

// Record containing one of the Identifiers of an Entity
public record Identifier(String name, String ruleName, int key, boolean hidden) {
	/*
	The data inside the record are:
		name of the Identifier,
		name of the rule that found it,
		key used in case of Identifiers with the same name,
		hidden flag indicating whether it is hidden from the user
	 */
}
