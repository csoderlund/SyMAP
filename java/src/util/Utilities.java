package util;

import java.util.Date;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

import symap.Globals;

/**
 * Time
 * basic array and string ops
 * text
 */
public class Utilities {
	public static void sleep(int milliseconds) {
 		try{ Thread.sleep(milliseconds); }
 		catch (InterruptedException e) { }
	}
	/**********************************************************
     * XXX Time methods
     */
	static public String getNormalizedDate(String nDate) {// date from NOW() 'year-mo-dy time'
		String time="";
		if (Globals.TRACE || Globals.INFO) {
			String [] tok1 = nDate.split(" "); 
			if (tok1.length==2) {
				if (tok1[1].contains(":")) time = tok1[1].substring(0, tok1[1].lastIndexOf(":"));
			}
		}
		String d = nDate.substring(0, nDate.indexOf(" "));
		String [] tok = d.split("-");
		if (tok.length!=3) return nDate;
		
		int m = getInt(tok[1]);
		String ms="Jan";
		if (m==2) ms="Feb"; 
		else if (m==3) ms="Mar";
		else if (m==4) ms="Apr";
		else if (m==5) ms="May";
		else if (m==6) ms="Jun";
		else if (m==7) ms="Jul";
		else if (m==8) ms="Aug";
		else if (m==9) ms="Sep";
		else if (m==10) ms="Oct";		
		else if (m==11) ms="Nov";
		else if (m==12) ms="Dec";
		
		return tok[2] + "-" + ms + "-" + tok[0] + " " + time;
	}
	
	public static String reformatAnnotDate(String adate) { // For Project - show on left of manager (see DB project.annotdate)
		try {
			String dt;
			if (adate.indexOf(" ")>0) dt = adate.substring(0, adate.indexOf(" "));
			else if (adate.startsWith("20")) dt = adate;
			else return "???";
			
			SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd"); 
	        Date d = sdf.parse(dt);
	        sdf = new SimpleDateFormat("ddMMMyy"); 
	        return sdf.format(d);
		}
		catch (Exception e) {return "???";}
	}
	static public String getDateTime ( ){
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy HH:mm"); 
        return sdf.format(date);
    }
	static public String getDateOnly ( ) {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy"); 
        return sdf.format(date);
    }
	// System.currentTimeMillis()     
	public static String getDurationString(long duration) { // milliseconds
    	duration /= 1000;
    	long min = duration / 60; 
    	long sec = duration % 60; 
    	long hr = min / 60;
    	min = min % 60; 
    	long day = hr / 24;
    	hr = hr % 24;
    	
    	return (day > 0 ? day+"d:" : "") + (hr > 0 ? hr+"h:" : "") + (min > 0 ? min+"m:" : "") + sec + "s";
	}
	static public long getNanoTime () {
 		return System.nanoTime(); 
	}
	static public String getNanoTimeStr(long startTime) { 
		long et = System.nanoTime()-startTime;
		long sec = et /1000000000;
		return timerStr2(sec);
	}
	static public void printElapsedNanoTime(String msg, long startTime) {
		long et = System.nanoTime()-startTime;
		long sec = et /1000000000;
		String t = timerStr2(sec);
		System.out.format("%-20s  %s\n", msg, t);
	}
	
	static public String timerStr2(long et) {
		long day = 	et/86400; //24*3600
		long time =  et%86400;
		long hr =  time/3600;
		
		time %= 3600;
		long min = time/60;
		long sec = time%60;
		
		String str = " ";
		if (day > 0) str += day + "d:";
		if (hr > 0 ) str += hr + "h:";
		str += min + "m:" + sec + "s";
		return str;
	}

	
	/*******************************************************************
	 * XXX basic array and string ops
	 */
	 public static String pad(String s, int width){
	    width -= s.length();
	    while (width-- > 0) s += " ";
	    return s;
	 }
	public static String getCommaSepString(int[] ints) {
		if (ints == null || ints.length == 0) return "";
		StringBuffer ret = new StringBuffer().append(ints[0]);
		for (int i = 1; i < ints.length; i++) ret.append(",").append(ints[i]);
		return ret.toString();
	}
	public static double getDouble(String i) { 
		try {
			double x = Double.parseDouble(i);
			return x;
		}
		catch (Exception e) {return -1.0;}
	}
	public static int getInt(String i) { 
		try {
			int x = Integer.parseInt(i);
			return x;
		}
		catch (Exception e) {return -1;}
	}
	
	public static boolean isEmpty(String s) {
		return (s == null || s.length() == 0);
	}
	public static boolean isOverlap(int start1, int end1, int start2, int end2) {
		if ((start1 >= start2 && start1 <= end2) || (end1  >= start2 && end1 <= end2)
		 || (start2 >= start1 && start2 <= end1) || (end2  >= start1 && end2 <= end1)) return true;
		
		return false;
	}
    
	/*****************************************************************************
	 * XXX Format
	 */
	// HitData;
	public static String coordsStr(boolean isStrandPos, int start, int end) {
		String o = (isStrandPos) ? "+" : "-";
		return String.format("%s(%,d - %,d) %,dbp", o, start, end, (end-start+1)) ;
	}
	
    // for writing to log
    static public String kMText(long len) {
		double d = (double) len;
		String x = len+"";
		if (len>=1000000000) {
			d = d/1000000000.0;
			x = String.format("%.2fB", d);
		}
		else if (len>=1000000) {
			d = d/1000000.0;
			x = String.format("%.2fM", d);
		}
		else if (len>=1000)  {
			d = d/1000.0;
			x = String.format("%.1fk", d);
		}
		return x;
	}
    static public String kMText(int len) {// for xToSymap summary; 
		double d = (double) len;
		String x = len+"";
		if (len>=1000000000) {
			d = d/1000000000.0;
			x = String.format("%dB", (int) d);
		}
		else if (len>=1000000) {
			d = d/1000000.0;
			x = String.format("%dM", (int) d);
		}
		else if (len>=1000)  {
			d = d/1000.0;
			x = String.format("%dk", (int) d);
		}
		return x; // <1000
	}
    static public String kText(int len) { 
    	if (len>=10000) {
    		double d = Math.round(((double) len)/1000.0);
    		return String.format("%dk", (int) d);
    	}
    	else {
    		return String.format("%,4d", len); // <10000
    	}
    }
   
    /*****************************************************
     * XXX Tag; The following are to parse the gene tag (I made a mess of this)
     * v544 DB: 992.a (1 746)
     * v542 DB: Gene #992a (9 1,306bp)
     * Exon tag created in Annotation class
     */
    public static String convertTag(String tag) {
    	if (!tag.startsWith("Gene")) return tag;
    	
    	String dbtag = tag.replace("bp","");
    	Pattern pat1 = Pattern.compile("Gene #(\\d+)([a-z]+[0-9]*)(.*)$");
		Matcher m = pat1.matcher(dbtag); // pre-CAS543 Gene #2 (9 1,306bp) or Gene #2b (9 1,306bp)
		if (m.matches()) {
			String d = m.group(1);
			String s = m.group(2);
			String p = m.group(3);
			return d + "." + s + " " + p;
		}
		Pattern pat2 = Pattern.compile("Gene #(\\d+)(.*)$");
		m = pat2.matcher(dbtag);
		if (m.matches()) {
			String d = m.group(1);
			String p = m.group(2);
			return d + "." + " " + p;
		}
		return dbtag;
    }
    // Annotation class; create fullTag for hover
    static public String createFullTagFromDBtag(String tag) {
    	String [] tok = tag.split("\\(");
    	
    	if (tok.length!=2) { // this should not happen but maybe very old db
    		if (tag.startsWith("Gene")) return tag;
    		else return Globals.geneTag + tag;
    	}
 
    	tok[1] = "(#Exon=" + tok[1];
    	if (!tag.startsWith("Gene")) { 
    		tok[0] = Globals.geneTag + tok[0];
    		tok[1] = tok[1].replace(")","bp)");
    	}
		return tok[0] + " " + tok[1];
    }
    // Annotation.popupDesc; e.g  1563.w (15 2164)
    // return tok[0]=Gene #1563.w and tok[1]=#Exons=15 2,164bp
    static public String [] getGeneExonFromTag(String tag) {
    	String [] ret = new String [2];
    	String [] tok = tag.split("\\(");
    	
    	if (tok.length!=2) {
    		System.out.println("SyMAP Error parsing tag: " + tag);
    		ret[0] = Globals.geneTag +  tag;
			ret[1] = Globals.exonTag +  " 0 0";
			return ret;
    	}
    
    	ret[0] = tok[0];
    	tok[1] = tok[1].replace(")","");
    	ret[1] = exonNum() + tok[1].replace(")",""); 
    	
    	if (!tag.startsWith("Gene")) { // v543 992.a (1 746)  old version started with Gene
			ret[0] = Globals.geneTag +  tok[0].trim();
			ret[1] = ret[1] + "bp";
		}
    	return ret;
    }
    // exonTag = "Exon #"; returns #Exons=
    public static String exonNum() { 
    	return "#" + Globals.exonTag.substring(0, Globals.exonTag.indexOf(" ")) + "s=";
    }
    // Created in backend.AnchorsPost.Gene; return "d.[a]"
    static public String getGenenumFromDBtag(String tag) {
		if (!tag.startsWith("Gene")) { // CAS544 '2 (9 1306)' or '2.b (9 1306)'
			String [] tok = tag.split(" \\(");
			if (tok.length==2) return tok[0].trim();
			else return tag; // shouldn't happen
		}
		
		Pattern pat1 = Pattern.compile("Gene #(\\d+)([a-z]+[0-9]*)(.*)$");
		Matcher m = pat1.matcher(tag); // pre-CAS544 Gene #2 (9 1,306bp) or Gene #2b (9 1,306bp)
		if (m.matches()) {
			String y = m.group(1);
			String z = m.group(2);
			return y + "." + z;
		}
		
		Pattern pat2 = Pattern.compile("Gene #(\\d+)(.*)$");
		m = pat2.matcher(tag);
		if (m.matches()) {
			String y = m.group(1);
			return y + ".";
		}
		else return tag;
    }
    // symapQuery.DBdata.passFilters; return number only
    static public String getGenenumIntOnly(String tag) {
    	if (tag=="-") return "";
    	
    	String gn = tag.contains("(") ? getGenenumFromDBtag(tag) : tag; // the () has already been removed
    	
    	String [] tok = gn.split("\\.");
    	if (tok.length>0) return tok[0]; 
    	else return gn; 						
    }
    // symap.QueryPanel
    public static boolean isValidGenenum(String gn) {
		String n=gn, s=null;
		if (gn.contains(".")) {
			String [] tok = gn.split("\\.");
			if (tok.length==0 || tok.length>2) return false;
			n = tok[0];
			if (tok.length==2) s=tok[1];
		} 
		try {
			Integer.parseInt(n);
		}
		catch (Exception e) { return false;}
		if (s!=null) {
			try {
				Integer.parseInt(s);
				return false;
			}
			catch (Exception e) { return true;}
		}
		return true;
	}
    // remove leading zeros before making full block name
    static public String blockStr(String c1, String c2, int block) {
    	String x1 = (c1.startsWith("0") && c1.length()>1) ? c1.substring(1) : c1;
    	String x2 = (c2.startsWith("0") && c2.length()>1) ? c2.substring(1) : c2;
    	return x1 + "." + x2 + "." + block;
    }
    /*******************************************************
	 * XXX Table maker copied from TCW
	 */
	public static String makeTable(
			int nCol, int nRow, String[] fields, int [] justify, String [][] rows) 
	{	
		Vector <String> lines = new Vector <String>();
		makeTable(lines, nCol, nRow, fields, justify, rows);
		String x="";
		for (String l : lines) x += l + "\n";
		return x;
	}
	public static void makeTable(
			Vector <String> lines, int nCol, int nRow, String[] fields, int [] justify, String [][] rows)
	{
		int c, r;
		String line;
		String space = "  ";
		
		// compute column lengths
		int []collen = new int [nCol];
		for (c=0; c < nCol; c++) collen[c] = 0;
		
        for (c=0; c< nCol; c++) { // longest value
            for (r=0; r<nRow && rows[r][c] != null; r++) {
        		if (rows[r][c] == null) rows[r][c] = "";
        		if (rows[r][c].length() > collen[c]) 
        			collen[c] = rows[r][c].length();
            }
        }
        if (fields != null) {    // heading longer than any value?
			for (c=0; c < nCol; c++) {
				if (collen[c] > 0) {
					if (fields[c] == null) fields[c] = "";
					if (fields[c].length() > collen[c]) 
						collen[c]=fields[c].length();
				}
			}
	        // output headings
	        line = space; // length of space in front
	        for (c=0; c< nCol; c++) 
	        		if (collen[c] > 0) 
	        			line += pad(fields[c],collen[c], 1) + space;
	        lines.add(line);
        }
        // output rows
        for (r=0; r<nRow; r++) {
        	line = space;
            for (c=0; c<nCol; c++) {
                 if (collen[c] > 0) 
                	 	line += pad(rows[r][c],collen[c],justify[c]) + space;
                 rows[r][c] = ""; // so wouldn't reuse in next table
            }
            lines.add(line);
        }
	}
	
    private static String pad(String s, int width, int o){
		if (s == null) return " ";
        if (s.length() > width) {
            String t = s.substring(0, width-1);
            System.out.println("'" + s + "' truncated to '" + t + "'");
            s = t;
            s += " ";
        }
        else if (o == 0) { // left
            String t="";
            width -= s.length();
            while (width-- > 0) t += " ";
            s = t + s;
        }
        else {
            width -= s.length();
            while (width-- > 0) s += " ";
        }
        return s;
    } 
}
