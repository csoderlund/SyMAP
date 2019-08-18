package util;

// Purpose - provide a way to tell threads they are cancelled b/c
// Java doesn't have a good way
public class Cancelled 
{
	static boolean cancelled = false;
	
	public static boolean isCancelled() { return cancelled;}
	public static void cancel() { cancelled = true;}
	public static void init() { cancelled = false;}
}
