package backend;

import java.io.File;
import java.io.FileFilter;

public class IsFileFilter implements FileFilter 
{
	public boolean accept(File f) 
	{
		return f.isFile();
	}
}

