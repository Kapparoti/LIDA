package org.lida.Languages;


import java.util.List;

// Record containing one of the code text rules for a programming language
public record CodeTextRule(String start, String end, List<String> endExceptions) {
	/*
	The data inside the record are:
		 the characters that identifies its start,
		 the characters that identifies its end,
		 the characters exceptions to the end
	 */
}
