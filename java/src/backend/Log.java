package backend;

import java.io.FileWriter;
import java.io.IOException;

import util.Logger;


public class Log implements Logger
{
	public FileWriter logFile = null;
	
	public Log(FileWriter fw) {
		logFile = fw;
	}
	
	public Log(String filePath) throws IOException
	{
		this( new FileWriter(filePath,false) );
	}
	
	public void msg(String s)
	{
		System.out.println(s);
		
		if (logFile != null) {
			try {
				logFile.write(s + "\n");		
				logFile.flush();
			}
			catch(Exception e) {
				e.printStackTrace();
			}		
		}
	}
	public void msgToFile(String s)
	{
		
		if (logFile != null) {
			try {
				logFile.write(s + "\n");		
				logFile.flush();
			}
			catch(Exception e) {
				e.printStackTrace();
			}		
		}
	}	
	public void write(char c)
	{
		System.out.print(c);
	}
}
