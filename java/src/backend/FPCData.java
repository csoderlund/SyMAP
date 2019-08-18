package backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;

import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.Cancelled;
import util.Logger;

/*
 * This class is used by both the FPC load script and the anchors/synteny scripts.
 * Some members are used in one case but not the other. 
 */

public class FPCData 
{
	int mProjIdx = 0;
	TreeMap<String,Integer> mClone2Ctg;
	Pattern mBESPat;
	UpdatePool mConn;
	String mName;
	int mCBSize = 1200;
	TreeMap<Integer, Vector<FPCContig>> mCtgs;
	Project mProj;
	int pidx;
	String mGrpPrefix;
	String mSeqDir;
	Vector<FPCContig> mCtgList;
	TreeMap<Integer, Vector<FPCClone>> mCtg2Clones;
	TreeMap<String,GroupInt> mGrpName2GrpObj;
	GroupSorter mGS;
	TreeMap<String,Integer> mCloneRemCounts; // also reused to store the DB index of the remarks
	TreeMap<String,TreeSet<RF>> mClone2BES;
	TreeMap<String,Integer> mCloneName2Idx;
	Vector<Marker> mMarkers;
	TreeMap<String,String> mClone2Mrk;
	TreeMap<String,Integer> mMrk2Idx;
	TreeMap<String,TreeSet<Integer>> mMrk2Ctg;
	Vector<File> besFiles = null;
	Vector<File> mrkFiles = null;
	int mMaxCtgs;
	Logger mLog;
	
	// Constructor when called by anchors/synteny scripts
	public FPCData(UpdatePool pool, Logger log, int projIdx, String name, Project proj) throws SQLException
	{
		mLog = log;
		mConn = pool;
		mProjIdx = projIdx;
		mProj = proj;
		mName = name;
		mBESPat = Pattern.compile("(\\S+)(r|f)",Pattern.CASE_INSENSITIVE);
		mClone2Ctg = new TreeMap<String,Integer>();
		try
		{
			mCBSize = Integer.parseInt(Utils.getProjProp(mProjIdx,"cbsize",mConn));
		}
		catch (Exception e)
		{
			mLog.msg("Invalid cbsize parameter, using default " + mCBSize);
		}
		mCtgs = new TreeMap<Integer, Vector<FPCContig>>();
		loadData();
	}
	
	// Constructor when called for loading FPC project to database
	public FPCData(UpdatePool pool, Logger log, File f, int pidx, String prefix, 
			GroupSorter gs, String seqdir, int maxCtgs, 
			Vector<File> besFiles, Vector<File> mrkFiles) throws Exception
	{
		mLog = log;
		mConn = pool;
		mCtgList = new Vector<FPCContig>();
		mCtg2Clones = new TreeMap<Integer, Vector<FPCClone>> ();
		mGrpName2GrpObj = new TreeMap<String,GroupInt>();
		mCloneRemCounts = new TreeMap<String,Integer>();
		mMarkers = new Vector<Marker>();
		mClone2Mrk = new TreeMap<String,String>();
		mMrk2Idx = new TreeMap<String,Integer>();
		mCloneName2Idx = new TreeMap<String,Integer>();
		mMrk2Ctg = new TreeMap<String,TreeSet<Integer>>();
		mMaxCtgs = maxCtgs;
		
		this.besFiles = besFiles;
		this.mrkFiles = mrkFiles;
		
		mProjIdx = pidx;
		mSeqDir = seqdir;
		mGrpPrefix = prefix;
		mGS = gs;
		mBESPat = Pattern.compile("(\\S+)(r|f)",Pattern.CASE_INSENSITIVE);
		mClone2BES = new TreeMap<String,TreeSet<RF>>();
		
		parseFPCFile(f);	
	}

	public boolean parseBES(String bes, Hit hit)
	{
		bes = bes.replace(".","");
		Matcher m = mBESPat.matcher(bes);
		if (m.matches())
		{
			hit.clone = m.group(1);
			String rfstr = m.group(2).toLowerCase();
			hit.rf = (rfstr.equals("r") ? RF.R : RF.F);
			return true;
		}
		
		return false;
	}

	private void loadData() throws SQLException
	{
		int anchoredCtgs = 0;
		int totalCtgs = 0;
		for (Group grp : mProj.getGroups() )
		{
			Integer grpIdx = grp.getIdx();
			mCtgs.put(grpIdx,new Vector<FPCContig>());
			String stmtStr = "SELECT idx,number,grp_idx,anchor_pos, size, ccb " +
				" FROM contigs WHERE proj_idx ='" + mProjIdx + "' and  grp_idx='" + grpIdx + "' and number > 0 order by ccb asc";
			ResultSet rs = mConn.executeQuery(stmtStr);
			while (rs.next())
			{
				mCtgs.get(grpIdx).add(ctgObjFromRS(rs,grp));
				anchoredCtgs++;
				totalCtgs++;
			}
			rs.close();
		}
		
		Integer unGrpIdx = mProj.getUnanchoredGrpIdx();
		mCtgs.put(unGrpIdx,new Vector<FPCContig>());
		String stmtStr = "SELECT idx,number,grp_idx,anchor_pos, size, ccb " +
			" FROM contigs WHERE proj_idx ='" + mProjIdx + "' and  grp_idx='" + unGrpIdx + "' and number > 0";
		ResultSet rs = mConn.executeQuery(stmtStr);
		while (rs.next())
		{
			mCtgs.get(unGrpIdx).add(ctgObjFromRS(rs,null));	
			totalCtgs++;
		}
		rs.close();
		mLog.msg(mProj.getName() + ":" + totalCtgs + " contigs, " + anchoredCtgs + " anchored");
	}	
	
	private FPCContig ctgObjFromRS(ResultSet rs, Group grp) throws SQLException
	{
		return new FPCContig(rs.getInt("idx"),
						rs.getInt("number"),
						rs.getFloat("anchor_pos"),
						rs.getInt("size"),
						rs.getInt("ccb"),
						grp,
						mProj
		);
	}
	
	public int getTotalCBSize() throws SQLException
	{
		int size = 0;
		String stmtStr = "SELECT sum(size) as tsize FROM contigs WHERE proj_idx ='" + mProjIdx + "' and number > 0";
		ResultSet rs = mConn.executeQuery(stmtStr);
		if (rs.next())
			size = rs.getInt("tsize");
		rs.close();
		
		return size;		
	}
	
	public void setBlockContigs(Block blk)
	{
		Vector<String> list = new Vector<String>();
		
		for (FPCContig c : mCtgs.get(blk.mGrpIdx1))
			if (Utils.intervalsOverlap(blk.mS1, blk.mE1, c.mCCB, c.mCCB+c.mSize,0))
				list.add(String.valueOf(c.mIdx));
		blk.mCtgs1 = Utils.strVectorJoin(list, ",");
	}
	
	private void parseFPCFile(File f) throws Exception
	{
		Pattern pBAC = Pattern.compile("^(BAC|Clone).*\"(\\S+)\".*");
		Pattern pMRK = Pattern.compile("^Marker_(\\S+).*\"(\\S+)\".*");
		Pattern pCTG = Pattern.compile("^Ctg(\\d+).*");

		Pattern pMAPL = Pattern.compile("Map.*ctg(\\d+).*Left\\s+([\\d\\.]+).*");
		Pattern pMAPR = Pattern.compile("Map.*ctg(\\d+).*Right\\s+([\\d\\.]+).*");
		Pattern pRMK = Pattern.compile("Remark\\s+\"(.*)\".*");
		Pattern pM2C = Pattern.compile("Positive.*\"(\\S+)\".*");

		Pattern pCHRRMK = Pattern.compile(".*Chr_remark.*(Lg|Chr)([^\\s\"]+).*Pos\\s+(\\S+).*");
		Pattern pUSRRMK = Pattern.compile(".*User_remark.*\"(.*)\".*");
		
		Pattern pNONNULL = Pattern.compile(".*\\S.*");
		
		Matcher mBAC = pBAC.matcher("");
		Matcher mMRK = pMRK.matcher("");
		Matcher mCTG = pCTG.matcher("");
		Matcher mMAPL = pMAPL.matcher("");
		Matcher mMAPR = pMAPR.matcher("");
		Matcher mRMK = pRMK.matcher("");
		Matcher mM2C = pM2C.matcher("");
		Matcher mCHRRMK = pCHRRMK.matcher("");
		Matcher mUSRRMK = pUSRRMK.matcher("");
		
		BufferedReader fh = new BufferedReader( new FileReader(f));
		while (fh.ready())
		{
			String line = fh.readLine();
			
			mBAC.reset(line);				
			mMRK.reset(line);				
			mCTG.reset(line);				
			
			if (mBAC.matches())
			{
				String cloneName = mBAC.group(2);
				if (!mConn.isSQLSafe(cloneName))
					throw(new Exception("Invalid clone name " + cloneName + ":" + mConn.mBadStrMsg));

				int ctgNum = 0;
				int left = 0;
				int right = 0;
				Vector<String> remarks = new Vector<String>();
				Vector<String> markers = new Vector<String>();
				
				while (fh.ready())
				{
					line = fh.readLine();
					
					if (!pNONNULL.matcher(line).matches())
					{
						if (ctgNum != 0)
						{
							if (!mCtg2Clones.containsKey(ctgNum))
								mCtg2Clones.put(ctgNum, new Vector<FPCClone>());
							mCtg2Clones.get(ctgNum).add(new FPCClone(cloneName,ctgNum,left,right,remarks,markers));
						}
						break;
					}
					
					mMAPL.reset(line);
					mMAPR.reset(line);
					mRMK.reset(line);
					mM2C.reset(line);
					
					if (mMAPL.matches())
					{
						ctgNum = Integer.parseInt(mMAPL.group(1));
						left = (int)Math.round(Double.parseDouble(mMAPL.group(2)));							
					}
					else if (mMAPR.matches())
					{
						right = (int)Math.round(Double.parseDouble(mMAPR.group(2)));
					}
					else if (mRMK.matches())
					{
						String rmk = mConn.sqlSanitize(mRMK.group(1));	
						remarks.add(rmk);
						if (!mCloneRemCounts.containsKey(rmk))
							mCloneRemCounts.put(rmk, 1);
						else
							mCloneRemCounts.put(rmk,1 + mCloneRemCounts.get(rmk));
					}
					else if (mM2C.matches())
					{
						String mrk = mM2C.group(1);	
						if (!mConn.isSQLSafe(mrk))
							throw(new Exception("Invalid marker " + mrk + " attached to clone " + cloneName + " :" + mConn.mBadStrMsg));								
						markers.add(mrk);
						if (!mMrk2Ctg.containsKey(mrk))
							mMrk2Ctg.put(mrk, new TreeSet<Integer>());
						if (ctgNum > 0)
							mMrk2Ctg.get(mrk).add(ctgNum);
					}
					
				}
			}
			else if (mMRK.matches())
			{
				String mrkType = mMRK.group(1);
				String mrkName = mMRK.group(2);
				
				if (!mConn.isSQLSafe(mrkType))
					throw(new Exception("Invalid marker type " + mrkType + " for marker " + mrkName + " :" + mConn.mBadStrMsg));						
				if (!mConn.isSQLSafe(mrkName))
					throw(new Exception("Invalid marker name " + mrkName + mrkName + " :" + mConn.mBadStrMsg));						
				
				Marker mrk = new Marker(mrkName,mrkType);
				mMarkers.add(mrk);

				while (fh.ready())
				{
					line = fh.readLine();
					
					if (!pNONNULL.matcher(line).matches()) break;
					
					/* We don't do anything with marker remarks now */
				}
			}
			else if (mCTG.matches())
			{		
				FPCContig curCtg = null;
				
				int ctgNum = Integer.parseInt(mCTG.group(1));
				curCtg = new FPCContig(ctgNum,"0",0,"",mProjIdx);
				if (ctgNum == 0) continue;
				mCtgList.add(curCtg);
				
				while (fh.ready())
				{
					line = fh.readLine();
					mCTG.reset(line);				

					if (mCTG.matches())
					{
						ctgNum = Integer.parseInt(mCTG.group(1));
						curCtg = new FPCContig(ctgNum,"0",0,"",mProjIdx);	
						mCtgList.add(curCtg);
						continue;
					}
					
					mCHRRMK.reset(line);
					mUSRRMK.reset(line);
					
					if (mCHRRMK.matches())
					{
						curCtg.mGrpName = mCHRRMK.group(2);
						curCtg.mPos = Float.parseFloat(mCHRRMK.group(3));
						 
					}
					else if (mUSRRMK.matches())
					{
						curCtg.mRmk = mUSRRMK.group(1);
					}						
				}
			}	
	
		}
		fh.close();
	}

	// Get the groups from the contigs, sort, and upload them.
	private void uploadGroups() throws Exception
	{
		for (FPCContig ctg : mCtgList) {

			if (!mGrpName2GrpObj.containsKey(ctg.mGrpName))
				mGrpName2GrpObj.put(ctg.mGrpName, new GroupInt(ctg.mGrpName,mGrpPrefix,mProjIdx,ProjType.fpc));
		}
		
		Vector<String> sortedGrps = new Vector<String>(mGrpName2GrpObj.keySet());
		boolean allGood = true;
		for (String g : sortedGrps)
			allGood &= mGS.orderCheck(g);

		if (!allGood)
			throw(new Exception("Group order not defined"));

		Collections.sort(sortedGrps,mGS);
		for (String g : sortedGrps)
			mGrpName2GrpObj.get(g).addGrpToDB(mConn);
		
		// update the contig group indexes, now that we have them
		for (FPCContig ctg : mCtgList)
			ctg.mGrpIdx = mGrpName2GrpObj.get(ctg.mGrpName).getGrpIdx();

		mLog.msg(mGrpName2GrpObj.keySet().size() + " groups ");
	}
	
	public void doUpload() throws Exception
	{
		uploadGroups();
		Collections.sort(mCtgList,new CtgSort());
		ctgSizesAndCCB();
		
		for (FPCContig ctg : mCtgList)
		{
			if (Cancelled.isCancelled()) return;
			ctg.doUpload(mConn);
		}
		
		uploadCloneRemarks();
		if (Cancelled.isCancelled()) return;
		setCtgRemarkCounts();	
		loadBES();
		if (Cancelled.isCancelled()) return;
		loadClones();
		if (Cancelled.isCancelled()) return;
		getCloneIdxList();
		loadMarkers();
		if (Cancelled.isCancelled()) return;
		getMarkerIdxList();
		loadMarkerLocations();
		if (Cancelled.isCancelled()) return;
		loadMrkSeq();
		if (Cancelled.isCancelled()) return;
	}
	
	// go through contig by contig and load both the marker/clone hits
	// and the marker/contig positions (which must be calculated)
	private void loadMarkerLocations() throws Exception
	{
		mLog.msg("Load marker/clone associations");
		Vector<String> vals = new Vector<String>();
		vals.add(0,"");
		vals.add(1,"");

		Vector<String> vals2 = new Vector<String>();
		vals2.add(0,"");
		vals2.add(1,"");
		vals2.add(2,"");
		vals2.add(3,"");
		
		int nloc = 0;
		int nctgloc = 0;
		for (FPCContig ctg : mCtgList)
		{
			TreeMap<Integer,Integer> nMrkHits = new TreeMap<Integer,Integer>();
			TreeMap<Integer,Integer> mrkPos = new TreeMap<Integer,Integer>();
			for (FPCClone c : mCtg2Clones.get(ctg.mNum))
			{				
				vals.set(1,mCloneName2Idx.get(c.mName).toString());
				for (String mrk : c.mMrks)
				{
					if (!mMrk2Idx.containsKey(mrk)) continue; // we didn't add it because it hits too many ctgs
					Integer mrkIdx = mMrk2Idx.get(mrk);
					vals.set(0,mrkIdx.toString());
					mConn.bulkInsertAdd("mrk_clone", vals);
					nloc++;
					int pos = (c.mL + c.mR)/2;
					if (!nMrkHits.containsKey(mrkIdx))
					{
						nMrkHits.put(mrkIdx, 1);	
						mrkPos.put(mrkIdx,pos);
					}
					else
					{
						nMrkHits.put(mrkIdx, 1 + nMrkHits.get(mrkIdx));
						mrkPos.put(mrkIdx, pos + mrkPos.get(mrkIdx));
					}					
				}
			}
			vals2.set(1, String.valueOf(ctg.mIdx));
			for (Integer mrkIdx : nMrkHits.keySet())
			{
				vals2.set(0,mrkIdx.toString());
				Integer nHits = nMrkHits.get(mrkIdx);
				Integer pos = mrkPos.get(mrkIdx)/nHits;
				vals2.set(2,pos.toString());
				vals2.set(3,nHits.toString());
				mConn.bulkInsertAdd("mrk_ctg", vals2);
				nctgloc++;
			}
		}
		mLog.msg(nloc + " marker/clone associations loaded");
		mLog.msg(nctgloc + " marker/contig positions loaded");
		mConn.finishBulkInserts();
	}
	
	private void loadMarkers() throws Exception
	{
		mLog.msg("Load markers");
		Vector<String> vals = new Vector<String>();
		vals.add(0,Integer.toString(mProjIdx));
		vals.add(1,"");
		vals.add(2,"");
		vals.add(3,"");
		
		int nmrk = 0;
		for (Marker m : mMarkers)
		{			
			if (Cancelled.isCancelled()) break;
			
			if (!mMrk2Ctg.containsKey(m.mName) ) continue;
			if ( mMrk2Ctg.get(m.mName).size() > mMaxCtgs) continue;
			vals.set(1,m.mName);
			vals.set(2,m.mType);

			mConn.bulkInsertAdd("markers", vals);
			nmrk++;
		}
		mConn.finishBulkInserts();
		mLog.msg(nmrk + " markers uploaded");
	}
	
	private void getMarkerIdxList() throws Exception
	{
		String st = "select idx,name from markers where proj_idx=" + mProjIdx;
		ResultSet rs = mConn.executeQuery(st);
		while(rs.next())
		{
			int idx = rs.getInt(1);
			String name = rs.getString(2);
			mMrk2Idx.put(name,idx);
		}
		rs.close();
	}	
	
	private void getCloneIdxList() throws Exception
	{
		String st = "select idx,name from clones where proj_idx=" + mProjIdx;
		ResultSet rs = mConn.executeQuery(st);
		while(rs.next())
		{
			int idx = rs.getInt(1);
			String name = rs.getString(2);
			mCloneName2Idx.put(name,idx);
		}
		rs.close();
	}
	
	private void loadClones() throws Exception
	{
		mLog.msg("Load clones");
		int n = 0;
		Vector<String> vals = new Vector<String>();
		vals.add(0,Integer.toString(mProjIdx));
		vals.add(1,"");
		vals.add(2,"");
		vals.add(3,"");
		vals.add(4,"");
		vals.add(5,"");
		vals.add(6,"");
		vals.add(7,"");
		
		for (FPCContig ctg :mCtgList)
		{			
			if (Cancelled.isCancelled()) break;
			//if (ctg.mNum == 0) continue;

			vals.set(2, String.valueOf(ctg.mIdx));
			for (FPCClone c : mCtg2Clones.get(ctg.mNum))
			{
				String bes1 = "n";
				String bes2 = "n";
				if (mClone2BES.containsKey(c.mName))
				{
					if (mClone2BES.get(c.mName).contains(RF.R))
						bes1 = "r";
					if (mClone2BES.get(c.mName).contains(RF.F))
						bes2 = "f";
				}
				
				// collect the remark indexes
				Vector<String> ridxlist = new Vector<String>(); 
				for (String rem : c.mRem)
				{
					String ridx = mCloneRemCounts.get(rem).toString();
					ridxlist.add(ridx);
				}
				String ridxstr = Utils.join(ridxlist, ",");
				
				vals.set(1, c.mName);
				vals.set(3, String.valueOf(c.mL));
				vals.set(4, String.valueOf(c.mR));
				vals.set(5,bes1);
				vals.set(6,bes2);
				vals.set(7,ridxstr);
				
				mConn.bulkInsertAdd("clones", vals);
				
				n++;
				if (n % 5000 == 0)
					System.out.print(n + " clones\r");
			}
		}
		mConn.finishBulkInserts();
	}
	
	private void loadBES() throws Exception
	{
		String besdir = mSeqDir + "/bes";
		File bdf = new File(besdir);
		if (besFiles == null || besFiles.size() == 0)
		{
			if (bdf.exists() && bdf.isDirectory())
			{
				for (File f : bdf.listFiles())
				{
					besFiles.add(f);
				}
			}
			else
			{
				mLog.msg("No BES files were found in " + besdir);
				return;
			}
		}
		if (besFiles == null || besFiles.size() == 0)
		{
			mLog.msg("No BES files to load");
			return;
		}
		TreeSet<String> besSet =  new TreeSet<String>();
		int nDups = 0;
		BES besObj = new BES();
		Vector<String> vals = new Vector<String>();
		vals.add(0,String.valueOf(mProjIdx));
		vals.add(1,"");
		vals.add(2,"");
		vals.add(3,"");
		vals.add(4,"0");
		for (File f : besFiles)
		{
			if (Cancelled.isCancelled()) break;

			if (!f.isFile()) continue;
			FASTAParse fp = new FASTAParse(f);
			FASTASequence seq = new FASTASequence();
			int nSeqs = 0;
			mLog.msg("Load BES file:" + f.getName());
			while (fp.nextSeq(seq))
			{
				if (Cancelled.isCancelled()) break;
				
				if (!mConn.isSQLSafe(seq.name))
					throw(new Exception("Invalid BES name " + seq.name + " :" + mConn.mBadStrMsg));

				if (besSet.contains(seq.name))
				{
					mLog.msg("Duplicate BES " + seq.name);
					nDups++;
				}
				else
				{
					besSet.add(seq.name);
					if (!besObj.parseBES(seq.name))
						throw(new Exception("Unable to parse BES name " + seq.name + " (format should be e.g. a0123D12r)"));

					// keep track of which bes we found for each clone
					if (!mClone2BES.containsKey(besObj.mClone))
						mClone2BES.put(besObj.mClone, new TreeSet<RF>());

					mClone2BES.get(besObj.mClone).add(besObj.mRF);
					
					vals.set(1,besObj.mClone);
					vals.set(2,(besObj.mRF == RF.F ? "f" : "r")); // make sure sanitized!
					vals.set(3,seq.seq);
					mConn.bulkInsertAdd("bes_seq", vals);
				}
				nSeqs++;
				if (nSeqs % 5000 == 0) 
					System.out.print(nSeqs + " BES\r"); // CAS 1/5/18
			
				seq.clear();
			}
			mLog.msg(f.getName() + " has " + nSeqs + " sequences");
		}
		if (nDups > 0)
			throw(new Exception(nDups + " duplicated BES found; cannot continue"));
	}
	
	private void loadMrkSeq() throws Exception
	{
		String dir = mSeqDir + "/mrk";
		File df = new File(dir);
		if (mrkFiles == null || mrkFiles.size() == 0)
		{
			if (df.exists() && df.isDirectory())
			{
				for (File f : df.listFiles())
				{
					mrkFiles.add(f);
				}
			}
			else
			{
				mLog.msg("No marker files were found in " + dir);
				return;
			}
		}
		if (mrkFiles == null || mrkFiles.size() == 0)
		{
			mLog.msg("No marker files to load");
		}
		TreeSet<String> mrkSet =  new TreeSet<String>();
		int nDups = 0;
		Vector<String> vals = new Vector<String>();
		vals.add(0,String.valueOf(mProjIdx));
		vals.add(1,"");
		vals.add(2,"");
		for (File f : mrkFiles)
		{
			if (!f.isFile()) continue;
			FASTAParse fp = new FASTAParse(f);
			FASTASequence seq = new FASTASequence();
			int nSeqs = 0;
			mLog.msg("Load marker file:" + f.getName());
			while (fp.nextSeq(seq))
			{
				if (!mConn.isSQLSafe(seq.name))
					throw(new Exception("Invalid marker name " + seq.name + " :" + mConn.mBadStrMsg));
				if (mrkSet.contains(seq.name))
				{
					mLog.msg("Duplicate marker " + seq.name);
					nDups++;
				}
				else
				{
					mrkSet.add(seq.name);

					vals.set(1,seq.name);
					vals.set(2,seq.seq);
					if (seq.seq.length() > 20000)
					{
						mLog.msg("Long marker sequence: " + seq.name + " " + seq.seq.length());
						mConn.singleInsert("mrk_seq", vals);
					}
					else
					{
						mConn.bulkInsertAdd("mrk_seq", vals);
					}

				}
				nSeqs++;
				if (nSeqs % 5000 == 0) 
					System.out.print(nSeqs + " markers\r");
			
				seq.clear();
			}
			mLog.msg(f.getName() + " has " + nSeqs + " sequences");
		}
		if (nDups > 0)
			throw(new Exception(nDups + " duplicated markers found; cannot continue"));
	}	
	
	private void setCtgRemarkCounts() throws Exception
	{
		for (FPCContig c : mCtgList)
		{
			TreeMap<Integer,Integer> counts = new TreeMap<Integer,Integer>();
			for (FPCClone cl : mCtg2Clones.get(c.mNum))
			{
				for (String rmk : cl.mRem)
				{
					assert(mCloneRemCounts.containsKey(rmk));
					int ridx = mCloneRemCounts.get(rmk);
					if (!counts.containsKey(ridx))
						counts.put(ridx,1);
					else
						counts.put(ridx,1 + counts.get(ridx));
				}
				
			}
			Vector<String> vals = new Vector<String>();
			vals.add(0,String.valueOf(c.mIdx));
			vals.add(1,"");
			vals.add(2,"");
			for (Integer ridx : counts.keySet())
			{
				vals.set(1,String.valueOf(ridx));
				vals.set(2,String.valueOf(counts.get(ridx)));
				mConn.bulkInsertAdd("clone_remarks_byctg", vals);
			}		
		}
		mConn.finishBulkInserts();
	}
	
	private void uploadCloneRemarks() throws Exception
	{
		PreparedStatement mpsCloneRem = 
			mConn.prepareStatement("INSERT INTO clone_remarks VALUES(0," + mProjIdx + ",?,?)");
		// We can't do this with batch inserts since we need the insert ids
		for (String remark : mCloneRemCounts.keySet())
		{
			int count = mCloneRemCounts.get(remark);
			mpsCloneRem.setString(1,remark);
			mpsCloneRem.setInt(2,count);
			mpsCloneRem.executeUpdate();
			
			String st = "SELECT idx FROM clone_remarks WHERE proj_idx=" + mProjIdx +
							" AND remark='" + remark + "'" +
							" AND count=" + count;
			int idx = mConn.getIdx(st);
			mCloneRemCounts.put(remark, idx); 
		}
		mpsCloneRem.close();
	}
	
	private void ctgSizesAndCCB() throws Exception
	{
		TreeMap<Integer,Integer> grpIdx2CCB = new TreeMap<Integer,Integer>();
		
		for (FPCContig ctgObj : mCtgList)
		{
			if (!grpIdx2CCB.containsKey(ctgObj.mGrpIdx))
				grpIdx2CCB.put(ctgObj.mGrpIdx, 0);
			ctgObj.mCCB = grpIdx2CCB.get(ctgObj.mGrpIdx);

			if (!mCtg2Clones.containsKey(ctgObj.mNum))
				throw(new Exception("No clones found for contig " + ctgObj.mNum + " listed in the fpc file"));
			
			int left = mCtg2Clones.get(ctgObj.mNum).firstElement().mL;
			int right = mCtg2Clones.get(ctgObj.mNum).firstElement().mR;
			
			for (FPCClone cl : mCtg2Clones.get(ctgObj.mNum))
			{
				left = Math.min(left,cl.mL);
				right = Math.max(right,cl.mR);
			}
			ctgObj.mSize = right - left + 1;
			assert(ctgObj.mSize > 0);
			grpIdx2CCB.put(ctgObj.mGrpIdx, ctgObj.mCCB + ctgObj.mSize);
		}		
	}	
	
	class CtgSort  implements Comparator<FPCContig>
	{
		public int compare(FPCContig c1, FPCContig c2)
		{
			if (c1.mGrpIdx == c2.mGrpIdx)
			{
				if (c1.mPos < c2.mPos) return -1;
				else if (c1.mPos > c2.mPos) return +1;
				else return 0;
			}
			return (c1.mGrpIdx - c2.mGrpIdx);
		}
	}
}
