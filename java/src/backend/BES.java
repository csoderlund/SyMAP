package backend;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class BES 
{
	String mClone;
	String mRFstr;
	RF mRF;
	Pattern mBESPat;
	
	public BES()
	{
		mBESPat = Pattern.compile("(\\S+)(r|f)",Pattern.CASE_INSENSITIVE);
	
	}
	public boolean parseBES(String bes) throws Exception
	{
		if (bes.length() > 30)
			throw(new Exception("BES name " + bes + " is too long (max 30 chars)"));

		bes = bes.replace(".","");
		Matcher m = mBESPat.matcher(bes);
		if (m.matches())
		{
			mClone = m.group(1);
			mRFstr = m.group(2).toLowerCase();
			mRF = (mRFstr.equals("r") ? RF.R : RF.F);			
			return true;
		}
		
		return false;
	}

	
}
