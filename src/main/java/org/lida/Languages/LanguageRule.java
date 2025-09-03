package org.lida.Languages;


import java.util.List;


// Record storing a rule of a programming language
public record LanguageRule(String name, CodeReader.RuleTypes type, String pattern, int totalNumber, List<String> conditions, String constantValue, boolean hidden, boolean debug) {
	/*
	The data inside the record are:
		name of the rule,
		type of the rule (Variable, Identifier or Dependency),
		pattern of the rule (null if there are none),
		total number of times the rule can be applied inside a file,
		list of conditions that this rule needs to satisfy (null if there are none),
		constant value of the rule (null if the rule is not constant),
		hidden flag specifying if this rule should be displayed to the user (true by default),
		debug flag enabling the console log for this rule (false by default)
	 */
}
