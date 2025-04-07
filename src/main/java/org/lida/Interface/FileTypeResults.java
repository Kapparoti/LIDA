package org.lida.Interface;

// Record used to store data for each file type inside a directory. Used only in DirectoryDetails
public record FileTypeResults (String fileType, int fileCount, int lineCount, long storageSize, String color) {
	/*
	The data inside the record are:
		file type that we are searching for,
		file count of the files we found,
		storage size of the files we found,
		color representing the file type
	 */
}