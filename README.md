<div align=center>

# LIDA: Language Independent Dependency Analyzer

![lida graph](https://github.com/user-attachments/assets/0b1f5219-1f26-40a2-93e3-361ff81ad78a)

</div>

## What does Language Independent mean?

LIDA uses a dedicated file for each supported programming language, that contains the rules to find Identifiers and Dependencies inside the corresponding programming language's code.
These files are inside "LIDA/src/main/resources/org/lida/Languages", and are written in a syntax defined in Rulebook.txt (also inside the Languages directory) that can be understood by LIDA.
This approach isn't precise as other language-specific ones, but makes supporting a new programming language just a matter of some hours.
Also, with the expansion of the syntax and functionality of the rules files a high level of precision is achievable.

## Why not just use ANTLR?

First, this is my final assignment for my bachelor degree, so i wanted to challenge myself creating an idea that came to me some time ago, and i must say, the result is very plesant.
Handling my own syntax and logic geve me the possibility to abstract the concept of Dependency to a very high level.

## What are the currently supported languages?

For now, i write the rules for:

-Java  
-c  
-c++  
-c#  
-Rust  
-Ruby  
-GDScript  
-Javascript  
-Python  
-Typescript  
-Shell  
-Go  

For some of them the Dependency Analysis is still vague, but for others (like Java) the analysis is already quite precise.

## Functionalities:

LIDA has a basic user interface and can create both graphs for the file structure of any codebase and the dependency structure of the supported languages.
The graph can be exported as an image and i will be working to export them as json/xml/plantuml as well.

## What to expect in the future?

I will improve and expand the rule's syntax to support more complex behaviour and consequently improve the analysis quality.
I want to improve the user interface with quality-of-life improvements, such as a search bar and new visualizations (needed for very large codebases).
I also have ideas for more accurate analysis, such as warnings for dangerous cases or improvements suggestion.
