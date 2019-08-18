package backend;

import java.io.File;
import java.io.FilenameFilter;

public class ExtensionFilter implements FilenameFilter 
{
	private String extension;
	public ExtensionFilter( String extension ) 
	{
		this.extension = extension;             
	}
	  
	public boolean accept(File dir, String name) 
	{
		return (name.endsWith(extension));
	}
}