package backend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.sql.Statement;
import java.sql.ResultSet;

public class Utils 
{
	static TreeMap<String,Float> mStats;
	static Vector<String> mKeyOrder;
	static TreeMap<String,Vector<Integer>> mHistLevels;
	static TreeMap<String,TreeMap<Integer,Integer>> mHist;
	
	static void initStats()
	{
		mStats = new TreeMap<String,Float>();	
		mKeyOrder= new Vector<String>();
		//mHistLevels = new TreeMap<String,Vector<Integer>>();
		mHist = new TreeMap<String,TreeMap<Integer,Integer>>();
	}
	static void initHist(String key, Integer... levels)
	{
		//mHistLevels.put(key,new Vector<Integer>());
		mHist.put(key, new TreeMap<Integer,Integer>());
		for (Integer i : levels)
		{
			//mHistLevels.get(key).add(i);	
			mHist.get(key).put(i,0);
		}
		mHist.get(key).put(Integer.MAX_VALUE,0);
	}
	static void incHist(String key, int val) throws Exception
	{
		int l = -1;
		for (int lvl : mHist.get(key).keySet())
		{
			l = lvl;
			if (val < lvl) break;
		}
		if (l == -1)
		{
			throw(new Exception("Bad level: " + val));	
		}
		int curval = mHist.get(key).get(l);
		curval++;
		mHist.get(key).put(l,curval);
	}
	static void incHist(String key, int val, int inc) throws Exception
	{
		int l = -1;
		for (int lvl : mHist.get(key).keySet())
		{
			l = lvl;
			if (val < lvl) break;
		}
		if (l == -1)
		{
			throw(new Exception("Bad level: " + val));	
		}
		int curval = mHist.get(key).get(l);
		curval += inc;
		mHist.get(key).put(l,curval);
	}	
	static void dumpHist()
	{
		for (String name : mHist.keySet())
		{
			System.out.println("Histogram: " + name);	
			for (int lvl : mHist.get(name).keySet())
			{
				int val = mHist.get(name).get(lvl);
				System.out.println("<" + lvl + ":" + val);
			}
		}
	}
	static void initStat(String key)
	{
		mStats.put(key, 0.0F);	
		mKeyOrder.add(key);
	}
	static void incStat(String key, float inc)
	{
		if (mStats != null)
		{
			if (!mStats.containsKey(key))
			{
				initStat(key);	
			}
			mStats.put(key, inc + mStats.get(key));
		}
	}
	static void statAvg(String numkey, String denomkey, String avgkey)
	{
		if (mStats == null) return;
		if (!mStats.containsKey(numkey) || !mStats.containsKey(denomkey))
		{
			return;
		}	
		float num = mStats.get(numkey);
		float denom = mStats.get(denomkey);
		
		if (denom > .5) // in case comes out .0000001 or something
		{
			float avg = num/denom;
			incStat(avgkey,avg);
		}
		
	}
	static void dumpStats()
	{
		if (mStats == null) return;
		for (String key : mKeyOrder)
		{
			Float val = mStats.get(key);
			System.out.println(key + " = " + val);
		}
	}
	static void uploadStats(UpdatePool db, int pair_idx, int pidx1, int pidx2) throws Exception
	{
		if (mStats == null) return;
		for (String key : mKeyOrder)
		{
			Integer val = mStats.get(key).intValue();
			
			db.executeUpdate("replace into pair_props (pair_idx,proj1_idx,proj2_idx,name,value) values(" + pair_idx + 
					"," + pidx1 + "," + pidx2 + ",'" + key + "','" + val + "')");			
		}
	}	
	static int[] strArrayToInt(String[] sa)
	{
		int[] ia = new int[sa.length];
		for (int i = 0; i < sa.length; i++)
			ia[i] = Integer.parseInt(sa[i]);
		return ia;
	}
	
	static String strArrayJoin(String[] sa, String delim)
	{
		String out = "";
		if (sa != null) {
			for (int i = 0; i < sa.length; i++)
			{
				out += sa[i];
				if (i < sa.length -1)
					out += delim;
			}
		}
		return out;
	}
	public static String intArrayJoin(int[] sa, String delim)
	{
		String out = "";
		if (sa != null) {
			for (int i = 0; i < sa.length; i++)
			{
				out += sa[i];
				if (i < sa.length -1)
					out += delim;
			}
		}
		return out;
	}	
	static String intArrayToBlockStr(int[] ia)
	{
		String out = "";
		if (ia != null) {
			for (int i = 0; i < ia.length; i+=2)
			{
				out += ia[i] + ":" + ia[i+1];
				if (i + 1 < ia.length - 1)
					out += ",";
			}
		}
		return out;
	}
	
	static String strVectorJoin(java.util.Vector<String> sa, String delim)
	{
		String out = "";
		for (int i = 0; i < sa.size(); i++)
		{
			out += sa.get(i);
			if (i < sa.size() -1)
				out += delim;
		}
		return out;
	}
	
	static boolean intervalsTouch(int s1,int e1, int s2, int e2)
	{
		return intervalsOverlap(s1,e1,s2,e2,0);
	}	
	
	static public boolean intervalsOverlap(int s1,int e1, int s2, int e2, int max_gap)
	{
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	// purpose - but a debug breakpoint here and use in debug if statements
	static void noop()
	{
		System.out.println("");
		return;
	}	
	// Returns the amount of the overlap (negative for gap)
	static int intervalsOverlap(int s1,int e1, int s2, int e2)
	{
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return -gap;
	}	
	static boolean intervalContained(int s1,int e1, int s2, int e2)
	{
		return ( (s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}	
	
	static float simpleRatio(int top, int bot)
	{
		float ratio = (float)(.1*Math.round(10.0*(float)top/(float)bot));
		return ratio;
	}
	
	static public String join(Collection<?> s, String delimiter) 
	{
		String buffer = "";
	    Iterator<?> iter = s.iterator();
	    while (iter.hasNext()) 
	    {
	    	buffer += iter.next().toString();
	        if (iter.hasNext()) 
	        	buffer += delimiter;
	    }
        return buffer;	
	} 
	public static void clearDir(File d)
	{
		if (d.isDirectory())
		{
			//Log.msg("clear directory " + d.getAbsolutePath());

			for (File f : d.listFiles())
			{
				if (f.isDirectory() && !f.getName().equals(".") && !f.getName().equals("..")) 
				{
					clearDir(f);
				}
				f.delete();
			}
		}
		//WN why needed?? checkCreateDir(d);
	}
	
	static String getProjProp(int projIdx, String name, UpdatePool conn) throws SQLException
	{
		String value = null;
		
		String st = "SELECT value FROM proj_props WHERE proj_idx='" + projIdx + "' AND name='" + name +"'";
		ResultSet rs = conn.executeQuery(st);
		if (rs.next())
			value = rs.getString("value");
		rs.close();
		
		return value;
	}
	static void setProjProp(int projIdx, String name, String val, UpdatePool conn) throws Exception
	{
		conn.executeUpdate("delete from proj_props where name='" + name + "' and proj_idx=" + projIdx);
		conn.executeUpdate("insert into proj_props (name, value, proj_idx) values('" + name + "','" + val + "','" + projIdx + "')");	
	}
	static int getPairIdx(int proj1Idx, int proj2Idx, UpdatePool conn) throws SQLException
	{
		int idx = 0;
		
		String st = "SELECT idx FROM pairs WHERE proj1_idx='" + proj1Idx + "' AND proj2_idx='" + proj2Idx +"'";
		ResultSet rs = conn.executeQuery(st);
		if (rs.next())
			idx = rs.getInt("idx");
		rs.close();
		
		return idx;
	}
/*	static String getProjectName(int idx, Statement s)
	{
		String ret = "";
		try
		{
			ResultSet r = s.executeQuery("select name from projects where idx=" + idx);
			if (r.first()) ret = r.getString("name");
		}
		catch (Exception e ){System.out.println("Failed to load project name!!");}
		return ret;
	}*/
	static void concatFile(File from, File to) throws Exception
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter(to, true));
		BufferedReader br = new BufferedReader(new FileReader(from));
		while (br.ready())
		{
			bw.write(br.readLine());
			bw.newLine();
		}
		bw.flush();
		bw.close();
		br.close();
	}	

	
	static String getFirstLine(File f) throws IOException
	{
		BufferedReader fh = new BufferedReader(new FileReader(f));
		String firstLine = null;
		if (fh != null) {
			firstLine = fh.readLine();
			fh.close();
		}
		return firstLine;
	}
	
	static boolean yesNo(String question)
	{
		BufferedReader inLine = new BufferedReader(new InputStreamReader(
				System.in));

		System.err.print(question + " (y/n)? "); // CAS SOP no newline; easy to miss otherwise
		try
		{
			String resp = inLine.readLine();
			if (resp.equals("y"))
				return true;
			else if (resp.equals("n"))
				return false;
			else
			{
				System.err.println("Sorry, could not understand the response, please try again:");
				System.err.print(question + " (y/n)? "); // CAS SOP no newline; easy to miss otherwise
				resp = inLine.readLine();
				if (resp.equals("y"))
					return true;
				else if (resp.equals("n"))
					return false;
				else
				{
					System.err.println("Sorry, could not understand the response, exiting.");
					System.exit(0);
					// Have to just exit since returning "n" won't necessarily cause an exit
					return false;
				}
			
			}

		} catch (Exception e)
		{
			return false;
		}

		//unreachable return false;
	}
	public static boolean checkDoneFile(String dir) 
	{
		File f = new File(dir);
		File d = new File(f,"all.done");
		return d.exists();
	}	
	static void fastaPrint(BufferedWriter fh, String name, char[] str) throws IOException
	{
		final int FASTA_LINE_LEN = 80;
		
		if (name.length() > 0)
		{
			fh.append(">" + name);				
			fh.newLine();
		}
		for (int i = 0; i < str.length; i += FASTA_LINE_LEN)
		{
			int len = Math.min(FASTA_LINE_LEN, str.length-i);
			fh.write(str, i, len);
			fh.newLine();			
		}
		fh.flush();
	}
	
	static String ucFirst(String in)
	{
		if (in.length() <= 1) return in.toUpperCase();
		return in.substring(0, 1).toUpperCase() + in.substring(1);
	}
	static String getParamsName()
	{
		String s1 = "symap.config";
		String s2 = "params";
		File f = new File(s1);
		if (f.isFile())
		{
			return s1;	
		}
		f = new File(s2);
		if (f.isFile())
		{
			return s2;	
		}
		System.err.println("Parameters file " + s1 + " not found");
		return s1;
	}
	// Sort the Comparable array, and apply the same sorting to the Object array,
	// so they stay parallel.
	// The purpose of cmp is to sort nulls to one end.
	static void HeapDoubleSort(Comparable[] a, Object[] aa, ObjCmp cmp){
        int i,f,s;
        for(i=1;i<a.length;i++){
            Comparable e = a[i];
            Object ee = aa[i];
            s = i;
            f = (s-1)/2;
            while(s > 0 && cmp.compare(a[f], e) < 0){
                a[s] = a[f];
                aa[s] = aa[f];
                s = f;
                f = (s-1)/2;
            }
            a[s] = e;
            aa[s] = ee;
        }
        for(i=a.length-1;i>0;i--){
            Comparable value = a[i];
            Object vv = aa[i];
            a[i] = a[0];
            aa[i] = aa[0];
            f = 0;
            if(i == 1)
                s = -1;
            else
                s = 1;
            if(i > 2 && cmp.compare(a[1], a[2]) < 0)
                s = 2;
            while(s >= 0 && cmp.compare(value, a[s]) < 0){
                a[f] = a[s];
                aa[f] = aa[s];
                f = s;
                s = 2*f+1;
                if(s+1 <= i-1 && cmp.compare(a[s], a[s+1]) < 0)
                    s = s+1;
                if(s > i-1)
                    s = -1;
            }
            a[f] = value;
            aa[f] = vv;
        }
    }	
	public static class ObjCmp implements Comparator<Comparable>
	{
		public int compare(Comparable a, Comparable b)
		{
			if (a == null)
			{
				if (b == null) return 0;
				else return +1;
			}
			else if (b == null)
			{
				return -1;
			}
			else return a.compareTo(b);
		}
	}
	public static void checkCreateDir(File dir)
		{
			if (dir.exists() && !dir.isDirectory())
			{
				System.out.println("Please remove file " + dir.getAbsolutePath()
						+ " as SyMAP needs to create a directory at this path");
				System.exit(0);
			}
			if (!dir.exists())
			{
				dir.mkdir(); 
				if (!dir.exists())
				{
					System.out.println("Unable to create directory " + dir.getAbsolutePath());
					System.exit(0);
				}
			}
		}
	static String reverseComplement(String in)
	{
		in = (new StringBuffer(in)).reverse().toString().toUpperCase();
		in = in.replace('A', 't');
		in = in.replace('G', 'c');
		in = in.replace('C', 'g');
		in = in.replace('T', 'a');
		return in.toLowerCase();
	}
	public static void updateGeneFractions(UpdatePool db) throws Exception
	{
		TreeMap<String,Integer> projlist = new TreeMap<String,Integer>();
	
		db.executeUpdate("update blocks set ngene1 = (select count(*) from pseudo_annot as pa " +
		"where pa.grp_idx=grp1_idx  and (greatest(pa.start,start1) < least(pa.end,end1)) and pa.type='gene')");
		db.executeUpdate("update blocks set ngene2 = (select count(*) from pseudo_annot as pa " +
		"where pa.grp_idx=grp2_idx  and (greatest(pa.start,start2) < least(pa.end,end2)) and pa.type='gene')");
		
		db.executeUpdate("update blocks set genef1=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp1_idx)/ngene1 " +
				" where ngene1 > 0");
		db.executeUpdate("update blocks set genef2=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp2_idx)/ngene2 " +
				" where ngene2 > 0");
	}
	public static void updateGeneFractions(UpdatePool db, int pair_idx) throws Exception
	{
		db.executeUpdate("update blocks set ngene1 = (select count(*) from pseudo_annot as pa " +
				"where pa.grp_idx=grp1_idx  and greatest(pa.start,start1) < least(pa.end,end1) " +
				"  and pa.type='gene') where pair_idx=" + pair_idx);
		db.executeUpdate("update blocks set ngene2 = (select count(*) from pseudo_annot as pa " +
		"where pa.grp_idx=grp2_idx  and greatest(pa.start,start2) < least(pa.end,end2) " +
		"  and pa.type='gene') where pair_idx=" + pair_idx);

		db.executeUpdate("update blocks set genef1=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp1_idx)/ngene1 " +
				" where ngene1 > 0 and pair_idx=" + pair_idx);
		db.executeUpdate("update blocks set genef2=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp2_idx)/ngene2 " +
				" where ngene2 > 0 and pair_idx=" + pair_idx);
	}
}