package backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.sql.SQLException;
import java.util.regex.Pattern;

import util.Cancelled;
import util.Logger;

/*
 *  Used to hold the pseudomolecule data when loading pseudo project
 *  
 *  NB, assumes one sequence per seq file!!
 */

public class Pseudo extends GroupInt
{
	private static final int CHUNK_SIZE = 1000000; // don't change this or client breaks!!
	String mFileName;
	String mPath;
	
	public Pseudo(String filename, String path, String name, String prefix, int pidx)
	{
		super(name,prefix,pidx,ProjType.pseudo);
		mFileName = filename;
		mPath = path;
	}

	public long uploadSeq(UpdatePool pool, Logger log) throws SQLException, IOException
	{
		StringBuffer sb = new StringBuffer();
		int chunk = 0;
		long totalLen = 0;
				
		BufferedReader fh = new BufferedReader( new FileReader(new File(mPath)));
		fh.readLine(); // Skip first line, it is section name - FIXME assumption
		while (fh.ready())
		{
			if (Cancelled.isCancelled()) break;
			String line = fh.readLine().replaceAll("\\s+",""); 
			if (!line.matches("\\s*>.*") && line.matches(".*[^agctnAGCTN].*"))
			{
				log.msg("Bad line in " + mPath + " -- ABORTING");
				log.msg("Line contains characters other than agct");
				return 0;
			}
			sb.append(line);
			if (sb.length() > CHUNK_SIZE)
			{
				uploadChunk(sb,chunk,CHUNK_SIZE,pool); // clears sb
				totalLen += CHUNK_SIZE;
				chunk++;
			}
		}
		fh.close();
		
		if (sb.length() > 0)
		{
			assert(sb.length() <= CHUNK_SIZE);
			totalLen += sb.length();
			uploadChunk(sb,chunk,CHUNK_SIZE,pool);
			chunk++;
		}
		
		// Note: totalLen will be less than file size due to spaces removed.
		log.msg("Loaded: " + mPath + " " + totalLen + " bytes");
		
		String st = "INSERT INTO pseudos VALUES('" + getGrpIdx() + "','" + mFileName + "','" + totalLen + "')";
		pool.executeUpdate(st);
		
		return totalLen;
	}
	
	private void uploadChunk(StringBuffer sb, int chunk, int chunkSize, UpdatePool conn) throws SQLException
	{
		int len = Math.min(sb.length(),CHUNK_SIZE);
		String cseq = sb.substring(0,len);
		String st = "INSERT INTO pseudo_seq2 VALUES('" + getGrpIdx() + "','" + chunk + "','" + cseq + "')";
		conn.executeUpdate(st);
		sb.delete(0,len);
	}
}
