package backend;
import java.util.Vector;

public class FPCClone 
{
	int mCtg;
	int mL;
	int mR;
	Vector<String> mRem;
	Vector<String> mMrks;
	String mName;
	
	public FPCClone(String name, int ctg, int left, int right, Vector<String> rem, Vector<String> mrk)
	{
		mCtg = ctg;
		mL = left;
		mR = right;
		mRem = rem;
		mName = name;
		mMrks = mrk;
	}
}
