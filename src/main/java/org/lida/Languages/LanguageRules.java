package org.lida.Languages;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;


// Class containing all information about a language's rules file
public class LanguageRules {

	// List of syntax used to comment
	private final List<Pair<String, String>> comments = new ArrayList<>();

	public int getCommendsCount() {
		return comments.size();
	}

	public void addComment(String start, String end) {
		comments.add(new Pair<>(start, end));
	}

	// Comments helper functions to get specific types of comments
	public List<String> getLineComments() {
		List<String> result = new ArrayList<>();
		for (Pair<String, String> comment : comments) {
			if (comment.getValue().isEmpty()) {
				result.add(comment.getKey());
			}
		}
		return result;
	}

	public List<String> getCommentStarts() {
		List<String> result = new ArrayList<>();
		for (Pair<String, String> comment : comments) {
			if (!comment.getValue().isEmpty()) {
				result.add(comment.getKey());
			}
		}
		return result;
	}

	public List<String> getCommentEnds() {
		List<String> result = new ArrayList<>();
		for (Pair<String, String> comment : comments) {
			if (!comment.getValue().isEmpty()) {
				result.add(comment.getValue());
			}
		}
		return result;
	}


	// CodeTextRules list of this language
	private final List<CodeTextRule> codeTexts = new ArrayList<>();

	public List<CodeTextRule> getCodeTexts() {
		return codeTexts;
	}

	public void addCodeText(String start, String end, List<String> escapes) {
		codeTexts.add(new CodeTextRule(start, end, escapes));
	}


	// LanguageRules list of this language
	private final List<LanguageRule> rules = new ArrayList<>();

	public List<LanguageRule> getRules() {
		return rules;
	}

	public void addRule(LanguageRule rule) {
		rules.addLast(rule);
	}

	public boolean hasNoRules() {
		return rules.isEmpty();
	}

}
