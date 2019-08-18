package util;

public class ErrorCount 
{
	static int count = 0;
	
	public static void init() 
	{
		count = 0;
	}
	public static void inc()
	{
		count++;
	}
	public static int getCount()
	{
		return count;
	}
}
