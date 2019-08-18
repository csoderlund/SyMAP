package backend;
import java.util.Vector;

public class SyStats 
{
	float mAvg = 0;
	float mDev = 0;
	
	public SyStats(Vector<Float> list)
	{		
		float N = list.size();
		float S = 0;
		float sqS = 0;
		for (float x : list)
		{
			S += x;
			sqS += x*x;
		}
		mAvg = S/N;
		float sqAvg = sqS/N;
		mDev = (float)Math.sqrt(sqAvg - mAvg*mAvg);
	}
	public float getAvg()
	{
		return mAvg;
	}
	public float getDev()
	{
		return mDev;
	}
}
