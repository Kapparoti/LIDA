package org.lida.Functionality;

import java.util.ArrayList;

// Class to represent file type data inside the file_types.json file, storing:
public record FileTypeData(String name, ArrayList<String> extensions, String color, Boolean code) {
	/*
	The data inside the record are:
		name of the file type,
		extensions that the file type is recognized with,
		color associated with the file type,
		code flag to know if the file type is used by a programming language
	 */
}