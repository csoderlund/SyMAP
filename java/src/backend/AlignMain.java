package backend;

/*******************************************************
 * Run blat/mummer for alignments
 * CAS500 1/2020 this has been almost totally rewritten, but results are the same,
 * 	except there is intelligence built into setting up the files for alignment,
 *  which can make a difference
 */
import java.io.File;
import java.io.FileWriter;
import java.util.Properties;
import java.util.Vector;
import java.util.TreeMap;
import java.util.Queue;
import java.util.LinkedList;

import java.sql.ResultSet;

import symap.projectmanager.common.ProjectManagerFrameCommon;
import util.Cancelled;
import util.ErrorReport;
import util.Logger;
import util.Utilities;

public class AlignMain 
{
	public boolean mCancelled = false;
	
	private static final int CHUNK_SIZE = Constants.CHUNK_SIZE; 
	private static final int maxFileSize = 70000000; // for project with separate files
	
	private Logger log;
	private String proj1Name, proj2Name;
	private ProjType proj1Type, proj2Type;
	private int proj1Idx, proj2Idx;
	
	private String alignLogDirName;
	private Vector<ProgSpec> allAlignments;
	private Queue<ProgSpec> toDoList;
	private Vector<Thread> threads;
	private int nMaxThreads;
	private boolean error = false;
	
	private SyProps mMainProps = null;
	private UpdatePool pool = null;
	
	private boolean notStarted = true;
	private boolean interrupted = false;
	
	private String resultDir;
	
	public AlignMain(UpdatePool _pool, Logger log, 
			String proj1Name, String proj2Name,
			int numProcessors, Properties props, 
			SyProps pairProps, String alignLogDirName)
	{
		this.log = log;
		allAlignments = new Vector<ProgSpec>();
		toDoList = new LinkedList<ProgSpec>();

		threads = new Vector<Thread>();
		nMaxThreads = numProcessors;
		assert(pairProps != null);
		mMainProps = pairProps;
		
		pool = _pool;
		
		this.proj1Name = proj1Name;
		this.proj2Name = proj2Name;
		this.alignLogDirName = alignLogDirName;
			
		try {
			this.proj1Type = pool.getProjType(proj1Name);
			this.proj2Type = pool.getProjType(proj2Name);
			proj1Idx = pool.getProjIdx(proj1Name,proj1Type);
			proj2Idx = pool.getProjIdx(proj2Name,proj2Type);	
		}
		catch (Exception e) { ErrorReport.print(e, "Align Main");}
	}
	public boolean run() throws Exception 
	{
		long startTime = System.currentTimeMillis();

		System.gc(); // free unused heap for blat/mummer to use (Java treats this as a suggestion)
		
		if (error) return false; // error occurred in constructor
			
		//pool.updateSchemaTo40();
		
		if (alignExists()) return true; // must have all.done and at least one .mum file
		
		buildAlignments();
		
		if (mCancelled) return false;
		
		if (toDoList.size() > 0)
		{
			String program = toDoList.peek().program.toUpperCase();
			log.msg("\nRunning " + program + ": " + toDoList.size() +" alignments to perform, using up to "
					+ nMaxThreads + " CPUs");
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
		
		Utils.timeMsg(log, startTime, "Align");
		
		if (getNumErrors() == 0)
		{
			if (!mCancelled) {
				Utils.writeDoneFile(resultDir + Constants.alignDir);
				
				// CAS500 only delete tmp files if successful
				String tmpDir = Constants.getNameTmpDir(proj1Name, (proj1Type==ProjType.fpc), proj2Name);
				if (Constants.PRT_STATS) {
					System.out.println("Do not delete " + tmpDir);
				}
				else {
					File dir = new File(tmpDir);
					Utilities.clearAllDir(dir);
					dir.delete();
				}
			}
		}
		else
		{
			log.msg("Errors occurred in the alignment. See TroubleShoot.html.");
		}
		
		return (getNumErrors() == 0);
	}
	/****************************************************
	 * Create preprocessed files and run alignment
	 */
	private void buildAlignments() throws Exception 
	{
		log.msg("\nAligning " + proj1Name + " and " + proj2Name);
		Utils.setProjProp(proj1Idx,"concatenated","0",pool);
		
	/* create directories */
		
		// result directory (e.g. data/seq_results/demo1_to_demo2)
		Utilities.checkCreateDir(resultDir, "AM dir");
				
		// temporary directories to put data for alignment
		boolean isFPC = (proj1Type==ProjType.fpc);
		String tmpDir =  Constants.getNameTmpDir(proj1Name, isFPC, proj2Name);
		Utilities.checkCreateDir(tmpDir, "AM tmp");
		
		boolean isSelf = (proj1Type == proj2Type && proj1Name.equals(proj2Name));
		String tmpDir1 = Constants.getNameTmpPreDir(proj1Name, isFPC, proj2Name, proj1Name);
		String tmpDir2 = (isSelf) ? tmpDir1 :
			Constants.getNameTmpPreDir(proj1Name, isFPC, proj2Name, proj2Name);
		File fh_tDir1 = Utilities.checkCreateDir(tmpDir1, "AM tmp1");
		File fh_tDir2 = Utilities.checkCreateDir(tmpDir2, "AM tmp2");
		
	// Preprocessing for proj1
		
		if (isFPC) {
			writePreprocFPC(fh_tDir1, proj1Idx, proj1Name, pool, true); // isBes
			writePreprocFPC(fh_tDir1, proj1Idx, proj1Name, pool, false); // !isBes
		}
		else {
			if (!writePreprocSeq(fh_tDir1, proj1Idx, proj1Name, pool, true, isSelf)) // concat=true
			{
				mCancelled = true;
				Cancelled.cancel();
				System.out.println("Cancelling");
				return;								
			}
		}
		
	// Preprocessing for proj2	
		
		if (!writePreprocSeq(fh_tDir2, proj2Idx, proj2Name, pool, false, isSelf)) // concat=false
		{
			mCancelled = true;
			Cancelled.cancel();
			System.out.println("Cancelling");
			return;															
		}
		
		/** Assign values for Alignment **/
		
		ProgType pType = (isFPC) ? ProgType.blat : ProgType.mummer;
		
		String program = "promer";
		if (isFPC) program = "blat";
		else if (isSelf) program = "nucmer";
		
		// user over-rides
		if (program.equals("promer") && mMainProps.getProperty("nucmer_only").equals("1"))
			program = "nucmer";
		else if (program.equals("nucmer") && mMainProps.getProperty("promer_only").equals("1"))
			program = "promer";
		
		String args = 	getProgramArgs(program, mMainProps);
		String platform = Constants.getPlatformPath();
		
	/** create list of comparisons to run **/
		// under runDir/<p1>_to_<p2>/tmp/<px>/ with file names
		// files in <p1> are aligned with files in <p2>. 
		// For self, p1=p2
		String alignDir = resultDir + Constants.alignDir;
		Utilities.checkCreateDir(alignDir, "AM align dir");
		
		for (File f1 : fh_tDir1.listFiles()) {    
			if (!f1.isFile() || f1.isHidden()) continue; 
			
			for (File f2 : fh_tDir2.listFiles()) {
				if (!f2.isFile() || f2.isHidden()) continue; 
				
				// XXX
				String aArgs = args;
				if (isSelf) { // chr files have been written as is
					if (f1.getName().equals(f2.getName())) {
						// CAS501 --maxmatch was hardcoded here, but takes forever, give user choice
						String self = mMainProps.getProperty("self_args");
						aArgs += self;
					}
					else if (f1.getName().compareTo(f2.getName()) < 0) continue; 
				}
				ProgSpec ps = new ProgSpec(pType,program, platform,aArgs, f1, f2, alignDir, alignLogDirName);
				if (ps.isDone()) continue;
				
				allAlignments.add(ps);
				ps.setStatus(ProgSpec.STATUS_QUEUED);
				toDoList.add(ps);
			}
		}
		if ( allAlignments.size() == 0  && !Cancelled.isCancelled()) {
			log.msg("Warning: no alignments between projects");
			error = true;
		}
	}
	// BES/MRK write sequences to one file
	private boolean writePreprocFPC(File dir, int pidx, String projName, UpdatePool db, boolean isBes) throws Exception
	{
		try {
			if (Cancelled.isCancelled()) return false;
			
			String fName = (isBes) ? Constants.besType : Constants.mrkType;
			fName = fName + "." + projName + Constants.faFile;
		
			File f = Utilities.checkCreateFile(dir, fName, "AM create file to write");
			FileWriter fw = new FileWriter(f);
	
			String query = "";
			if (isBes)
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
				String name = rs.getString(1);
				String seq = rs.getString(2);
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
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Write preprocessed FPC files"); return false;}
	}
	
	// Write sequences to file
	private boolean writePreprocSeq(File dir, int projIdx, String projName, UpdatePool pool, 
			boolean concat, boolean isSelf) throws Exception
	{
		if (concat && isSelf) return true; // all get written as originals
		
		if (Cancelled.isCancelled()) return false;
		
		ResultSet rs = pool.executeQuery("select count(*) as nseqs from pseudos " +
			" join xgroups on xgroups.idx=pseudos.grp_idx " +
			" where xgroups.proj_idx=" + projIdx);
		rs.first();
		int nSeqs = rs.getInt("nseqs");
		if (nSeqs == 0)
		{
			log.msg("No sequences are loaded for " + projName + "!!");
			return false;
		}
		
		String gmprop = Utils.getProjProp(projIdx, "mask_all_but_genes", pool);
		boolean geneMask = (gmprop != null && gmprop.equals("1"));	
		if (geneMask) log.msg(projName + ": Masking non-genic sequence");
	
		if (Utilities.dirNumFiles(dir) > 0)
			Utilities.clearAllDir(dir); // leaves directory, just removes files
		
		try
		{
			Vector<Vector<Integer>> groups = new Vector<Vector<Integer>>();
		
			if (concat) log.msg(projName + ": Concatenating all sequences into one file for query");
			else  log.msg(projName +       ": Writing sequences into one or more files for target");
			
			// pseudos (files): grp_idx, fileName (e.g. chr3.seq), length
			// groups  (chrs):  idx, proj_idx, name, fullname where grp_idx is xgroups.idx
			rs = pool.executeQuery("select grp_idx, length from pseudos " +
					" join xgroups on xgroups.idx=pseudos.grp_idx " +
					" where xgroups.proj_idx=" + projIdx + " order by xgroups.sort_order");
			groups.add(new Vector<Integer>());
			long curSize = 0;
			int nOrigGrp = 0, nGrp = 0;
			while (!interrupted && rs.next())
			{
				nOrigGrp++;
				int grp_idx = rs.getInt(1);
				long length = rs.getLong(2);
				
				if (!isSelf)
				{
					// Unless concat=true, group up to total seq length maxFileSize.
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
					// each group only has one group in it (written like the original file)
					groups.get(nGrp).add(grp_idx);
					nGrp++;
					groups.add(new Vector<Integer>());
					curSize += length;
				}
			}
	
			TreeMap<Integer,TreeMap<Integer,Vector<Range>>> geneMap = new TreeMap<Integer,TreeMap<Integer,Vector<Range>>>();
			int cSize = 100000;
	
			if (geneMask)
			{
				// Build a map of the gene annotations that we can use to mask each sequence chunk as we get it.
				// The map is sorted into 100kb bins for faster searching. 
				rs = pool.executeQuery("select xgroups.idx, pseudo_annot.start, pseudo_annot.end " +
						" from pseudo_annot join xgroups on xgroups.idx=pseudo_annot.grp_idx " +
						" where pseudo_annot.type='gene' and xgroups.proj_idx=" + projIdx);
				while (!interrupted && rs.next())
				{
					int grpIdx = rs.getInt(1);
					int start = rs.getInt(2);
					int end = rs.getInt(3);
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
			} // end mask
			
			if (interrupted) return false;
			
		/** Write files **/
			// Now go through each grouping, write the preprocess file, with masking if called for.
			// Note that sequences are stored in chunks of size CHUNK_SIZE.
			
			for (int i = 0; i < groups.size(); i++)
			{
				if (groups.get(i).size() == 0) break; // last one can come up empty
				
				String fileName = (concat) ? ("_cc") : ("_f" + (i+1)); 
				
				fileName = projName + fileName + Constants.faFile;
				String msg = "   " + fileName + ": ";
				int fileSize = 0, count=0;
				
				File f = Utilities.checkCreateFile(dir,fileName, "AM WritePreprocSeq");
				FileWriter fw = new FileWriter(f);
				
				for (int gIdx : groups.get(i))
				{
					if (interrupted) break;
	
					rs = pool.executeQuery("select name,fullname from xgroups where idx=" + gIdx);
					rs.first();
					String grpFullName = rs.getString("fullname");
					fw.write(">" + grpFullName + "\n");
					
					if (count==0) 		msg += grpFullName;
					else if (count<4) 	msg += ", " + grpFullName;
					else if (count==4)  msg += "... ";
					count++;
					
					rs = pool.executeQuery("select seq from pseudo_seq2 join xgroups on xgroups.idx=pseudo_seq2.grp_idx " + 
							" where grp_idx=" + gIdx + " order by chunk asc");
					int cNum = 0;
					while (!interrupted && rs.next())
					{
						if (interrupted) break;
						int start = cNum*CHUNK_SIZE + 1; // use 1-indexed string positions for comparing to annot
						int end = (1+cNum)*CHUNK_SIZE;
						String seq = rs.getString("seq");
						if (geneMask)
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
									else { } //System.out.println("No entries in bin " + c + " for " + gIdx );
								}
								seq = seqMask.toString();
							}				
						}
						for (int j = 0; j < seq.length(); j += 50)
						{
							int endw = Math.min(j+50,seq.length());
							String x = seq.substring(j, endw);
							fileSize += x.length();
							fw.write(x);
							fw.write("\n");
						}
						cNum++;
					}
				}
				log.msg(msg + ": length " + fileSize);
				fw.close();
			}
			return true;
		}
		catch (Exception e)
		{
			Utilities.clearAllDir(dir);
			dir.delete();
			ErrorReport.die(e, "AlignMain.writePreproc");
		}
		return false;
	}
	
	private boolean alignExists() {
		try {
			boolean isFPC = (proj1Type==ProjType.fpc);
			resultDir = Constants.getNameResultsDir(proj1Name, isFPC, proj2Name);
			
			String alignDir = Constants.getNameResultsDir(proj1Name, isFPC, proj2Name);
			alignDir += Constants.alignDir;
			File f = new File(alignDir);
			if (!f.exists()) return false;
			
			boolean done = Utils.checkDoneFile(alignDir);
			int n = Utils.checkDoneMaybe(alignDir, isFPC);
			if (done && n>0) {
				log.msg("Warning: " + n + " alignment files exist - using existing files ...");
				log.msg("   If not correct, remove " + resultDir + " and re-align.");
				return true;
			}			
			if (n>0) 
				log.msg("WARNING: No 'align/all.done' file, alignment may have ended before done.");

			return false;
		}
		catch (Exception e) {ErrorReport.print(e, "Trying to see if alignment exists");}
		return false;
	}
	private String getProgramArgs(String prog, Properties props)
	{
		String prop = prog + "_args";
		if (props.containsKey(prop))
		{
			return props.getProperty(prop);
		}
		if (prog.equals("blat")) // TODO why not getting blat_args prop?
		{
			return "-minScore=30 -minIdentity=70 -tileSize=10 -maxIntron=10000";
		}
		return "";
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
			return toDoList.size() + getNumErrors();
		}
	}
	
	public int getNumCompleted() {
		return allAlignments.size() - getNumRunning() - getNumRemaining();
	}
	
	private int getNumErrors() {
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
	private class Range
	{
		int s;
		int e;
		Range(int start, int end)
		{
			s = start; e = end;
		}
	}
}