package util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileDir {
	/********************************************************************
	 * XXX File ops
	 */
	
	// Return the Files; if fromHome==true, relative to home directroy
	public static File getFile(String name, boolean fromHome) {
		if (name == null) return new File("");
		if (fromHome) {
			String dir = null;
			try {
				dir = System.getProperty("user.home");
			}
			catch (Exception e) {ErrorReport.print(e, "Get file from users's directory");}
			return new File(dir,name);
		}
		return new File(name);
	}
	public static void deleteFile ( String strPath ){
		File theFile = new File ( strPath );
		theFile.delete();
	}

	public static void clearAllDir(File d){ // does not remove top directory d
		if (d.isDirectory()){
			for (File f : d.listFiles()){
				if (f.isDirectory() && !f.getName().equals(".") && !f.getName().equals("..")) {
					clearAllDir(f);
				}
				f.delete();
			}
		}
	}
	public static boolean deleteDir(File dir) { 
        if (dir.isDirectory()) {
            String[] subdir = dir.list();
            
            if (subdir != null) {
	            for (int i = 0;  i < subdir.length;  i++) {
	                boolean success = deleteDir(new File(dir, subdir[i]));
	                if (!success) {
	                	System.err.println("Error deleting " + dir.getAbsolutePath());
	                    return false;
	                }
	            }
            }
        } 
        System.gc(); // closes any file streams that were left open to ensure delete() succeeds
        return dir.delete();
    }
    public static boolean fileExists(String filepath) {
    	if (filepath == null) return false;
    	File f = new File(filepath);
    	return f.exists() && f.isFile();
    }
    public static boolean dirExists(String filepath){
    	if (filepath == null) return false;
    	File f = new File(filepath);
    	return f.exists() && f.isDirectory();
    }
    public static boolean pathExists(String path) {
		if (path == null) return false;
		File f = new File(path);
		return f.exists();
    }
    public static int dirNumFiles(File d){
		int numFiles = 0;
		for (File f : d.listFiles()){
			if (f.isFile() && !f.isHidden()) numFiles++;	
		}
		return numFiles;
	}
    
	public static File checkCreateDir(String dir, boolean bPrt) {
		try {
			File f = new File(dir);
			if (f.exists() && f.isFile()) {
				Popup.showErrorMessage("Please remove file " + f.getName()
					+ " as SyMAP needs to create a directory at this path");
				return null;
			}
			if (!f.exists()) {
				f.mkdir();
				if (bPrt) System.out.println("Create directory " + dir); 
			}
			return f;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create dir " + dir);
			return null;
		}
	}
	public static void checkCreateFile(String path, String trace){
		File f = new File(path);
		if (f.exists()) {
			f.delete();
		}
		try {
			f.createNewFile();
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create file " + path);
		}
	}
	
	public static File checkCreateFile(File path, String name, String trace){
		File f = new File(path, name);
		if (f.exists()) {
			f.delete();
		}
		try {
			f.createNewFile();
			return f;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create file " + path.getName() + " " + name);
			return null;
		}	
	}
	
	public static String fileOnly(String path) {
		return path.substring(path.lastIndexOf("/")+1, path.length());
	}
	
	public static String fileDate(String path) { 
	try {
		File f = new File(path);
		 if (f.exists()) {
	         long lastModified = f.lastModified();
	         Date date = new Date(lastModified);
	         SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
	         String formattedDate = formatter.format(date);
	         return formattedDate;
		 }
	}
	catch (Exception e) {ErrorReport.print(e, "get file date " + path); }
	return "";
	}
	// return path/file  (not path//file or pathfile path/file/)
	public static String fileNormalizePath(String path, String file) {
		String p = path, f = file;
		
		if (path.endsWith("/") && file.startsWith("/")) p = p.substring(0, p.length()-1);
		else if (!path.endsWith("/") && !file.startsWith("/")) f = "/" + f;
		
		if (file.endsWith("/")) f  = f.substring(0, f.length()-1); 
		
		return p + f;
	}
}
