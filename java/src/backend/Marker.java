package backend;

import java.util.Vector;

public class Marker 
{
	String mName;
	String mType;
	Vector<String> mRem;
	
	public Marker(String name,String type)
	{
		mName = name;
		mType = type;
		mRem = new Vector<String>();
	}
}
