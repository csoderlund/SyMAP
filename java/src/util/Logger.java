package util;

// backend.Log for aligner
// util.ProgressDialog for writing to progress window and file

public interface Logger {
	public void msgToFile(String s);
	public void msg(String s);
	public void write(char c);
}
