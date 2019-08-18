package backend;

import java.util.Comparator;
import java.util.TreeMap;

/*
 *  Handle sorting groups in the 3 ways possible in the params files
 */
enum GrpSortType {Numeric,Alpha,Explicit};

public class GroupSorter implements Comparator<String>
{
	private static GrpSortType mType;
	private static TreeMap<String,Integer> mGrpOrder;
	
	public GroupSorter(GrpSortType type)
	{
		mType = type;
	}
	
	public GroupSorter(String[] grpList) throws Exception
	{
		mType = GrpSortType.Explicit;
		
		mGrpOrder = new TreeMap<String,Integer>();
		
		mGrpOrder.put("0", 0);
		for (int i = 0; i < grpList.length; i++)
		{
			String name = grpList[i].trim();
			if (name.equals("0"))
				throw(new Exception("don't use \"0\" in grp_order list in param file \"0\" - this is reserved for unanchored contigs"));				
			mGrpOrder.put(name,i+1);
			//System.out.println("grporder add " + grpList[i]);
		}
	}
	
	public boolean orderCheck(GroupInt g)
	{
		if (mType == GrpSortType.Explicit)
		{
			String name = g.getName();
			//System.out.println("grporder check " + name);
			if (!mGrpOrder.containsKey(name))
			{
				return false;
			}
		}
		return true;
	}
	public boolean orderCheck(String name)
	{
		if (mType == GrpSortType.Explicit)
		{
			//System.out.println("grporder check " + name);
			if (!mGrpOrder.containsKey(name))
			{
				return false;
			}
		}
		return true;
	}	
	public int compare(GroupInt g1, GroupInt g2)
	{	
		return compare(g1.getName(), g2.getName());
	}
	// Intelligent sequence name sorter (albeit very inefficient).
	// Strips off any matching prefix, then does a numeric compare
	// on the remainder,  if it is numeric. 
	public int compare(String g1, String g2)
	{
		if (mType == GrpSortType.Explicit)
		{
			return mGrpOrder.get(g1) - mGrpOrder.get(g2);
		}
		if (g1.equals(g2))
		{
			return 0;
		}
		boolean areNumbers = true;
		try
		{
			Integer.parseInt(g1);
			Integer.parseInt(g2);
		}
		catch(Exception e)
		{
			areNumbers = false;
		}
		if (areNumbers)
		{
			return Integer.parseInt(g1) - Integer.parseInt(g2);
		}
		// Look for a matching prefix
		int nMatch = 0;
		while (nMatch < g1.length() && nMatch < g2.length()
			//		&& !Character.isDigit(g1.getName().charAt(nMatch))
			//		&& !Character.isDigit(g2.getName().charAt(nMatch))
					&& g1.charAt(nMatch) == g2.charAt(nMatch))
		{
			nMatch++;
		}
		if(nMatch == g1.length())
		{
			return -1; // all of g1 matched, hence must come before g2 alphabetically 
		}
		if(nMatch == g2.length())
		{
			return 1;
		}
		String suff1 = g1.substring(nMatch);
		String suff2 = g2.substring(nMatch);
		
		// Ok, are these suffixes numeric??
		areNumbers = true;
		try
		{
			Integer.parseInt(suff1);
			Integer.parseInt(suff2);
		}
		catch(Exception e)
		{
			areNumbers = false;
		}
		if (areNumbers)
		{
			return Integer.parseInt(suff1) - Integer.parseInt(suff2);
		}	
		// well, we tried...
		return g1.compareTo(g2);

	}
/*	public int compare(String g1, String g2)
	{	
		switch(mType)
		{
			case Numeric:
				return Integer.parseInt(g1) - Integer.parseInt(g2);
			case Alpha:
				return g1.compareTo(g2);
			case Explicit:
				return mGrpOrder.get(g1) - mGrpOrder.get(g2);
		}
		assert false;
		return 0;
	}	*/
}
