package backend;

import java.io.File;
import java.io.FileWriter;
import java.util.Properties;
import java.util.Vector;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Queue;
import java.util.LinkedList;

import javax.swing.JOptionPane;
import java.sql.ResultSet;
import util.Cancelled;
import util.Logger;
import util.Utilities;

// Do the blats/mummers

public class AlignMain 
{
	private static final String PREPROC_DIR = "preproc";
	private static final String CONCAT_DIR = "concat";
	private static final int CHUNK_SIZE = 1000000; // don't change this, used in several classes
	
	private Logger log;
	private String proj1Name, proj2Name;
	private ProjType proj1Type, proj2Type;
	private int proj1Idx, proj2Idx;
	private Vector<ProgSpec> allAlignments;
	private Queue<ProgSpec> toDoList;
	private Queue<ProgSpec> toDoList2;
	private Vector<Thread> threads;
	private int nMaxThreads;
	private boolean error = false;
	public boolean mCancelled = false;
	private SyProps mMainProps = null;
	private boolean askReuseExisting = false;
	UpdatePool pool = null;
	Vector<File> preprocDirs;
	boolean notStarted = true;
	boolean interrupted = false;
	private String pairDir;
	
	public AlignMain(UpdatePool _pool, Logger log, 
			String proj1Name, String proj2Name,
			int numProcessors, Properties props, 
			SyProps pairProps, boolean askReuseExisting)
	{
		this.log = log;
		allAlignments = new Vector<ProgSpec>();
		toDoList = new LinkedList<ProgSpec>();
		toDoList2 = new LinkedList<ProgSpec>();
		threads = new Vector<Thread>();
		nMaxThreads = numProcessors;
		assert(pairProps != null);
		mMainProps = pairProps;
		this.askReuseExisting = askReuseExisting;
		pool = _pool;
		preprocDirs = new Vector<File>();
		
		this.proj1Name = proj1Name;
		this.proj2Name = proj2Name;
			
		try {
			this.proj1Type = pool.getProjType(proj1Name);
			this.proj2Type = pool.getProjType(proj2Name);
			proj1Idx = pool.getProjIdx(proj1Name,proj1Type);
			proj2Idx = pool.getProjIdx(proj2Name,proj2Type);
		}
		catch (Exception e) { 
			e.printStackTrace();
		}
	}
	
	private boolean checkWritePreproc(File d, int projIdx, String projName, UpdatePool pool, boolean concat, boolean singleFiles) throws Exception
	{
		boolean ok = false;
		if (Cancelled.isCancelled()) return false;
		if (dirNumFiles(d) > 0)
		{
			Utils.clearDir(d);
		}
		if (!ok)
		{
			String gmprop = Utils.getProjProp(projIdx, "mask_all_but_genes", pool);
			boolean geneMask = (gmprop != null && gmprop.equals("1"));	
			
			ResultSet rs = pool.executeQuery("select count(*) as nseqs from pseudos join groups on groups.idx=pseudos.grp_idx " +
				" where groups.proj_idx=" + projIdx);
			rs.first();
			int nSeqs = rs.getInt("nseqs");
			if (nSeqs == 0)
			{
				log.msg("No sequences are loaded for " + projName + "!!");
				return false;
			}
			boolean manyFiles = (nSeqs > 50);
			
			boolean needsPreproc = true; // actually we always need to do it because we might have to break up a file
										//  (geneMask || hasBadChars || manyFiles);
			
			if (needsPreproc)
			{
				if (geneMask)
				{
					log.msg(projName + ": Masking non-genic sequence"); 
				}
				
				writePreproc(geneMask,manyFiles,concat,projIdx,projName,d,pool,singleFiles);
				ok = true;
			}
		}		
		return ok;
	}

	private class Range
	{
		int s;
		int e;
		Range(int start, int end)
		{
			s = start; e = end;
		}
	}
	// BES/MRK just write them in one file
	private void WritePreprocFPC(FileType t1, File dir,int pidx,String proj1Name,UpdatePool db) throws Exception
	{
		if (!dir.exists()) dir.mkdir();
		preprocDirs.add(dir);
		File f = new File(dir,t1.toString());
		if (f.isFile()) f.delete();
		f.createNewFile();
		FileWriter fw = new FileWriter(f);

		String query = "";
		if (t1 == FileType.Bes)
		{
			query = "select concat(clone,type) as name, seq from bes_seq where proj_idx=" + pidx;
		}
		else
		{
			query = "select marker as name, seq from mrk_seq where proj_idx=" + pidx;
		}
		ResultSet rs = db.executeQuery(query);
		while (rs.next())
		{
			String name = rs.getString("name");
			String seq = rs.getString("seq");
			fw.write(">" + name + "\n");
			for (int j = 0; j < seq.length(); j += 50)
			{
				int endw = Math.min(j+50,seq.length());
				fw.write(seq.substring(j, endw));
				fw.write("\n");
			}
		}
		rs.close();
		fw.close();
	}
	private void writePreproc(boolean mask, boolean manyfiles, boolean concat, int projIdx, 
								String projName, File dir, UpdatePool pool, boolean singleFiles) throws Exception
	{
		if (!dir.exists()) dir.mkdir();
		preprocDirs.add(dir);
		manyfiles = true; // we always want to reorganize the files as they may have come in one big file with is not efficient as a target
		try
		{
			ResultSet rs;
			Vector<Vector<Integer>> groups = new Vector<Vector<Integer>>();
	
			int maxFileSize = 70000000;
			
			rs = pool.executeQuery("select grp_idx, length from pseudos join groups on groups.idx=pseudos.grp_idx " +
					" where groups.proj_idx=" + projIdx + " order by groups.sort_order");
			int nGrp = 0;
			groups.add(new Vector<Integer>());
			long curSize = 0;
			int nOrigGrp = 0;
			while (!interrupted && rs.next())
			{
				nOrigGrp++;
				int grp_idx = rs.getInt("grp_idx");
				if (manyfiles && !singleFiles)
				{
					// We're grouping the groups up to total seq length maxFileSize.
					// Or, if concat=true, we're concatenating all 
					long length = rs.getLong("length");
					if (!concat && nOrigGrp > 1 && curSize + length > maxFileSize)
					{
						nGrp++;
						groups.add(new Vector<Integer>());
						curSize = 0;
					}
					groups.get(nGrp).add(grp_idx);
					curSize += length;
				}
				else
				{
					// each group only has one group in it
					groups.get(nGrp).add(grp_idx);
					nGrp++;
					groups.add(new Vector<Integer>());
				}
			}
			if (concat) log.msg("Concatenating " + projName + " sequences for use as query");
			else if (manyfiles) log.msg("Writing " + projName + " sequence groups");
	
			TreeMap<Integer,TreeMap<Integer,Vector<Range>>> geneMap = new TreeMap<Integer,TreeMap<Integer,Vector<Range>>>();
			int cSize = 100000;
	
			if (mask)
			{
				// Build a map of the gene annotations that we can use to mask each sequence chunk as we get it.
				// The map is sorted into 100kb bins for faster searching. 
				rs = pool.executeQuery("select groups.idx,pseudo_annot.start,pseudo_annot.end from pseudo_annot join groups on groups.idx=pseudo_annot.grp_idx " +
						" where pseudo_annot.type='gene' and groups.proj_idx=" + projIdx);
				while (!interrupted && rs.next())
				{
					int grpIdx = rs.getInt("idx");
					int start = rs.getInt("start");
					int end = rs.getInt("end");
					if (!geneMap.containsKey(grpIdx))
					{
						geneMap.put(grpIdx, new TreeMap<Integer,Vector<Range>>());
					}
					int cStart = (int)Math.floor(start/cSize);
					int cEnd = (int)Math.ceil(end/cSize);
					for (int c = cStart; c <= cEnd; c++)
					{
						int s = c*cSize;
						int e = (c+1)*cSize - 1;
						int r1 = Math.max(s, start);
						int r2 = Math.min(e, end);
						if (!geneMap.get(grpIdx).containsKey(c)) geneMap.get(grpIdx).put(c, new Vector<Range>());
						geneMap.get(grpIdx).get(c).add(new Range(r1,r2));
					}
					if (interrupted) break;
				}
			}
			if (interrupted) return;
			// Now go through each grouping, write the preproc file, with masking if called for.
			// Note that sequences are stored in chunks of size CHUNK_SIZE.
			for (int i = 0; i < groups.size(); i++)
			{
				if (groups.get(i).size() == 0) break; // last one can come up empty
				String fileName = projName + ".pp" + (i+1) + ".fasta";
				File f = new File(dir,fileName);
				if (f.isFile()) f.delete();
				f.createNewFile();
				FileWriter fw = new FileWriter(f);
				for (int gIdx : groups.get(i))
				{
					if (interrupted) break;
	
					rs = pool.executeQuery("select name,fullname from groups where idx=" + gIdx);
					rs.first();
					String grpFullName = rs.getString("fullname");
					fw.write(">" + grpFullName + "\n");

					rs = pool.executeQuery("select seq from pseudo_seq2 join groups on groups.idx=pseudo_seq2.grp_idx " + 
							" where grp_idx=" + gIdx + " order by chunk asc");
					int cNum = 0;
					while (!interrupted && rs.next())
					{
						if (interrupted) break;
						int start = cNum*CHUNK_SIZE + 1; // use 1-indexed string positions for comparing to annot
						int end = (1+cNum)*CHUNK_SIZE;
						String seq = rs.getString("seq");
						if (mask)
						{
							if (geneMap.containsKey(gIdx))
							{
								StringBuffer seqMask = new StringBuffer(seq.replaceAll(".", "N"));
								int cs = (int)Math.floor(start/cSize);
								int ce = (int)Math.ceil(end/cSize);
								TreeMap<Integer,Vector<Range>> map = geneMap.get(gIdx);
								for (int c = cs; c <= ce; c++) // check the bins covered by this chunk
								{
									if (map.containsKey(c))
									{
										for (Range r : map.get(c))
										{
											int olapS = (int)Math.max(start, r.s);
											int olapE = (int)Math.min(end, r.e);
											if (olapE > olapS)
											{
												olapS -= start; // get the 0-indexed relative coords within this chunk
												olapE -= start;
												seqMask.replace(olapS, olapE, seq.substring(olapS, olapE));
											}
										}
									}
									else
									{
										//System.out.println("No entries in bin " + c + " for " + gIdx );
									}
								}
								seq = seqMask.toString();
							}				
						}
						for (int j = 0; j < seq.length(); j += 50)
						{
							int endw = Math.min(j+50,seq.length());
							fw.write(seq.substring(j, endw));
							fw.write("\n");
						}
						cNum++;
					}
				}
				fw.close();
			}
		}
		catch (Exception e)
		{
			Utils.clearDir(dir);
			e.printStackTrace();
			System.exit(0);
		}
	}
	private void buildAlignments2() throws Exception 
	{
		log.msg("\nAligning " + proj1Name + " and " + proj2Name);
		mMainProps.printNonDefaulted(log);
		
		boolean isSelf = (proj1Type == proj2Type && proj1Name.equals(proj2Name));
		String pairName = proj1Name + "_to_" + proj2Name;
		String typeDir = "data/" + proj1Type.toString() + "_" + proj2Type.toString();
		pairDir =  typeDir + "/" + pairName;
		String seqDir1 = "data/" + proj1Type.toString() + "/" + proj1Name + "/sequence";
		String seqDir2 = "data/" + proj2Type.toString() + "/" + proj2Name + "/sequence";
 
		// Make sure required paths/files exist
		Utilities.checkCreateDir(typeDir);
		Utilities.checkCreateDir(seqDir1);
		createPairDir(pairDir, proj1Type, isSelf);

		// Get the possible source/destination file combinations for blast/mummer
		Vector<FileType> srcList1 = buildSrcList(proj1Type,proj1Name,seqDir1,false);
		Vector<FileType> srcList2 = buildSrcList(proj2Type,proj2Name,seqDir2,false);

		Utils.setProjProp(proj1Idx,"concatenated","0",pool);

		if (Utils.checkDoneFile(pairDir))
		{
			log.msg("Alignment has already been done.");
			log.msg("If not correct, remove " + pairDir + " and re-align.");
			return;
		}	
		TreeSet<String> src1Seen = new TreeSet<String>();
		TreeSet<String> src2Seen = new TreeSet<String>();
		for (FileType t1 : srcList1)
		{
			String src1 = t1.toString().toLowerCase();
			String srcdir1 = seqDir1 + "/" + src1;
			Utilities.checkCreateDir(srcdir1);
			File d1 = new File(srcdir1);
			if ( !d1.exists() ) {
				log.msg("Not found: " + srcdir1);
				continue;
			}

			if (t1 == FileType.Pseudo ) 
			{
				//boolean preProc1 = checkWritePreproc(t1,srcdir1,proj1Idx,proj1Name,pool,true);
				File dp = new File(d1,PREPROC_DIR);
				if (!dp.exists()) dp.mkdir();
				d1 = dp;
				if (isSelf)
				{
					dp = new File(d1,CONCAT_DIR);
					if (!dp.exists()) dp.mkdir();
					d1 = dp;
				}
			}
			else if (t1 == FileType.Bes || t1 == FileType.Mrk)
			{
				File dp = new File(d1,PREPROC_DIR);
				if (!dp.exists()) dp.mkdir();
				d1 = dp;
	
			}

			for (FileType t2 : srcList2)
			{
				String src2 = t2.toString().toLowerCase();	
				String srcdir2 = seqDir2 + "/" + src2;
				File d2 = new File(srcdir2);
				if ( !d2.exists() ) {
					log.msg("Not found: " + srcdir2);
					continue;
				}
				if (t2 == FileType.Pseudo ) 
				{
					String ppdir = PREPROC_DIR;
					if (isSelf) ppdir += "2";
					File dp2 = new File(d2,ppdir);
					if (!dp2.exists()) dp2.mkdir();
					d2 = dp2;		
				}
				
				String destpair = src1 + "_" + src2;
				String resdir = pairDir + "/anchors/" + destpair;				
				
				Utilities.checkCreateDir(resdir);
				
				ProgType pType = ( (t1 == FileType.Pseudo && t2 == FileType.Pseudo) ? ProgType.mummer : ProgType.blat );
				File rDir = new File(resdir);
				int nExist = rDir.listFiles().length;
				boolean bReplaceExisting = true;

				if (nExist > 0 )
				{
					if (askReuseExisting)
					{
						int ret = JOptionPane.showConfirmDialog(null, "Some " + pType.toString() + " alignments were previously completed. Do you want to use those?" + 
								"\n(Choose 'Yes' only if you are sure the alignments are for the same sequences currently loaded.)");	
						if (ret == JOptionPane.YES_OPTION)
						{
							bReplaceExisting = false;
						}
						else if (ret == JOptionPane.CANCEL_OPTION)
						{
							mCancelled = true;
							Cancelled.cancel();
							System.out.println("Cancelling");
							return;	
						}
						else if (ret == JOptionPane.NO_OPTION)
						{
							;	
						}
					}
				}
				
				// if they said don't use previous, then delete all the previous
				if (bReplaceExisting) {
					Utilities.clearDir(resdir);
					nExist = 0;
				}
				// Do the preprocessing
				if (t1 == FileType.Pseudo)
				{
					if (!src1Seen.contains(src1)) // maybe not necessary
					{
						if (!checkWritePreproc(d1,proj1Idx,proj1Name,pool,true, false))
						{
							mCancelled = true;
							Cancelled.cancel();
							System.out.println("Cancelling");
							return;								
						}
						src1Seen.add(src1);
					}
				}
				else if (t1 == FileType.Bes || t1 == FileType.Mrk)
				{
					if (!src1Seen.contains(src1)) // maybe not necessary
					{
						WritePreprocFPC(t1,d1,proj1Idx,proj1Name,pool); 
						src1Seen.add(src1);
					}
				}
				if (t2 == FileType.Pseudo)
				{
					if (!src2Seen.contains(src2))
					{
						// If it's a self alignment, we're not going to do any grouping on these preproc2 files
						// because we will run the chr self alignments one by one
						if (!checkWritePreproc(d2,proj2Idx,proj2Name,pool,false, isSelf))
						{
							mCancelled = true;
							Cancelled.cancel();
							System.out.println("Cancelling");
							return;															
						}
						src2Seen.add(src2);
					}
				}
				
				for (File f1 : d1.listFiles(new IsFileFilter())) 
				{
					for (File f2 : d2.listFiles(new IsFileFilter())) 
					{
						String program = (t1 != FileType.Pseudo ? "blat" : (isSelf ? "nucmer" : "promer"));
						if (program.equals("promer") && mMainProps.getProperty("nucmer_only").equals("1"))
						{
							program = "nucmer";
						}
						else if (program.equals("nucmer") && mMainProps.getProperty("promer_only").equals("1"))
						{
							program = "promer";
						}
						String args = getProgramArgs(program,mMainProps);
						ProgSpec ps = new ProgSpec(pType,program,getPlatformPath(program),args,t1,t2,f1,f2,resdir);
						if (!bReplaceExisting && ps.isDone()) continue;
						allAlignments.add(ps);
						ps.setStatus(ProgSpec.STATUS_QUEUED);
						toDoList.add(ps);
					}
				}
				if (isSelf)
				{
					resdir = pairDir + "/anchors/" + destpair + "_self";
					Utilities.checkCreateDir(resdir);
					for (File f2 : d2.listFiles(new IsFileFilter())) 
					{
						String program = "nucmer" ;
						if (program.equals("promer") && mMainProps.getProperty("nucmer_only").equals("1"))
						{
							program = "nucmer";
						}
						else if (program.equals("nucmer") && mMainProps.getProperty("promer_only").equals("1"))
						{
							program = "promer";
						}
						String args = getProgramArgs(program,mMainProps);
						if (!args.contains("maxmatch"))
						{
							args += " -maxmatch ";
						}
						ProgSpec ps = new ProgSpec(pType,program,getPlatformPath(program),args,t2,t2,f2,f2,resdir);
						if (!bReplaceExisting && ps.isDone()) continue;
						allAlignments.add(ps);
						ps.setStatus(ProgSpec.STATUS_QUEUED);
						toDoList2.add(ps);
					}
				}
			}
		}
		
		if ( allAlignments.size() == 0  && !Cancelled.isCancelled()) {
			log.msg("ERROR: no alignments between projects");
			error = true;
		}
	}
	public String getProgramArgs(String prog, Properties props)
	{
		String prop = prog + "_args";
		if (props.containsKey(prop))
		{
			return props.getProperty(prop);
		}
		if (prog.equals("blat"))
		{
			return "-minScore=30 -minIdentity=70 -tileSize=10 -maxIntron=10000";
		}
		return "";
	}
	public int dirNumFiles(File d)
	{
		int numFiles = 0;
		for (File f : d.listFiles(new IsFileFilter()))
		{
			numFiles++;	
		}
		return numFiles;
	}
	public String getStatusSummary() {
		String s = "";
		
		for (ProgSpec p : allAlignments)
			if ( p.isRunning() || p.isError() )
				s += p.toString() + "\n";
		
		return s;
	}
	
	public int getNumRunning() {
		synchronized(threads) {
			return threads.size();
		}
	}
	
	public int getNumRemaining() {
		synchronized(threads) {
			return toDoList.size() + toDoList2.size() + getNumErrors();
		}
	}
	
	public int getNumCompleted() {
		return allAlignments.size() - getNumRunning() - getNumRemaining();
	}
	
	public int getNumErrors() {
		int count = 0;
		
		for (ProgSpec p : allAlignments)
			if (p.isError())
				count++;
		
		return count;
	}
	
	public boolean notStarted()
	{
		return notStarted;
	}
	public boolean run() throws Exception 
	{
		long startTime = System.currentTimeMillis();

		System.gc(); // free unused heap for blat/mummer to use (Java treats this as a suggestion)
		
		if (error) // error occurred in constructor
			return false;
		pool.updateSchemaTo40();
		buildAlignments2();
		if (mCancelled) return false;
		
		if (toDoList.size() > 0)
		{
			String program = toDoList.peek().program.toUpperCase();
			log.msg("\nRunning " + program + ": " + (toDoList.size() + toDoList2.size()) +" alignments to perform, using up to "
					+ nMaxThreads + " threads");
		}
		
		int alignNum = 0;
		while (true) {
			synchronized(threads) {
				if (toDoList.size()==0 && threads.size() == 0)
					break;
				
				while (toDoList.size()>0 && threads.size() < nMaxThreads) {
					// Start another alignment thread
					final ProgSpec p = toDoList.remove();
					alignNum++;
					p.alignNum = alignNum;
					Thread newThread = new Thread() {
						public void run() {
							try {
								p.doAlignment();
							}
							catch (Exception e) {
								p.setStatus(ProgSpec.STATUS_ERROR);
								e.printStackTrace();
							}
							synchronized(threads) { threads.remove( this ); }
						}
					};
					
					threads.add( newThread );
					newThread.start();
					notStarted=false;
				}
			}
			
			Utilities.sleep(1000); // free processor
		}
		
		// Do self-alignments, if any
		if (toDoList2.size() > 0)
		{
			int nThreads = nMaxThreads;
			log.msg("\nStarting " + toDoList2.size() + " chromosome self-alignments, using nucmer -maxmatch with up to "
					+ nThreads + " threads");
			alignNum = 0;
			while (true) {
				synchronized(threads) {
					if (toDoList2.size()==0 && threads.size() == 0)
						break;
					
					while (toDoList2.size()>0 && threads.size() < nThreads) {
						// Start another alignment thread
						final ProgSpec p = toDoList2.remove();
						alignNum++;
						p.alignNum = alignNum;
						Thread newThread = new Thread() {
							public void run() {
								try {
									p.doAlignment();
								}
								catch (Exception e) {
									p.setStatus(ProgSpec.STATUS_ERROR);
									e.printStackTrace();
								}
								synchronized(threads) { threads.remove( this ); }
							}
						};
						
						threads.add( newThread );
						newThread.start();
						notStarted=false;
					}
				}
				
				Utilities.sleep(1000); // free processor
			}			
		}
			
		for (File dir : preprocDirs)
		{
			if (dir.isDirectory())
			{
				Utils.clearDir(dir);
				dir.delete();
			}
		}
		// CAS 1/7/18 this was commented out - why?
		log.msg("Done:  " + Utilities.getDurationString(System.currentTimeMillis()-startTime) + "\n");
		if (getNumErrors() == 0)
		{
			if (!mCancelled)
			{
				writeDoneFile(pairDir);
			}
		}
		else
		{
			log.msg("Errors occurred in the alignment.");
			log.msg("Try specifying your platform (32/64 bit and linux/mac) on the symap command line.");
			log.msg("Type ./symap -h to see the options.");
		}
		
		return (getNumErrors() == 0);
	}
	
	public void interrupt() {
		interrupted = true;
		mCancelled = true;
		synchronized(threads) {
			for (ProgSpec p : allAlignments)
				if (p.isRunning())
					p.interrupt();
			
			toDoList.clear();
			
			for (Thread t : threads)
				t.interrupt();
			threads.removeAllElements();
		}
	}

	// create or fill out the pair directory structure
	private static void createPairDir(String mPairDir, ProjType mType1, boolean isSelf)
	{
		String mAnchorDir = mPairDir + "/anchors";
		
		Utilities.checkCreateDir(mPairDir);
		Utilities.checkCreateFile(mPairDir + "/params");
		Utilities.checkCreateDir(mAnchorDir);
		if (mType1 == ProjType.fpc)
		{
			Utilities.checkCreateDir(mAnchorDir + "/bes_pseudo");
			Utilities.checkCreateDir(mAnchorDir + "/mrk_pseudo");
		}
		else if (mType1 == ProjType.pseudo)
		{
			Utilities.checkCreateDir(mAnchorDir + "/pseudo_pseudo");
		}
	}

	private Vector<FileType> buildSrcList(ProjType type, String projname, String seqdir, boolean mUseExtractedGenes) throws Exception
	{
		Vector<FileType> ret = new Vector<FileType>();
		
		if (type == ProjType.fpc)
		{
			ret.add(FileType.Bes);
			ret.add(FileType.Mrk);
		}
		else if (type == ProjType.pseudo)
		{
			ret.add(FileType.Pseudo);
		}
		if (type == ProjType.pseudo && ret.size() > 1)
		{
			// obviously can't happen but let's assert it
			throw(new Exception("multiple directories for pseudo"));
		}
		return ret;
	}
	public String getPlatformPath(String program) 
	{
		if (Utilities.isLinux()) 
		{
			String plat = (mMainProps.containsKey("platform") ? mMainProps.getProperty("platform") : "");
			if (program.equals("blat"))
			{
				return "/lintel/"; // no 64b blat version
			}
			
			if (plat.equals("i386"))
			{
				return "/lintel/";
			}
			else if (plat.equals("x86_64"))
			{
				return "/lintel64/";
			}	
			else if (Utilities.is64Bit() ) 
			{
				return "/lintel64/";
			}
			else
			{
				return "/lintel/";
			}
		}
		else if (Utilities.isWindows())
		{
			return "/wintel/";
		}
		else if (Utilities.isMac())
		{
			return "/mac/";
		}
		else 
		{
			System.err.println("Unknown platform!");
			return "";
		}
	}	
	private void writeDoneFile(String dir) throws Exception
	{
		File f = new File(dir);
		File d = new File(f,"all.done");
		d.createNewFile();
	}
}