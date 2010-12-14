/**
 * This class provides implements a command line tool for creating and
 * extracting ZIP files.
 * 
 * @author Jamie Cho
 * @version 1.0.0
 */

package com.jcho.tools;

import java.io.File;

import com.jcho.util.zip.ZipUtil;

public class ZipTool {
	/** Expected number of arguments. */
	private final static int    EXPECTED_NUM_ARGS  = 3;
	/** Command for creating ZIP files. */
	private final static String CREATE_COMMAND     = "-cf";
	/** Command for extracting ZIP files. */
	private final static String EXTRACT_COMMAND    = "-xf";
	
	/** Error message displayed when the number of arguments is wrong. */
	private final static String ERROR_MESSAGE_WRONG_NUMBER_OF_ARGUMENTS = "ZipTool requires %d arguments.";
	/** Error message displayed when the command argument is wrong. */
	private final static String ERROR_MESSAGE_UNKNOWN_COMMAND           = "%s is an unknown command.";
	/** Error message displayed when creating a ZIP file by the last parameter is not a directory. */
	private final static String ERROR_MESSAGE_NOT_DIRECTORY             = "The last argument MUST specify a valid directory when creating ZIP files.";
	/** Error message displayed when a ZIP file could not be unzipped. */
	private final static String ERROR_MESSAGE_COULD_NOT_UNZIP           = "Failed to unzip %s: %s";
	/** Error message displayed when a ZIP file could not be created. */
	private final static String ERROR_MESSAGE_COULD_NOT_ZIP             = "Failed to zip %s: %s";
	
	/**
	 * Creates a ZIP file if -cf is specified and extracts a ZIP file if
	 * -xf is specified. The zipfile argument specifies the path to the
	 * ZIP file and the directory argument specifies the src or destination
	 * directory.
	 * 
	 * @param  argv                expects  {-cf | -xf}, zipfile, directory
	 */
	public static void main(String[] argv) {
		// Check the command line arguments
		String errorMessage = verifyCommandLineArguments(argv);
		if (errorMessage != null) {
			outputUsage();
			System.err.println(errorMessage);
			return;
		}
		
		// Extract the command line arguments
		String command = argv[0];
		File   zipFile = new File(argv[1]);
		File   dirPath = new File(argv[2]);
		
		// Invoke ZipUtil to do the heavy lifting
		try {
			if (command.equals(CREATE_COMMAND))			
				ZipUtil.zip(dirPath, zipFile);
			else
				ZipUtil.unzip(zipFile, dirPath);						
		} catch(Throwable throwable) {
			String errMessage 
				= command.equals(CREATE_COMMAND)
				? ERROR_MESSAGE_COULD_NOT_ZIP
				: ERROR_MESSAGE_COULD_NOT_UNZIP;
			System.err.format(errMessage + "\n", zipFile.toString(),
					          throwable.getMessage());
		}		
	}

	/**
	 * Verifies the command line arguments for ZipTool.
	 * 
	 * @param  argv                expects  {-cf | -xf}, zipfile, directory
	 * @return null if there are no errors and an error message otherwise.
	 */
	private static String verifyCommandLineArguments(String[] argv) {
		String errorMessage 
			= (argv.length != EXPECTED_NUM_ARGS) 
			? String.format(ERROR_MESSAGE_WRONG_NUMBER_OF_ARGUMENTS,
							EXPECTED_NUM_ARGS)
		    : null;
		String command = null;
		File dirFile = null;
		if (errorMessage == null) {
			command = argv[0];
			dirFile = new File(argv[2]);
		}
	
		// Verify the command is valid
		if ((errorMessage == null)
			&& !(command.equals(CREATE_COMMAND) || command.equals(EXTRACT_COMMAND)))
			errorMessage
				= String.format(ERROR_MESSAGE_UNKNOWN_COMMAND,
								EXPECTED_NUM_ARGS);			
	
		// Verify that the last item is a directory when creating a ZIP file
		if ((errorMessage == null)
			&& command.equals("-cf") && !dirFile.isDirectory())
			errorMessage = ERROR_MESSAGE_NOT_DIRECTORY;
		
		return errorMessage;
	}
	
	/**
	 * Output to System.err the usage of this tool.
	 */
	private static void outputUsage() {
		System.err.println("java -jar ZipTool {-cf | -xf}, zipfile, directory");
		System.err.println("-cf creates a new ZIP file");
		System.err.println("-xf extract contents from a ZIP file\n");
		System.err.println("This tool does not handle special attributes such as permissions");
		System.err.println("and forks. When creating a ZIP file, this tool convert links into");
		System.err.println("files and directories but only includes a directory once.");
		System.err.println("It will traverse links that point to parent directories which");
		System.err.println("could cause unexpectedly large ZIP files.\n");
	}
}
