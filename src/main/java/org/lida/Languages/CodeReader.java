package org.lida.Languages;

import org.lida.Entity.*;
import org.lida.Functionality.DirectoryAnalyzer;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;


// Class responsible for reading code files and filling their Variables, Identifiers and Dependencies
public class CodeReader {

	// --------------------- Language rules handling ---------------------

	// Map to store the rules for each language, loading them from the respective rules file
	private static final Map<String, LanguageRules> languageToRules = new HashMap<>();

	// Different types of language rules
	public enum RuleTypes {variable, identifier, dependency}

	// Retrieves the LanguageRules for a given language. If the rules aren't already loaded in the map, it loads them from the curresponding rules file
	private static LanguageRules getLanguageRules(String language) {
		synchronized (languageToRules) {

			// If the language rules are already loaded inside the map, we can just retrieve them
			if (languageToRules.containsKey(language)) return languageToRules.get(language);

			// We must load the language rules and load them inside the map:
			LanguageRules languageRules = new LanguageRules();
			try {

				// We try to find the curresponding rules file
				File rulesFile = new File(Objects.requireNonNull(CodeReader.class.getResource(language)).toURI());
				BufferedReader br = new BufferedReader(new FileReader(rulesFile));
				try {

					// For each rule, we prepare the possible params values with their default values
					String ruleName = null;
					String rulePattern = null;
					String constantValue = null;
					List<String> ruleConditions = null;
					int totalNumber = -1;
					boolean hidden = false;
					boolean isDebug = false;

					// We precompile the patterns used to read the rules inside the file
					Pattern ruleNamePattern = Pattern.compile("^(.*?)\\s*\\{");
					Pattern patternPattern = Pattern.compile("^pattern:\\s*\"([^\"]+)\";?");
					Pattern valuePattern = Pattern.compile("[\\t ]*value:\\s*(.*?)(?:;|$)");
					Pattern conditionPattern = Pattern.compile("^[\\s+]*condition:\\s*(.*)");
					Pattern totalPattern = Pattern.compile("^total_number:\\s*(-?\\d+)(?:;|$)");
					Pattern hiddenPattern = Pattern.compile("\\bhidden\\b(?:;|$)");
					Pattern debugPattern = Pattern.compile("\\bDEBUG\\b(?:;|$)");

					// We read the file line by line, remembering the current rules section
					String line;
					RuleTypes currentSection = null;

					while ((line = br.readLine()) != null) {
						// We ignore empty or comment lines
						if ((line = line.trim()).isEmpty() || line.startsWith("#")) continue;

						// First, we encounter the comments. They will be formatted like this: "Comments = [[//, ], [/*, */]]"
						if (line.startsWith("Comments")) {

							// We remove the external brackets to get a string like "//, ], [/*, */"
							line = line.substring(line.indexOf("[[") + 2, line.length() - 2);

							// Then, we split the different comment rules separated by "], ["
							for (String values : line.split("],\\s*\\[")) {

								// Each pair will be in the form of "//, " or "/*, */"
								String[] tokens = values.split("\\s*,\\s*");

								// If this is a single-line comment rule, the second value will be empty
								languageRules.addComment(tokens[0].trim(), (tokens.length > 1) ? tokens[1].trim() : "");
							}

							continue;
						}

						// Then, we encounter the text. They will be formatted like this: "Text = [[", \", /", "], [', \', /', ']]"
						if ((line = line.trim()).startsWith("Text")) {

							// We remove the external brackets to get a string like "", \", /", "], [', \', /', '"
							line = line.substring(line.indexOf("[[") + 2, line.length() - 2);

							// Then, we split the different text rules separated by "], ["
							for (String values : line.split("],\\s*\\[")) {

								// Each text rule will be in the form of "", \", /", "" or "', \', /', '"
								String[] tokens = values.split(",\\s*");

								// The first part is the start of the text, the last one will be the end, and any between are end exceptions
								languageRules.addCodeText(tokens[0], tokens[tokens.length - 1], new ArrayList<>(Arrays.asList(tokens).subList(1, tokens.length - 1)));
							}

							continue;
						}

						// We read the start of a new rule section
						if (line.startsWith("Variables")) {
							currentSection = RuleTypes.variable;
							continue;
						}
						if (line.startsWith("Dependencies")) {
							currentSection = RuleTypes.dependency;
							continue;
						}
						if (line.startsWith("Identifiers")) {
							currentSection = RuleTypes.identifier;
							continue;
						}

						// We then process the name line
						Matcher ruleNameMatcher = ruleNamePattern.matcher(line);
						if (ruleNameMatcher.find() && ruleName == null) {

							// We store the name of the rule
							ruleName = ruleNameMatcher.group(1);

							// And reset the other parameters to default, as this is a start of a new rule
							rulePattern = null;
							constantValue = null;
							ruleConditions = null;
							totalNumber = -1;
							hidden = false;
							isDebug = false;
							continue;
						}

						// We then process the pattern line
						Matcher patternMatcher = patternPattern.matcher(line);
						if (patternMatcher.find()) {

							// We store the pattern inside the quotation marks
							rulePattern = line.substring(line.indexOf("\"") + 1, line.length() - 1);

							// We then check if the pattern is valid
							try {
								Pattern.compile(rulePattern);
								continue;
							} catch (PatternSyntaxException e) {

								// If not, we show an error and throw an exception to stop the reading of this current rule file
								System.err.println("Invalid rule pattern: " + rulePattern + ": " + e.getMessage());
								throw new RuntimeException("Invalid rule pattern: " + rulePattern, e);
							}
						}

						// We then process the constant value line
						Matcher valueMatcher = valuePattern.matcher(line);
						if (valueMatcher.find()) {

							// We store the constant value
							constantValue = valueMatcher.group(1);

							// If a Variable or rule is a constant, they can be applied only once, because their value won't change
							totalNumber = 1;
							continue;
						}

						// We then process the condition lines
						Matcher conditionMatcher = conditionPattern.matcher(line);
						if (conditionMatcher.find()) {

							// If no conditions have been found yet, we initialize the list
							if (ruleConditions == null) ruleConditions = new ArrayList<>();

							// Then, we can add the current condition
							ruleConditions.add(conditionMatcher.group(1));
							continue;
						}

						// We then process the total number line
						Matcher totalMatcher = totalPattern.matcher(line);
						if (totalMatcher.find()) {

							// We store the total number
							totalNumber = Integer.parseInt(totalMatcher.group(1));
							continue;
						}

						// We then process the hidden flag line
						Matcher hiddenMatcher = hiddenPattern.matcher(line);
						if (hiddenMatcher.find()) {

							// We store the hidden flag
							hidden = true;
							continue;
						}

						// We then process the debug flag line
						Matcher debugMatcher = debugPattern.matcher(line);
						if (debugMatcher.find()) {

							// We store the debug flag
							isDebug = true;
							continue;
						}

						// Finally, the '}' indicates the end of the rule declaration
						if (line.startsWith("}")) {

							// For the rule to be valid, it must have a name, and then a pattern or a value
							if (ruleName != null && (rulePattern != null || constantValue != null))
								languageRules.addRule(new LanguageRule(ruleName, currentSection, rulePattern, totalNumber, ruleConditions, constantValue, hidden, isDebug));

							// We then reset all parameters to default
							ruleName = null;
							rulePattern = null;
							constantValue = null;
							ruleConditions = null;
							totalNumber = -1;
							hidden = false;
							isDebug = false;
						}
					}

					// If we finished iterating without errors, we can safely say that the rules are valid and can be put in the mapping
					System.out.println("Loaded " + languageRules.getCommendsCount() + " comments, " + languageRules.getCodeTexts().size() + " texts and " + languageRules.getRules().size() + " rules for " + language);
					languageToRules.put(language, languageRules);
				} catch (Exception e) {

					// We failed to compile the rules from the file, so we notify it and put empty rules in the map to avoid trying to compile the same file again
					System.err.println("Error while loading " + (languageRules.getRules().size() + 1) + " rules for " + language + ": " + e.getMessage());
					languageToRules.put(language, new LanguageRules());
				}
			} catch (Exception e) {

				// We failed to find the curresponding rules file, so we just notify it and put empty rules in the map to avoid searching for the same file again
				System.err.println("Didn't find " + language + " rule file");
				languageToRules.put(language, new LanguageRules());
			}

			return languageRules;
		}
	}

	// --------------------- File reading ---------------------

	// Record to hold the data about the line that is being currently processed
	private record LineProcessResult(String line, Matcher matcher, LanguageRule languageRule) {
	}

	// Helper function to read a file line by line processing each line with the given LanguageRules
	private static void processFileWithRules(AnalysisEntity entity, LanguageRules languageRules, ConcurrentLinkedQueue<LanguageRule> rules, Consumer<LineProcessResult> matchProcessor) {

		// We first set up the variables needed for the reading of the file:

		// Map to keep track of the number of times a rule has been used
		Map<LanguageRule, Integer> ruleToCount = new HashMap<>();

		// Flag to keep track of our position inside a comment or not
		AtomicBoolean isInsideComment = new AtomicBoolean(false);

		// Current code text rule used as a flag to determine if we are in a code text or not
		AtomicReference<CodeTextRule> currentTextLiteral = new AtomicReference<>(null);

		// We try to open the Entity's file
		try {
			BufferedReader br = new BufferedReader(new FileReader(entity.getPath()));
			String line;

			// We first process the constant rules, because they are always applied, ignoring the line
			List<LanguageRule> constantRules = rules.stream().filter(rule -> rule.constantValue() != null && rule.type() != RuleTypes.dependency).toList();
			rules.removeAll(constantRules);

			for (LanguageRule constantRule : constantRules) matchProcessor.accept(new LineProcessResult("", null, constantRule));

			// Then, we iterate each line, ignoring the empty ones:
			while ((line = br.readLine()) != null) {
				if ((line = line.trim()).isEmpty()) continue;

				// We remove comments and code text
				line = leaveOnlyCode(line, languageRules, isInsideComment, currentTextLiteral);
				if (line.isEmpty()) continue;

				// We then iterate over every rule
				for (LanguageRule rule : rules) {

					// We check for the rule pattern inside the current line after replacing local variables with their actual value
					Matcher matcher = Pattern.compile(swapWithVar(rule.pattern(), "||", entity)).matcher(line);
					if (matcher.find()) {
						matchProcessor.accept(new LineProcessResult(line, matcher, rule));

						// After applying the rule, we increase its counter
						int count = ruleToCount.getOrDefault(rule, 0);
						ruleToCount.put(rule, count + 1);

						// If the rule has reached its total number, we remove it from the queue and the map
						if (rule.totalNumber() != -1 && count >= rule.totalNumber()) {
							rules.remove(rule);
							ruleToCount.remove(rule);
						}
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Error while reading " + entity.getName() + ": " + e.getMessage());
		}
	}


	// Helper function to leave only code inside a line, removing comments or code text
	private static String leaveOnlyCode(String line, LanguageRules languageRules, AtomicBoolean isInsideComment, AtomicReference<CodeTextRule> currentCodetext) {
		if (line == null || line.isEmpty()) return "";

		// We prepare the variables needed for the operations on the string:

		// The final string
		StringBuilder result = new StringBuilder();

		// The current position
		int pos = 0;

		// And the original line lingth
		int lineLength = line.length();

		// We iterate for every position of the string
		while (pos < lineLength) {

			// If we are inside a multi-line comment, we look for it's closing
			if (isInsideComment.get()) {
				int endPos = -1;
				int endLength = 0;

				// We search the closest multi-line comment end
				for (String commentEnd : languageRules.getCommentEnds()) {
					int idx = line.indexOf(commentEnd, pos);

					// If we found a vailable comment end:
					if (idx != -1 && (endPos == -1 || idx < endPos)) {

						// We update its position and length
						endPos = idx;
						endLength = commentEnd.length();
					}
				}

				// If we found a comment end:
				if (endPos != -1) {

					// We are not in a comment anymore and jump to the end
					isInsideComment.set(false);
					pos = endPos + endLength;
				} else {

					// Or else, we are still in a comment and can return
					break;
				}
			}
			// If we are inside a code text block, we look for it's closing
			else if (currentCodetext.get() != null) {

				// We search the closest code text end that isn't an end exception
				CodeTextRule textRule = currentCodetext.get();
				int endPos = -1;

				// We scan for the remaining positions in the string
				while (pos < lineLength) {
					if (line.startsWith(textRule.end(), pos)) {

						// For each end exception:
						boolean isException = false;
						for (String endExc : textRule.endExceptions()) {

							// We check if the found code text end is one of the end exceptions
							if (pos >= endExc.length() && line.startsWith(endExc, pos - endExc.length())) {
								isException = true;
								break;
							}
						}

						// If we didn't find any exception that contains the end, we can jump to it
						if (!isException) {
							endPos = pos;
							break;
						}
					}

					pos++;
				}

				if (endPos != -1) {

					// If we found the end of the code text, we can remove the current code text and jump to the end of it
					pos = endPos + textRule.end().length();
					currentCodetext.set(null);
				} else {

					// Or else, we are still in a code text and can return
					break;
				}
			}
			// We are not inside a comment or a code text, so we look for the start of one of them
			else {
				// First, we check for a single-line comment start, that would make all the rest of the line a comment
				for (String singleCommentStart : languageRules.getLineComments()) if (line.indexOf(singleCommentStart, pos) != -1) return result.toString();

				// If we are not in a single line of comment, we need to find the earliest multi-line comment or code text start
				int newPos = lineLength;
				String tempString = null;
				CodeTextRule firstCodeTextRule = null;

				// For each multi-line comment start:
				for (String commentStart : languageRules.getCommentStarts()) {
					int idx = line.indexOf(commentStart, pos);

					// We check if it's present and the first one
					if (idx != -1 && idx < newPos) {

						// In that case, we update the temp string and new position
						tempString = line.substring(pos, idx);
						newPos = idx + commentStart.length();
					}
				}

				// For each code text start:
				for (CodeTextRule codeTextRule : languageRules.getCodeTexts()) {
					int idx = line.indexOf(codeTextRule.start(), pos);

					// We check if it's present and the first one
					if (idx != -1 && (idx < newPos)) {

						// In that case, we update the first text rule, temp string and new position
						firstCodeTextRule = codeTextRule;
						tempString = line.substring(pos, idx);
						newPos = idx + codeTextRule.start().length();
					}
				}

				// If the temp string is null, we didn't find any starts
				if (tempString == null) {

					// So, we can add the rest of the string and return
					result.append(line.substring(pos));
					break;
				} else {

					// If we found a start, we add to the result only the part before it and jump to it
					result.append(tempString);
					pos = newPos;

					// Depending on if it was a comment or a code text, we update the flags accordingly
					if (firstCodeTextRule == null) {
						isInsideComment.set(true);
					} else {
						currentCodetext.set(firstCodeTextRule);
					}
				}
			}
		}

		return result.toString();
	}

	// --------------------- Public functions ---------------------

	// Fills an Entity Variables by reading its file and applying its language's rules
	public static void fillVariables(AnalysisEntity entity) {

		// We get the rules for this language and return if there aren't any
		LanguageRules languageRules = getLanguageRules(entity.getFileType());
		if (languageRules.hasNoRules()) return;

		// We keep only variable-type rules
		ConcurrentLinkedQueue<LanguageRule> variableRules = languageRules.getRules().stream().filter(rule -> rule.type() == RuleTypes.variable).collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
		if (variableRules.isEmpty()) return;

		// Using the file process function, we get only the rules that can be applied
		processFileWithRules(entity, languageRules, variableRules, (lineProcessResult) -> {

			// We check the rule for its constant value, so we know if It's constant or not
			String constantValue = lineProcessResult.languageRule.constantValue();
			if (constantValue != null) {

				// We get ready to compute the final value to assign to the Variable
				String finalValue;
				String[] tokens = constantValue.split("\\s");

				// We will swap only the local values (|| and not !!) because in the Variable filling we only have the local ones, no other file is being processed

				if ((constantValue.startsWith("\"") && constantValue.endsWith("\"")) || tokens.length == 1) {

					// If the value is a single word, we can use the helper function to get its value
					finalValue = swapWithVar(constantValue, "||", entity);
					entity.addVariable(new Variable(lineProcessResult.languageRule.name(), finalValue, lineProcessResult.languageRule.hidden()));
				} else if (tokens.length == 3) {

					// If the value has three words, they must be two values and an operation. We start by getting the two values
					String firstVar = swapWithVar(tokens[0], "||", entity);
					String secondVar = swapWithVar(tokens[2], "||", entity);

					// Then, we can execute the operation
					finalValue = switch (tokens[1]) {
						case "-" -> firstVar.replace(secondVar, "");
						case "+" -> firstVar + secondVar;
						default -> null;
					};

					// Finally, we can assign the new variable
					if (finalValue != null) entity.addVariable(new Variable(lineProcessResult.languageRule.name(), finalValue, lineProcessResult.languageRule.hidden()));

					// If the constant rule has the debug flag, we print the result
					if (lineProcessResult.languageRule.debug()) System.out.println("Applied " + entity.getFileType() + " constant Variable " + lineProcessResult.languageRule.name() + " of value: " + finalValue);
				}
			} else {
				// If a Variable is not constant, we can use the helper function to get the matching string
				String matchGroup = getMatchingString(lineProcessResult.matcher());

				// If the rule has the debug flag, we print the line that has been found
				if (lineProcessResult.languageRule.debug())
					System.out.println("Applied " + entity.getFileType() + " Variable " + lineProcessResult.languageRule.name() + " on: \"" + lineProcessResult.line + "\" with result: " + matchGroup);


				// Finally, we can assign the new variable
				entity.addVariable(new Variable(lineProcessResult.languageRule.name(), matchGroup, lineProcessResult.languageRule.hidden()));
			}
		});
	}


	// Reads an Entity Identifiers by reading its file and applying its language's rules
	public static List<Identifier> readIdentifiers(AnalysisEntity entity) {
		List<Identifier> identifiers = new ArrayList<>();

		// We get the rules for this language and return if there aren't any
		LanguageRules lr = getLanguageRules(entity.getFileType());
		if (lr.hasNoRules()) return identifiers;

		// We keep only identifier-type rules
		ConcurrentLinkedQueue<LanguageRule> identifierRules = lr.getRules().stream().filter(rule -> rule.type() == RuleTypes.identifier).collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
		if (identifierRules.isEmpty()) return identifiers;

		// Using the file process function, we get only the rules that can be applied
		processFileWithRules(entity, lr, identifierRules, (lineProcessResult) -> {

			// We check the rule for its constant value, so we know if It's constant or not
			if (lineProcessResult.languageRule.constantValue() != null) {

				// We don't need to account for operations inside an Identifier value
				String finalValue = swapWithVar(lineProcessResult.languageRule.constantValue(), "||", entity);

				// So, we can just assign its value to the new Identifier
				identifiers.add(new Identifier(finalValue, lineProcessResult.languageRule.name(), 0, lineProcessResult.languageRule.hidden()));

				// If the constant rule has the debug flag, we print the result
				if (lineProcessResult.languageRule.debug()) System.out.println("Applied " + entity.getFileType() + " constant Identifier " + lineProcessResult.languageRule.name() + " of value: " + finalValue);
			} else {
				// If an Identifier is not constant, we can use the helper function to get the matching string
				String matchGroup = getMatchingString(lineProcessResult.matcher());

				// There can be multiple identifiers in a single line, so we split and iterate on them
				String[] tokens = matchGroup.split("\\s*,\\s*");
				for (String token : tokens) {
					if ((token = token.trim()).isEmpty()) continue;

					// If the rule has the debug flag, we print the line that has been found
					if (lineProcessResult.languageRule.debug())
						System.out.println("Applied " + entity.getFileType() + " Identifier " + lineProcessResult.languageRule.name() + " on: \"" + lineProcessResult.line + "\" with result: " + matchGroup);

					// Finally, we can swap the Variables values insithe the token and create a new Identifier from it
					identifiers.add(new Identifier(swapWithVar(token, "||", entity), lineProcessResult.languageRule.name(), 0, lineProcessResult.languageRule.hidden()));
				}
			}
		});

		return identifiers;
	}


	// Finds an Entity Dependencies by reading its file and applying its language's rules
	public static List<FileDependency> findDependencies(AnalysisEntity entity, String language) {
		List<FileDependency> fileDependencies = new ArrayList<>();
		// Map to avoid iterating the fileDependencies list to find the same target entity
		Map<AnalysisEntity, FileDependency> entityToFileDependency = new HashMap<>();

		// We get the rules for this language and return if there aren't any
		LanguageRules languageRules = getLanguageRules(language);
		if (languageRules.hasNoRules()) return fileDependencies;

		// We keep only dependency-type rules
		ConcurrentLinkedQueue<LanguageRule> dependencyRules = languageRules.getRules().stream().filter(rule -> rule.type() == RuleTypes.dependency).collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
		if (dependencyRules.isEmpty()) return fileDependencies;

		// We will need a list of all Identifiers and a map to keep track of the already applied rules (to use them as conditions for other rules)
		Set<Identifier> allIdentifiers = DirectoryAnalyzer.identifierToEntity.keySet();
		Map<AnalysisEntity, List<String>> entityToAppliedRulesNames = new HashMap<>();

		// Using the file process function, we get only the rules that can be applied
		processFileWithRules(entity, languageRules, dependencyRules, (lineProcessResult) -> {
			LanguageRule rule = lineProcessResult.languageRule();
			AnalysisEntity targetEntity;

			// We check the rule for its constant value, so we know if It's constant or not
			if (rule.constantValue() == null) {
				String matchGroup = getMatchingString(lineProcessResult.matcher());

				// There can be multiple Dependencies in a single line, so we split and iterate on them
				String[] tokens = matchGroup.split("\\s*,\\s*");
				for (String dependencyName : tokens) {
					if ((dependencyName = dependencyName.trim()).isEmpty()) continue;

					// For each identifier with the same name, we check for a different Entity than the current one (can't depend on itself)
					List<Identifier> sameNameIdentifiers = DirectoryAnalyzer.nameToIdentifiers.get(dependencyName);
					if (sameNameIdentifiers == null) continue;
					for (Identifier identifier : sameNameIdentifiers) {
						if (entity.hasIdentifier(identifier)) continue;

						// We find the other Entity that could be a Dependency to this one
						targetEntity = DirectoryAnalyzer.identifierToEntity.get(identifier);

						// For now, Entities with different programming languages will be ignored
						if (!entity.getFileType().equals(targetEntity.getFileType())) continue;

						// We use the helper function to check if the rule's conditions are satisfied
						if (failedRule(lineProcessResult, entity, targetEntity, entityToAppliedRulesNames)) continue;

						// We add the used rule to the applied ones
						entityToAppliedRulesNames.computeIfAbsent(entity, k -> new ArrayList<>()).add(rule.name() + targetEntity.getPath());

						// If the rule has the debug flag, we print the line and value that have been found
						if (lineProcessResult.languageRule.debug())
							System.out.println("Applied " + entity.getFileType() + " Dependency " + lineProcessResult.languageRule.name() + " on: \"" + lineProcessResult.line + "\" with result: " + matchGroup);

						// If a FileDependency between these two Entities already exists, we add the newly found Dependency to it using the map
						FileDependency fileDependency = entityToFileDependency.get(targetEntity);
						if (fileDependency == null) {
							// If there were not already a FileDependency between these two Entities, we create a new one
							fileDependency = new FileDependency(targetEntity, new ArrayList<>(List.of(new Dependency(identifier, rule.name(), lineProcessResult.languageRule.hidden()))));

							entityToFileDependency.put(targetEntity, fileDependency);
							fileDependencies.add(fileDependency);
						} else {
							fileDependency.addUniqueDependency(new Dependency(identifier, rule.name(), lineProcessResult.languageRule.hidden()));
						}

						// Finally, we finish the search
						break;
					}
				}

			} else {
				// Dependencies that have a constant value will be searched through all Identifiers only checking conditions and not their Idenitifer name
				for (Identifier identifier : allIdentifiers) {

					// We find the other Entity that could be a Dependency to this one
					targetEntity = DirectoryAnalyzer.identifierToEntity.get(identifier);

					// For now, Entities with different programming languages will be ignored
					if (!entity.getFileType().equals(targetEntity.getFileType())) continue;

					// We use the helper function to check if the rule's conditions are satisfied
					if (failedRule(lineProcessResult, entity, targetEntity, entityToAppliedRulesNames)) continue;

					// We add the used rule to the applied ones
					entityToAppliedRulesNames.computeIfAbsent(entity, k -> new ArrayList<>()).add(rule.name() + targetEntity.getPath());


					// If the rule has the debug flag, we print the line that has been found
					if (lineProcessResult.languageRule.debug())
						System.out.println("Applied " + entity.getFileType() + " Dependency " + lineProcessResult.languageRule.name() + " on: \"" + lineProcessResult.line + "\" with result: " + identifier.name());

					// If a FileDependency between these two Entities already exists, we add the newly found Dependency to it using the map
					FileDependency fileDependency = entityToFileDependency.get(targetEntity);
					if (fileDependency == null) {
						// If there were not already a FileDependency between these two Entities, we create a new one
						fileDependency = new FileDependency(targetEntity, new ArrayList<>(List.of(new Dependency(identifier, rule.name(), lineProcessResult.languageRule.hidden()))));

						entityToFileDependency.put(targetEntity, fileDependency);
						fileDependencies.add(fileDependency);
					} else {
						// We only apply a constant Dependency once for FileDependency, so we check if it has a unique rule
						fileDependency.addUniqueRuleDependency(new Dependency(identifier, rule.name(), lineProcessResult.languageRule.hidden()));
					}

					// For constant Dependencies, we don't finish the search after only one find because all Identifiers must be searched
				}
			}
		});

		return fileDependencies;
	}

	// --------------------- Helpers for public functions ---------------------

	// Helper function to safely get the matching string in the given Matcher
	private static String getMatchingString(Matcher matcher) {
		if (matcher.groupCount() < 1) {

			// If there are no caught strings, we can return
			return "";
		} else {

			// Then, we check for the value of the caught string
			String matchGroup = matcher.group(1);
			if (matchGroup == null || matchGroup.isEmpty()) {

				// If the caught string is null or empty, we can return
				return "";
			} else {

				// The string is valid and can be returned
				return matchGroup;
			}
		}
	}


	// Helper function to swap variable markers (such as "||VARIABLE||") with their string value
	private static String swapWithVar(String string, String margins, AnalysisEntity entity) {
		StringBuilder resultLine = new StringBuilder();

		// We iterate on the string to find the given margins
		int index = 0;
		while (index < string.length()) {

			// We search for the starting margins
			int start = string.indexOf(margins, index);
			if (start == -1) {

				// If we found nothing, we can append the rest of the tring and return
				resultLine.append(string.substring(index));
				break;
			}

			// If we found a margin, we add the text before it to the result
			resultLine.append(string, index, start);

			// We search for the ending margins
			int end = string.indexOf(margins, start + margins.length());
			if (end == -1) {

				// If we found nothing, we can append the rest of the tring and return
				resultLine.append(string.substring(index));
				break;
			}

			// Now that we found both margins, we can read the variable name
			String variableName = string.substring(start + margins.length(), end);

			// Now that we have the variable name, we swap its value accordingly. First we check for global variables, then for Entity's Variables
			if (variableName.equals("NAMEOFFILE")) {
				resultLine.append(entity.getName());
			} else if (variableName.equals("NAMEOFFILEONLY")) {
				resultLine.append(entity.getNameOnly());
			} else if (variableName.equals("PATHOFFILE")) {
				resultLine.append(entity.getPath().replace(File.separatorChar, '/'));
			} else if (variableName.equals("PATHOFFILEONLY")) {
				String resultString = entity.getPath().substring(0, entity.getPath().length() - entity.getExtension().length() - 1);
				resultLine.append(resultString.replace(File.separatorChar, '/'));
			} else if (variableName.equals("NAMEOFDIRECTORY")) {
				String temp = entity.getPath().replace(entity.getName(), "");
				temp = temp.substring(0, temp.length() - 1);
				resultLine.append(temp.substring(temp.lastIndexOf(File.separatorChar) + 1));
			} else if (variableName.equals("PATHOFDIRECTORY")) {
				resultLine.append(entity.getPath().replace(File.separator + entity.getName(), ""));
			}
			// If the variable is not global, we search its value from the Entity's Variables
			else if (entity.getVariablesMap().containsKey(variableName)) {
				resultLine.append(entity.getVariablesMap().get(variableName));
			}

			// We jump to the end of the current Variable's margins, so we can try to find another one
			index = end + margins.length();
		}

		return resultLine.toString();
	}

	// Hepler function to get the actual value of the string, swapping Variables and applying Patterns
	private static String getStringValue(String string, AnalysisEntity entity, AnalysisEntity targetEntity, LineProcessResult lineProcessResult) {

		// First, we swap the Variables with their respective values, using the current Entity for each ("||" is local and "!!" is for the target)
		string = swapWithVar(string, "||", entity);
		string = swapWithVar(string, "!!", targetEntity);

		// If the string is a Pattern:
		if (string.startsWith("\"") && string.endsWith("\"")) {
			string = string.substring(1, string.length() - 1);

			try {
				// We try to compile it and search for its caught string
				Matcher matcher = Pattern.compile(string).matcher(lineProcessResult.line);
				if (matcher.find()) return matcher.group(1);
			} catch (PatternSyntaxException _) {
			}
		}

		// If the string is not a Pattern, if it is, but invalid, or if no string got caught, we return the original string with the Variables swapped
		return string;
	}


	// Helper function to check if a rule fails to satisfy its conditions
	private static boolean failedRule(LineProcessResult lineProcessResult, AnalysisEntity entity, AnalysisEntity targetEntity, Map<AnalysisEntity, List<String>> entityToAppliedRules) {
		LanguageRule rule = lineProcessResult.languageRule();

		// If there are no conditions, then the rule succeds
		if (rule.conditions() == null || rule.conditions().isEmpty()) return false;

		// For each condition, we check if it's satisfied. Multiple conditions inside a rule always act as an AND
		for (String condition : rule.conditions()) {
			if (!respectedCondition(condition, lineProcessResult, entity, targetEntity, entityToAppliedRules)) {

				// If the rule has the debug flag, we print the failure of the condition
				if (rule.debug()) System.out.println("Failed condition: " + condition + " for rule " + rule.name() + " on line: " + lineProcessResult.line);

				return true;
			}
		}

		// If all conditions were satisfied, the rule succedes
		return false;
	}

	// Helper function to check if a single condition is satisfied
	private static boolean respectedCondition(String condition, LineProcessResult lineProcessResult, AnalysisEntity entity, AnalysisEntity targetEntity, Map<AnalysisEntity, List<String>> entityToAppliedRules) {

		// First, we split the conditions in its different parts
		String[] tokens = condition.split(" ");

		// If the first word is "not", the condition will be inverted
		boolean inverted = tokens[0].equals("not");
		if (inverted) {

			// If we found the "not" token, we remove it from the array containing the other words
			String[] tempTokens = new String[tokens.length - 1];
			System.arraycopy(tokens, 1, tempTokens, 0, tempTokens.length);
			tokens = tempTokens;
		}

		// For the next checks, we will need the Entity's variables
		Map<String, String> variablesMap = entity.getVariablesMap();

		if (tokens.length == 2) {

			// For two-words conditions, there should be an expression and a Variable. We start by swapping the Variable's value
			tokens[1] = getStringValue(tokens[1], entity, targetEntity, lineProcessResult);

			// Then, we apply the expression
			switch (tokens[0]) {
				case "==":
					return inverted ^ variablesMap.get(tokens[1]).equals(targetEntity.getVariablesMap().get(tokens[1]));
				case "contains":
					return inverted ^ variablesMap.get(tokens[1]).contains(targetEntity.getVariablesMap().get(tokens[1]));
			}
		} else if (tokens.length == 3) {

			// For three-words conditions, there should be two Variables and an expression in the middle. We start by swapping the Variables values
			String target1 = getStringValue(tokens[0], entity, targetEntity, lineProcessResult);
			String target2 = getStringValue(tokens[2], entity, targetEntity, lineProcessResult);

			// Then, we apply the expression
			switch (tokens[1]) {
				case "==":
					return inverted ^ target1.equals(target2);
				case "contains":
					return inverted ^ target1.contains(target2);
				case "startswith":
					return inverted ^ target1.startsWith(target2);
				case "endswith":
					return inverted ^ target1.endsWith(target2);
			}
		}
		// For one-word conditions, there should be only one Variable or rule name. They can contain spaces, so we don't check for the length of the tokens

		// First, we check for a common equal Variable
		if (variablesMap.containsKey(condition)) return inverted ^ variablesMap.get(condition).equals(targetEntity.getVariablesMap().get(condition));

		// Then, we check for a rule name
		List<String> appliedLanguageRules = null;
		String newCondition = null;
		String entityPath = null;

		if (condition.startsWith("||") && condition.endsWith("||")) {

			// If the condition is local, we extract its name and store the Entity's applied rules and the target Entity's path
			newCondition = condition.substring(2, condition.length() - 2);
			entityPath = targetEntity.getPath();
			appliedLanguageRules = entityToAppliedRules.get(entity);
		} else if (condition.startsWith("!!") && condition.endsWith("!!")) {

			// If the condition is on the target, we extract its name and store the applied rules for the target and the Entity's path
			newCondition = condition.substring(2, condition.length() - 2);
			entityPath = entity.getPath();
			appliedLanguageRules = entityToAppliedRules.get(targetEntity);
		}

		// Finally, we can check if a rule between those two entities has been already applied
		if (appliedLanguageRules != null && appliedLanguageRules.contains(newCondition + entityPath)) return !inverted;

		// If none of the previous checks has been satisfied, we try to use the condition as a Pattern
		if (condition.startsWith("\"") && condition.endsWith("\"")) {
			condition = condition.substring(1, condition.length() - 1);

			// We get the actual string value and try to compile the Pattern. If it's valid, we return the boolean match
			try {
				Matcher matcher = Pattern.compile(getStringValue(condition, entity, targetEntity, lineProcessResult)).matcher(lineProcessResult.line);
				return inverted ^ matcher.find();
			} catch (PatternSyntaxException _) {
			}
		}

		// If none of the checks were respected, the condition is not satisfied
		return false;
	}

}
