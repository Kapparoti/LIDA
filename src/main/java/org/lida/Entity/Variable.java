package org.lida.Entity;

// Record containing one of the Variables of an Entity
public record Variable(String name, String value, boolean hidden) {
	/*
	The data inside the record are:
		name of the Variable,
		value of the Variable,
		hidden flag indicating whether it is hidden from the user
	 */
}