package toSymap;

/**************************************************
 * toSymap is part of the symap package, but has its own main.
 * The files ConvertNCBI and ConvertEnsembl can be extracted, modified and run stand-alone
 * The other files use backend.Constants and backend.Util, so can not be directly extracted
 */
public class xToSymap {
	protected static boolean isDebug=false;
	public static void main(String[] args) {
		if (hasArg(args, "-d")) {
			isDebug=true;
			System.out.println("Running in debug mode");
		}
		new ToFrame();
	}
	static boolean hasArg(String [] args, String arg) {
		for (int i=0; i<args.length; i++)
			if (args[i].equals(arg)) return true;
		return false;
	}
}
