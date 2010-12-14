/**
 * This class provides functionality to recursively extract the contents of
 * a ZIP file to a given directory.
 * 
 * @author Jamie Cho
 * @version 1.0.0
 */
package com.jcho.util.zip;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
	/** Buffer size to use for buffer I/O in bytes. */
	private final static int IO_BUFFER_SIZE_BYTES = 0x10000;

	/** Character used to separate components in ZipEntry names. */
	private final static char zipNameSeparatorChar = '/';
	
	/** The common (not universal) file system parent directory indicator. */
	private final static String PARENT_DIRECTORY_COMPONENT = "..";
	
	/** Error used when a ZipEntry Name refers to an absolute path. */
	private final static String ERROR_MESSAGE_ZIP_NAME_IS_ABSOLUTE               = "Illegal ZipEntry Name refers to an absolute path: %s"; 
	/** Error used when a ZipEntry Name contains parent folder path components. */
	private final static String ERROR_MESSAGE_ZIP_NAME_CONTAINS_PARENT_REFERENCE = "Illegal ZipEntry Name contains parent directory components: %s"; 
	/** Error used when a ZipEntry Name contains parent folder path components. */
	private final static String ERROR_FAILED_TO_CREATE_DIRECTORY                 = "Failed to create the directory: %s"; 

	/**
	 * Extracts the contents of the ZIP file to the given output directory.
	 * <p>
	 * This method is thread safe.
	 * 
	 * @param  inputZipFile     points to the ZIP file to extract
	 * @param  outputDirectory  destination folder of the extracted ZIP
	 *                          contents
	 * @throws IOException      if an I/O error has occurred
	 * @throws ZipException     if a ZIP error has occurred
	 */
	public static void unzip(File inputZipFile, File outputDirectory) 
		throws IOException, ZipException {
		// Get the required streams, allocate required buffers
		InputStream inputZipStream = new FileInputStream(inputZipFile);
		ZipInputStream zipInputStream = new ZipInputStream(inputZipStream);
		byte[] buffer = new byte[IO_BUFFER_SIZE_BYTES];
		
		try {
			// Iterate through each ZipEntry, unzipping the content to output
			// directory.
			for (ZipEntry zipEntry = zipInputStream.getNextEntry();
			     zipEntry != null;
			     zipInputStream.closeEntry(),
			     zipEntry = zipInputStream.getNextEntry())
				unzip(zipInputStream, zipEntry, outputDirectory, buffer);
		} finally {
			zipInputStream.close();
		}		
	}

	/**
	 * Creates a ZIP file with the contents of the given directory. The
	 * resulting ZIP file will NOT include directory in its paths.
	 * <p>
	 * This method is thread safe.
	 * 
	 * @param  inputDirectory   source directory from which to extract ZIP
	 *                          contents
	 * @param  outputZipFile    points to the ZIP file to create.
	 * @throws IOException      if an I/O error has occurred
	 * @throws ZipException     if a ZIP error has occurred
	 */
	public static void zip(File inputDirectory, File outputZipFile) 
		throws IOException, ZipException {
		// canonicalTraversedFiles is the set of all directories that we have
		// traversed. It is here to avoid infinite loops caused by symlinks
		// that point to ancestor directories.
		final Set<File> canonicalTraversedDirs = new HashSet<File>();
		
		// To help avoid stack overflow, we add items to a list rather than
		// recurse. filesToTraverse is the collection of all files
		// that we must still traverse.
		List<File> filesToTraverse = new LinkedList<File>();
		filesToTraverse.add(inputDirectory);
		
		// Create required buffers and the outputStream to the zip file.
		byte[] buffer = new byte[IO_BUFFER_SIZE_BYTES];
		String inputDirectoryPath = inputDirectory.toString(); 
		FileOutputStream outputFileStream 
			= new FileOutputStream(outputZipFile);
		ZipOutputStream zipOutputStream 
			= new ZipOutputStream(outputFileStream);
		try {
			while(filesToTraverse.size() > 0) {
				File currentFile = filesToTraverse.remove(0);
				zip(zipOutputStream, currentFile, inputDirectoryPath,
					canonicalTraversedDirs, filesToTraverse, buffer);
			}
		} finally {
			zipOutputStream.close();
		}		
	}

	/**
	 * Creates the directories for the given path.
	 * @param  file              path of the directory
	 * @throws IOException       if the directory does not exist and can not
	 *                           be created.
	 */
	private static void mkdirs(File file) throws IOException {
		if (!file.isDirectory() && !file.mkdirs())
			throw new IOException(
				String.format(ERROR_FAILED_TO_CREATE_DIRECTORY,
						      file.toString()));		
	}
	
	/**
	 * Converts a File to an appropriate ZipEntry name.
	 * 
	 * @param  file                File to convert to a ZipEntry name
	 * @param  baseFile            Base file that MUST make up the first part
	 *                             of file
	 * @param  isDirectory         True iff file was read to be a directory.
	 * @return an appropriate name for a ZipEntry
	 */
	private static String convertFileToZipEntryName(File    file,
			                                        String  baseFile,
			                                        boolean isDirectory) {
		// remove baseFile + the subsequent fileSeparatorChar from file
		String fileName = file.toString();
		String relPath = fileName.substring(Math.min(baseFile.length() + 1,
				                            fileName.length()));
		
		// convert fileSeparatorChar to zipNameSeparatorChar
		relPath = relPath.replaceAll("\\" + File.separatorChar,
				                     "" + zipNameSeparatorChar);
		
		// Append zipNameSeparatorChar if isDirectory
		return relPath + (isDirectory ? zipNameSeparatorChar : "");
	}
	
	/**
	 * Converts a ZipEntry name to a relative file path.
	 * 
	 * @param  zipEntryName     original ZipEntry name
	 * @return relative file path corresponding to ZipEntry name
	 * @throws ZipException     if there is an illegal name entry
	 * @throws IOException      if the resulting File is illegal on the
	 *                          current file system.
	 */
	private static String convertZipEntryNameToPath(String zipEntryName)
		throws ZipException {
		// ZipEntry names use / as separators. We must: 
		// 1. Remove trailing / characters
		// 2. Convert / to File.separatorChar
		if (zipEntryName.endsWith("/"))
			zipEntryName = zipEntryName.substring(0, zipEntryName.length() - 1);
		String processedFilename 
			= zipEntryName.replace(zipNameSeparatorChar, File.separatorChar);
		
		// Security check:
		// Make sure this is a relative File to avoid writing files in an
		// unexpected area.
		File file = new File(processedFilename);
		if (file.isAbsolute())
			throw new ZipException(
				String.format(ERROR_MESSAGE_ZIP_NAME_IS_ABSOLUTE,
						      zipEntryName));
		
		// Security check:
		// Make sure there are no PARENT_DIRECTORY_COMPONENT that might force
		// writing files outside the expected area.
		List<String> components 
			= Arrays.asList(processedFilename.split("\\" + File.separatorChar));
		if (components.indexOf(PARENT_DIRECTORY_COMPONENT) >= 0)
			throw new ZipException(
				String.format(ERROR_MESSAGE_ZIP_NAME_CONTAINS_PARENT_REFERENCE,
						      zipEntryName));
		
		return file.toString();
	}
	
	/**
	 * Extracts the contents of the given ZipEntry file to the given output
	 * directory.
	 * 
	 * @param  zipInputStream   ZipInputStream from which to extract content
	 * @param  zipEntry         must be the current ZipEntry in zipInputStream
	 * @param  outputDirectory  destination folder of zipEntry
	 * @param  buffer           buffer to use for I/O
	 * @throws IOException      if an I/O error has occurred
	 */
	private static void unzip(ZipInputStream zipInputStream,
							  ZipEntry       zipEntry,
							  File           outputDirectory,
							  byte[]         buffer) 
		throws ZipException, IOException {
		// Get the location of the destination file
		String relativePath 
			= convertZipEntryNameToPath(zipEntry.getName());
		File destFile = new File(outputDirectory, relativePath);
		
		// If this is a directory, create the directory and continue.
		if (zipEntry.isDirectory()) {
			mkdirs(destFile);
			return;
		}
		
		// Otherwise, this is a file entry. Create the parent
		// directory.
		File destDir = destFile.getParentFile();
		mkdirs(destDir);
	
		// Create the output stream
		FileOutputStream outStream = new FileOutputStream(destFile);
		try {
			// Write the contents of this ZipEntry to outStream
			for (int numReadBytes = zipInputStream.read(buffer);
			     numReadBytes > -1;
			     numReadBytes = zipInputStream.read(buffer))						
				outStream.write(buffer, 0, numReadBytes);
		} finally {
			outStream.close();
		}				
	}
	
	/**
	 * Adds the given file to the ZIP file.
	 * 
	 * @param  zipOutputStream     destination for inputFile.     
	 * @param  inputFile           File to add to ZIP
	 * @param  baseFile            Base file that MUST make up the first part
	 *                             of inputFile 
	 * @param  traversedDirs       current list of canonical traversed
	 *                             directories. This set is appended to as
	 *                             needed
	 * @param  filesToTraverse     list of files remaining to traverse. files
	 *                             are appended to this list when inputFile is
	 *                             a directory
	 * @param  buffer              buffer to use for I/O
	 * @throws IOException         if an I/O error has occurred
	 */
	private static void zip(ZipOutputStream zipOutputStream,
			                File            inputFile,
			                String          baseFile,
			                Set<File>       canonicalTraversedDirs,
			                List<File>      filesToTraverse,
			                byte[]          buffer) 
		throws IOException {
		// Verify that we have not previously traversed this file
		File canonicalFile = inputFile.getCanonicalFile();
		if (canonicalTraversedDirs.contains(canonicalFile))
			return;

		// Figure out whether or not this file is a directory. Create
		// its corresponding ZipEntry.
		File[] newFiles = inputFile.listFiles();
		boolean isDirectory = (newFiles != null);
		String zipEntryName 
			= convertFileToZipEntryName(inputFile,
										baseFile,
					                    isDirectory);
		ZipEntry zipEntry = new ZipEntry(zipEntryName);

		// If this file is a directory, add its contents to
		// filesToTraverse 
		if (newFiles != null) {
			// Add this file to the list of canonical dirs
			canonicalTraversedDirs.add(canonicalFile);					
			
			// Add the child files to filesToTraverse
			for (File childFile : newFiles)
				filesToTraverse.add(childFile);
			
			// If the directory is empty, add it to zipOutputStream
			if (newFiles.length == 0) {
				// Don't try to create root dir
				if (!zipEntryName.equals("" + zipNameSeparatorChar)) {
					zipOutputStream.putNextEntry(zipEntry);
					zipOutputStream.closeEntry();					
				}					
			}
			
			return;
		}
		
		// Add currentFile's contents to zipOutputStream
		FileInputStream inStream = new FileInputStream(inputFile);
		zipOutputStream.putNextEntry(zipEntry);
		try {
			// Write the contents of this ZipEntry to outStream
			for (int numReadBytes = inStream.read(buffer);
			     numReadBytes > -1;
			     numReadBytes = inStream.read(buffer))						
				zipOutputStream.write(buffer, 0, numReadBytes);
		} finally {
			inStream.close();
		}
		zipOutputStream.closeEntry();
	}			
}
