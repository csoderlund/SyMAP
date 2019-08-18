package backend;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Properties;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.lang.Math;

import util.Cancelled;
import util.ErrorCount;
import util.Logger;
import util.Utilities;

enum AnnotAction { Include, Exclude, Mark };
enum AlignType   { Blat, Mum };
enum HitStatus   { In, Out, Undecided };
enum QueryType   { Query, Target, Either };
enum FileType    { Bes, Mrk, Gene, Pseudo, Pseudo_genemask, Unknown };


public class AnchorsMain
{
	private static final int HIT_VEC_INC = 1000;
	private static final int maxHitLength = 10000;
	private static SyProps mProps = null;
	private UpdatePool pool;
	private Logger log;
	private int pairIdx = -1;
	private HashSet<Hit> hits;
	private Vector<Hit> diagHits;

	private int checkGrpIdx1 = 0;
	private int checkGrpIdx2 = 0;
	private boolean bInterrupt = false;
	Project p1, p2;
	private int mTotalHits=0, mTotalLargeHits=0, mTotalBrokenHits=0;
	private boolean isSelf = false;

	public AnchorsMain(UpdatePool pool, Logger log,Properties props, SyProps pairProps) {
		this.pool = pool;
		this.log = log;
		
		assert(pairProps != null);
		mProps = pairProps;
	}
	
	public boolean run(String proj1Name, String proj2Name) throws Exception
	{
		try
		{
			long startTime = System.currentTimeMillis();
			
			log.msg("\nLoading anchors for " + proj1Name + " and " + proj2Name);
			mProps.printNonDefaulted(log);
			
			Utils.initStats();

			pool.updateSchemaTo40();

			ProjType type = pool.getProjType(proj1Name);
			isSelf = (proj1Name.equals(proj2Name) && type == ProjType.pseudo); 
			
			String pairName = proj1Name + "_to_" + proj2Name;
			String pairDir = "data/pseudo_pseudo/" + pairName; // default to pseudo_pseudo
			if (type == ProjType.fpc)
				pairDir = "data/fpc_pseudo/" + pairName;
			if ( !Utilities.pathExists(pairDir) ) {
				log.msg("Can't find pair directory " + pairDir);
				ErrorCount.inc();
				return false;
			}
	
			p1 = new Project(pool, log, mProps, proj1Name, type, QueryType.Query);
			
			// make new object even if it is a self alignment - or filtering gets confused
			p2 = new Project(pool, log, mProps, proj2Name, ProjType.pseudo, QueryType.Target);
	
			// specific group debug capability
			if (!mProps.getProperty("checkgrp1").equals("0"))
				checkGrpIdx1 = p1.getGrpIdx(mProps.getProperty("checkgrp1"));
	
			if (!mProps.getProperty("checkgrp2").equals("0"))
				checkGrpIdx2 = p2.getGrpIdx(mProps.getProperty("checkgrp2"));
	
			// These map the annotation type  (e.g. 'exon') to AnnotAction, i.e. include/exclude
			// WMN no longer supported, remove eventually 9/29/10
			TreeMap<String,AnnotAction> AnnotSpec1 = new TreeMap<String,AnnotAction>();
			TreeMap<String,AnnotAction> AnnotSpec2 = new TreeMap<String,AnnotAction>();
			
			setAnnotSpec(AnnotSpec1, mProps.getProperty("annot_in1"), 
									 mProps.getProperty("annot_out1"), 
									 mProps.getProperty("annot_mark1"));
			setAnnotSpec(AnnotSpec2, mProps.getProperty("annot_in2"), 
									 mProps.getProperty("annot_out2"), 
									 mProps.getProperty("annot_mark2"));
			
			if (Cancelled.isCancelled()) return true;
			p2.loadAnnotation(pool, AnnotSpec2);
			if (type == ProjType.pseudo) 
				p1.loadAnnotation(pool, AnnotSpec1);
			
			renewPairIdx(p1, p2);
			if (Cancelled.isCancelled()) return true;
			mProps.uploadPairProps( pool, p1.getIdx(), p2.getIdx(), pairIdx,
					new String[] { 		
						"topn_and", "topn", "topn_maxwindow", "use_genemask",
						"gene_pad",	"do_clustering", "joinfact", "merge_blocks", 
						"mindots", "keep_best", "mindots_keepbest",
						"mingap1", "mingap1_cb", "mingap2", "search_factor",
						"corr1_A", "corr1_B", "corr1_C", "corr1_D",	
						"corr2_A", "corr2_B", "corr2_C", "corr2_D",	
						"avg1_A", "avg2_A",  "maxgap1", "maxgap2",
						"do_unanchored", "unanch_join_dist_bp", "subblock_multiple", 
						"do_bes_fixing", "checkctg1", "checkgrp1", "checkgrp2",
						"maxjoin_cb", "maxjoin_bp", "do_synteny","nucmer_only","promer_only",
						"blat_args","nucmer_args","promer_args"});
	
			assert(p2.isPseudo()); 
			String[] btypes1 = getSeqTypeList(p1);
			String[] btypes2 = getSeqTypeList(p2);
			
			HitBin.initKeepTypes();
			if (mProps.getProperty("keep_gene_gene").equals("1"))
			{
				HitBin.addKeepTypes(HitType.GeneGene);	
			}
			if (mProps.getProperty("keep_gene").equals("1"))
			{
				HitBin.addKeepTypes(HitType.GeneGene);	
				HitBin.addKeepTypes(HitType.GeneNonGene);	
			}
	
			
			Utils.initHist("TopNHist1" + GeneType.Gene, 3,6,10,25,50,100);
			Utils.initHist("TopNHist1" + GeneType.NonGene, 3,6,10,25,50,100);
			Utils.initHist("TopNHist1" + GeneType.NA, 3,6,10,25,50,100);
			Utils.initHist("TopNHist1Accept", 3,6,10,25,50,100);
			Utils.initHist("TopNHistTotal1", 3,6,10,25,50,100);
			Utils.initHist("TopNHist2" + GeneType.Gene, 3,6,10,25,50,100);
			Utils.initHist("TopNHist2" + GeneType.NonGene, 3,6,10,25,50,100);
			Utils.initHist("TopNHist2" + GeneType.NA, 3,6,10,25,50,100);
			Utils.initHist("TopNHist2Accept", 3,6,10,25,50,100);
			Utils.initHist("TopNHistTotal2", 3,6,10,25,50,100);
	
			int fnum = 0;
			int nHitsScanned = 0;
			diagHits = new Vector<Hit>();
			
			TreeSet<String> skipList = new TreeSet<String>();
			TreeSet<String> cleanUpList = new TreeSet<String>();
			String anchorSuff = ""; 
			
			if (p1.isPseudo()) p1.setGrpGeneParams(mProps.getInt("max_cluster_gap"),mProps.getInt("max_cluster_size"));
			if (p2.isPseudo()) p2.setGrpGeneParams(mProps.getInt("max_cluster_gap"),mProps.getInt("max_cluster_size"));
			
			if (Cancelled.isCancelled()) return true;
			if (type == ProjType.pseudo)
			{
				if (mProps.getBoolean("do_clustering"))
				{
					log.msg("Scanning files to create clusters:");
				}
				else
				{
					log.msg("Scanning files (clustering disabled):");
				}
				for(int i1 = 0; i1 < btypes1.length; i1++) {
					String btype1 = btypes1[i1];
					for(int i2 = 0; i2 < btypes2.length; i2++) {
						
						String btype2 = btypes2[i2];
						String bpair = btype1 + "_" + btype2;
						String bdir = pairDir + "/anchors/" + bpair;
						File dh = new File(bdir);
						if (dh.exists() && dh.isDirectory()) {
							File[] fs = dh.listFiles();
				
							for (int k = 0; k < fs.length; k++) {
								File f = fs[k];
								if (f.isFile() 
									&& !f.getName().endsWith(".done")	
									&& !f.getName().endsWith(".log"))	
								{
									AnchorFile af = new AnchorFile(f,btype1, btype2);
									if (Cancelled.isCancelled()) return true;
									int nHits = scanFile1(af, p1, p2, true);
									if (bInterrupt) return false;
									if (nHits == 0)
									{
										skipList.add(af.mFile.getName());
									}
									nHitsScanned += nHits;
									log.msg("\t" + (++fnum) + " " + f.toString() + " " + nHitsScanned + " scanned ");
								}
							}
						}
						if (isSelf)
						{
							log.msg("Scanning chromosome self-alignments");
							bdir += "_self";
							dh = new File(bdir);
							if (dh.exists() && dh.isDirectory()) {
								File[] fs = dh.listFiles();
								
								for (int k = 0; k < fs.length; k++) {
									File f = fs[k];
									if (f.isFile() 
										&& !f.getName().endsWith(".done")	
										&& !f.getName().endsWith(".log"))	
									{
										AnchorFile af = new AnchorFile(f,btype1, btype2);
										if (Cancelled.isCancelled()) return true;
										int nHits = scanFile1(af, p1, p2, false);
										if (bInterrupt) return false;
										if (nHits == 0)
										{
											skipList.add(af.mFile.getName());
										}
										nHitsScanned += nHits;
										
										log.msg("\t" + (++fnum) + " " + f.toString() + " " + nHitsScanned + " scanned ");
									}
								}
							}
						}
					}

				}
				p1.collectPGInfo();
				p2.collectPGInfo();
				
				if (Cancelled.isCancelled()) return true;
				nHitsScanned = 0;
				if (mTotalLargeHits > 0)
				{
					log.msg(mTotalHits + " scanned; " + mTotalLargeHits + " large hits; broken to " + mTotalBrokenHits);
				}
				if (mTotalHits == 0)
				{
					log.msg("No readable anchors were found - MUMmer probably did not run correctly.");
					log.msg("Try clearing the pair and then specifying your");
					log.msg("platform (32/64 bit and linux/mac) on the symap command line.");
					log.msg("Type ./symap -h to see the options.");
					ErrorCount.inc();
					return false;
				}
				log.msg("Scanning files to load hits:");
				fnum = 0;
				for(int i1 = 0; i1 < btypes1.length; i1++) {
					String btype1 = btypes1[i1];
					for(int i2 = 0; i2 < btypes2.length; i2++) {
						String btype2 = btypes2[i2];
						String bpair = btype1 + "_" + btype2;
						String bdir = pairDir + "/anchors/" + bpair;
						if (bpair.equals("pseudo_pseudo"))
						{
							bdir += anchorSuff;	
						}
						
						File dh = new File(bdir);
						if (dh.exists() && dh.isDirectory()) {
							File[] fs = dh.listFiles();
							int filesScanned = 0;
							int filesToScan = fs.length;						
							for (int k = 0; k < fs.length; k++) {
								File f = fs[k];
								if (f.isFile() 
									&& !f.getName().endsWith(".done")	
									&& !f.getName().endsWith(".log"))	
								{
									if (!skipList.contains(f.getName()))
									{
										AnchorFile af = new AnchorFile(f,btype1, btype2);
										if (Cancelled.isCancelled()) return true;
										nHitsScanned += scanFile2(af, p1, p2,true);
										if (bInterrupt) return false;
										filesScanned++;
										String clustered = (mProps.getBoolean("do_clustering") ? "clustered" : "");
										if (p1.isUnordered())
										{
											if (filesScanned % 100 == 0)
											{
												log.msg("\t scanned " + filesScanned + "/" + filesToScan + " files, " + nHitsScanned + " " + clustered + " hits");	
											}
										}
										else
										{																	
											log.msg("\t" + (++fnum) + " " + f.toString() + " " + nHitsScanned + " " + clustered + " hits" );
										}
									}
								}
							}
						}
						if (isSelf)
						{
							log.msg("Load chromosome self-alignments");
							bdir += "_self";
							dh = new File(bdir);
							if (dh.exists() && dh.isDirectory()) {
								File[] fs = dh.listFiles();
								int filesScanned = 0;
								int filesToScan = fs.length;						
								for (int k = 0; k < fs.length; k++) {
									File f = fs[k];
									if (f.isFile() 
										&& !f.getName().endsWith(".done")	
										&& !f.getName().endsWith(".log"))	
									{
										if (!skipList.contains(f.getName()))
										{
											AnchorFile af = new AnchorFile(f,btype1, btype2);
											if (Cancelled.isCancelled()) return true;
											nHitsScanned += scanFile2(af, p1, p2,false);
											if (bInterrupt) return false;
											filesScanned++;
											String clustered = (mProps.getBoolean("do_clustering") ? "clustered" : "");
											if (p1.isUnordered())
											{
												if (filesScanned % 100 == 0)
												{
													log.msg("\t scanned " + filesScanned + "/" + filesToScan + " files, " + nHitsScanned + " " + clustered + " hits");	
												}
											}
											else
											{																	
												log.msg("\t" + (++fnum) + " " + f.toString() + " " + nHitsScanned + " " + clustered + " hits" );
											}
										}
									}
								}
							}
						}
					}
				}
			}
			else
			{
				for(int i1 = 0; i1 < btypes1.length; i1++) {
					String btype1 = btypes1[i1];
					for(int i2 = 0; i2 < btypes2.length; i2++) {
						String btype2 = btypes2[i2];
						String bpair = btype1 + "_" + btype2;
						String bdir = pairDir + "/anchors/" + bpair;
						File dh = new File(bdir);
						if (dh.exists() && dh.isDirectory()) {
							File[] fs = dh.listFiles();
							for (int k = 0; k < fs.length; k++) {
								File f = fs[k];
								if (f.isFile() 
									&& !f.getName().endsWith(".done")	
									&& !f.getName().endsWith(".log"))	
								{
									AnchorFile af = new AnchorFile(f,btype1, btype2);
									nHitsScanned += scanBlatFile(af, p1, p2);
									if (bInterrupt) return false;
									log.msg(++fnum + " " + f.toString() + " " + nHitsScanned + " scanned");
								}
							}
						}
					}
				}
				log.msg("No readable anchors were found - BLAT probably did not run correctly.");
				log.msg("Try clearing the pair and then specifying your");
				log.msg("platform (32/64 bit and linux/mac) on the symap command line.");
				log.msg("Type ./symap -h to see the options.");

			}
			
			Utils.incStat("DiagHitsRaw", diagHits.size());
			
	
			// Do the final TopN filtering, and collect all the hits
			// which pass on at least one side into the set "hits"
			// for further filtering.
			
			hits = new HashSet<Hit>();
			p1.filterHits(hits, true); //!isSelf);
			p2.filterHits(hits, true); //!isSelf);
					
			int nQueryIn = 0;
			int nTargetIn = 0;
			for (Hit h : hits) 
			{	
				if (h.target.status == HitStatus.In) nTargetIn++;
				if (h.query.status == HitStatus.In) nQueryIn++;
			}
			
			if (mProps.getBoolean("topn_and")) 
			{
				for (Hit h : hits) 
				{
					if (h.query.status == HitStatus.In && h.target.status == HitStatus.In)
						h.status = HitStatus.In;
				}
			}
			else 
			{
				for (Hit h : hits) 
				{
					if (h.query.status == HitStatus.In || h.target.status == HitStatus.In)					
						h.status = HitStatus.In;
				}
			}
			
			log.msg("Query side filter accept: " + nQueryIn);
			log.msg("Target side filter accept: " + nTargetIn);
			
			hits.addAll(diagHits); 
			
			int nToLoad = 0;
			for (Hit hit : hits) {
				if (hit.status == HitStatus.In)
				{			
					nToLoad++;
					Utils.incHist("TopNHist1Accept", hit.binsize1);
					Utils.incHist("TopNHist2Accept", hit.binsize2);
					if (hit.mBT != null)
					{
						Utils.incStat(hit.mBT.toString() + "FinalHits", 1);
						Utils.incStat(hit.mBT.toString() + "FinalOrigHits", hit.origHits);
					}
				}
			}
			log.msg(nToLoad + " total hits to load");// (from " + nOrigHits + " raw hits)");
			Utils.incStat("TopNFinalKeep", nToLoad);
			
			checkUpdateHitsTable();
	
			int numLoaded = 0;
			for (Hit hit : hits) {
				if (hit.status == HitStatus.In) {
					if (bInterrupt) return false;
					uploadHit(hit, p1, p2);
					if (++numLoaded % 5000 == 0)
						System.out.print(numLoaded + " loaded...\r"); // CAS 1/1/18 was log.msg
				}
			}
			pool.finishBulkInserts();
			if (cleanUpList.size() > 0)
			{
				log.msg("Cleaning up...");
				for (String path : cleanUpList)
				{
					(new File(path)).delete();
				}
			}
			hits.clear();
			hits = null;
			
			if (isSelf)
			{
				addMirroredHits();	
			}
			hitAnnotations(p1,p2);
			
			// Lastly flip the anchor coordinates if the first project is draft that has been ordered
			// against another project
			if (!p1.orderAgainst.equals("") && !p1.orderAgainst.equals(p2.getName()))
			{
				pool.executeUpdate("update pseudo_hits as ph, pseudos as p, groups as g  " +
					" set ph.start1=p.length-ph.start1, ph.end1=p.length-ph.end1 " +
					"where ph.pair_idx=" + pairIdx + " and p.grp_idx=ph.grp1_idx and g.idx=ph.grp1_idx and g.flipped=1");
			}
			
			log.msg("Done:  " + Utilities.getDurationString(System.currentTimeMillis()-startTime) + "\n");
			
			if (p1.isPseudo())
			{
				Utils.uploadStats(pool, pairIdx, p1.idx, p2.idx);
				if (mProps.getBoolean("do_clustering"))
				{
					//checkConsistency();
				}
			}			
		}
		catch (OutOfMemoryError e)
		{
			System.out.println("\n\nOut of memory! To fix, \nA)Make sure you are using a 64-bit computer\nB)Edit the symap launch script and increase the memory limit.\n\n");
			System.exit(0);
		}		
		return true;
	}
	public void unpackFile(File srcFile, File destDir, TreeSet<String> cleanUp, boolean isSelf, Project p1) throws Exception
	{
		File destFile = null;
		BufferedWriter dfh = null;
		String qName = "";
		String tName = null;
		log.msg("Unpack " + srcFile.getAbsolutePath());
		BufferedReader sfh = new BufferedReader(new FileReader(srcFile));
		String line;
		Hit h = new Hit();
		while (sfh.ready())
		{
			if (bInterrupt) return;
			line = sfh.readLine();
			scanNextMummerHit(line,h);
			
			h.query.grpIdx = p1.grpIdxFromQuery(h.query.name);
			if (h.query.grpIdx == -1) continue;

			if (tName == null)
			{
				tName = h.target.name;	
			}
			else if (!tName.equals(h.target.name))
			{
				log.msg("second target " + h.target.name + " found in file " + srcFile.getAbsolutePath() + "! Aborting");
				return;
			}
			if (isSelf)
			{
				// Don't expand redundant hits
				// Also, mummer isn't fully symmetric, so we will get fully symmetric hits by 
				// taking one direction only and then symmetrizing it after (addMirroredHits)
				if (0 < h.query.name.compareTo(tName))
				{
					continue;
				}
			}
			if (!h.query.name.equals(qName))
			{
				// we came to a new query
				if (dfh != null)
				{
					dfh.flush();
					dfh.close();
					dfh = null;
				}
				qName = h.query.name;
				destFile = new File(destDir,qName + "_" + tName + ".mum");
				
				cleanUp.add(destFile.getAbsolutePath());
				destFile.createNewFile();
				dfh = new BufferedWriter(new FileWriter(destFile));
			}
			dfh.write(line + "\n");				
	
		}
		if (dfh != null)
		{
			dfh.flush();
			dfh.close();
		}
	}
	public void interrupt()
	{
		bInterrupt = true;	
	}
	public void checkUpdateHitsTable() throws Exception
	{
		ResultSet rs = pool.executeQuery("show columns from pseudo_hits where field='annot1_idx'");
		if (!rs.first())
		{
			pool.executeUpdate("alter table pseudo_hits add annot1_idx integer default 0");	
			pool.executeUpdate("alter table pseudo_hits add annot2_idx integer default 0");	
		}
	}

	private String[] getSeqTypeList(Project p) {
		boolean useGenes = mProps.getBoolean("use_genemask");
		boolean genesOnly = false;
		
		if (p.isFPC()) 
			return new String[] {"mrk","bes"};
		else {
			if (genesOnly)
				return new String[] {"gene"};
			else if (!useGenes)
				return new String[] {"pseudo"};
			else
				return new String[] {"gene","pseudo"};
		}
	}
	
	private void setAnnotSpec(TreeMap<String,AnnotAction> AnnotSpec, String in, String out, String mark)
	{
		String[] keys = in.split(",");
		for (int i = 0; i < keys.length; i++)
		{
			String key = keys[i].trim();
			if (key.length() > 0)
				AnnotSpec.put(key,AnnotAction.Include);				
		}

		keys = out.split(",");
		for (int i = 0; i < keys.length; i++)
		{
			String key = keys[i].trim();
			if (key.length() > 0)
				AnnotSpec.put(key,AnnotAction.Exclude);				
		}
		
		keys = mark.split(",");
		for (int i = 0; i < keys.length; i++)
		{
			String key = keys[i].trim();
			if (key.length() > 0)
				AnnotSpec.put(key,AnnotAction.Mark);				
		}
	}
	
	private void renewPairIdx(Project p1, Project p2) throws SQLException
	{
		String st = "DELETE FROM pairs WHERE proj1_idx=" + p1.getIdx() + " AND proj2_idx=" + p2.getIdx();
		pool.executeUpdate(st);
	
		st = "INSERT INTO pairs (proj1_idx,proj2_idx) VALUES('" + p1.getIdx() + "','" + p2.getIdx() + "')";
		pool.executeUpdate(st);
		
		st = "SELECT idx FROM pairs WHERE proj1_idx=" + p1.getIdx() + " AND proj2_idx=" + p2.getIdx();
		pairIdx = pool.getIdx(st);
	}
	
	// pseudo/pseudo first time through - create the predicted genes
	private int scanFile1(AnchorFile file, Project p1, Project p2, boolean skipSelf) throws Exception
	{
		if (!file.mFile.getName().endsWith(".mum")) return 0;
		
		BufferedReader fh = new BufferedReader(new FileReader(file.mFile));
		int lineNum = 0;

		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		int numErrors = 0;
		
		while (fh.ready()) {
			if (bInterrupt) return 0;
			Hit hit = new Hit();
			hit.origHits = 1;
			hit.query.fileType = file.mType1;
			hit.target.fileType = file.mType2;
			line = fh.readLine().trim();
			lineNum++;
			if (line.length() > 0)
			{
				boolean success = false;
				success = scanNextMummerHit(line,hit);
				if (!success)
				{
					if (numErrors < 5)
					{
						log.msg("Parse error on line " + lineNum + " in " + file.mFile.getName());
						numErrors++;
						continue;
					}
					else
					{
						throw( new Exception("Too many errors in file!") );
					}
				}

				if (hit.query.fileType == FileType.Pseudo) 
				{
					hit.query.grpIdx = p1.grpIdxFromQuery(hit.query.name);
					if (hit.query.grpIdx == -1)
					{
						log.msg(p2.getName() + ": Query not found: " + hit.query.name + " --skipping file");
						fh.close();
						return 0;
					}
				}
				if (hit.target.fileType == FileType.Pseudo) 
				{
					hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
					if (hit.target.grpIdx == -1)
					{
						log.msg(p2.getName() + ": Target not found for: " + hit.target.name + " --skipping file");
						fh.close();
						return 0;
					}
				}
				if (skipSelf && hit.query.grpIdx==hit.target.grpIdx) continue; // we run the chr x itself separately
				if (p1.idx == p2.idx && hit.query.grpIdx > hit.target.grpIdx) continue; // for self we'll be mirroring later
				if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start < hit.target.start) continue; // we'll mirror these later
				if (checkGrpIdx1 > 0 && hit.query.grpIdx != checkGrpIdx1)
					continue;
				if (checkGrpIdx2 > 0 && hit.target.grpIdx != checkGrpIdx2)
					continue;
				
				mTotalHits++;
				if (hit.maxLength() > maxHitLength)
				{
					Vector<Hit> brokenHits = breakHit(hit);
					mTotalLargeHits++;
					mTotalBrokenHits++;
					rawHits.addAll(brokenHits);
				}
				else
				{
					hit.orderEnds();
					rawHits.add(hit);
				}
			}
		}
		fh.close();

		createPredictedGenes2(rawHits);
		rawHits = null;
		return lineNum;
	}
	public Vector<Hit> breakHit(Hit hit)
	{
		Vector<Hit> ret = new Vector<Hit>();
		
		// Break the hit into pieces, taking care to not leave a tiny leftover hit, and for 
		// cases where query and target are different lengths (maybe not possible with mummer).
		// Note, hits have been previously fixed so start < end. 
		int minLeftover = maxHitLength/10;
		
		int qlen = hit.query.length();
		int tlen = hit.target.length();
		
		int qleft = qlen%maxHitLength;
		int tleft = tlen%maxHitLength;		
		
		int qparts = (qleft >= minLeftover ?  (1 + qlen/maxHitLength) : (qlen/maxHitLength));
		int tparts = (tleft >= minLeftover ?  (1 + tlen/maxHitLength) : (tlen/maxHitLength));
		
		int parts = Math.min(qparts,tparts);
		
		if (parts == 1)
		{
			hit.orderEnds();
			hit.idx=6;
			ret.add(hit);
		}
		else if (!hit.reversed())
		{
			// build (parts-1) hits of fixed size, and put the rest into the final hit
			
			hit.orderEnds(); // this is a bit hacky but in fact the query and target can BOTH be reversed,
								// in which case reversed() returns false
			for (int i = 1; i < parts; i++)
			{
				int qstart = hit.query.start + maxHitLength*(i-1);
				int qend = qstart + maxHitLength - 1;
				int tstart = hit.target.start + maxHitLength*(i-1);
				int tend = tstart + maxHitLength - 1;
				Hit h = new Hit(hit);
				h.query.start = qstart;
				h.query.end = qend;
				h.target.start = tstart;
				h.target.end = tend;
				h.idx=1;
				ret.add(h);
			
			}
			int qstart = hit.query.start + maxHitLength*(parts-1);
			int qend = hit.query.end;
			int tstart = hit.target.start + maxHitLength*(parts-1);
			int tend = hit.target.end;
			Hit h = new Hit(hit);
			h.query.start = qstart;
			h.query.end = qend;
			h.target.start = tstart;
			h.target.end = tend;
			h.idx=2;
			ret.add(h);
			
		}
		else if (hit.reversed())
		{
			// reversed - for forward through the query, backward through target
			
			hit.orderEnds(); // because this is the last time we need to know whether it started out reversed
			
			for (int i = 1; i < parts; i++)
			{
				int qstart = hit.query.start + maxHitLength*(i-1);
				int qend = qstart + maxHitLength - 1;
				int tend = hit.target.end - maxHitLength*(i-1);
				int tstart = tend - maxHitLength + 1;
				Hit h = new Hit(hit);
				h.query.start = qstart;
				h.query.end = qend;
				h.target.start = tstart;
				h.target.end = tend;
				h.idx=3;
				ret.add(h);

			}
			int qstart = hit.query.start + maxHitLength*(parts-1);
			int qend = hit.query.end;
			int tend = hit.target.end - maxHitLength*(parts-1);
			int tstart = hit.target.start;
			Hit h = new Hit(hit);
			h.query.start = qstart;
			h.query.end = qend;
			h.target.start = tstart;
			h.target.end = tend;
			h.idx=4;
			ret.add(h);		

		}
		return ret;
	}
	public void checkHitList(Vector<Hit> list, int s1, int e1, int s2, int e2) throws Exception
	{
		for (Hit h : list)
		{
			if (s2 > 0 || e2 > 0)
			{
				if (h.target.start == s2 && h.target.end == e2)
				{
					return;
				}
			}
			else if (s1 > 0 || e1 > 0)
			{
				if (h.query.start == s2 && h.query.end == e2)
				{
					return;
				}
			}			
		}
		throw(new Exception("hit not found"));
	}
	// pseudo/pseudo 2nd time through - do the clustering
	private int scanFile2(AnchorFile file, Project p1, Project p2, boolean skipSelf) throws Exception
	{
		AlignType alignType = null;
		if (file.mFile.getName().endsWith(".mum"))
			alignType = AlignType.Mum;
		else return 0;
		
		BufferedReader fh = new BufferedReader(new FileReader(file.mFile));
		int lineNum = 0;
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		int numErrors = 0;
		
		int totalLargeHits = 0;
		int totalBrokenHits = 0;
		while (fh.ready()) 
		{
			Hit hit = new Hit();
			hit.origHits = 1;
			hit.query.fileType = file.mType1;
			hit.target.fileType = file.mType2;
			line = fh.readLine().trim();
			lineNum++;
			if (line.length() > 0)
			{
				if (bInterrupt) return 0;
				boolean success = false;
				success = scanNextMummerHit(line,hit);
				
				if (!success)
				{
					if (numErrors < 5)
					{
						log.msg("Parse error on line " + lineNum + " in " + file.mFile.getName());
						numErrors++;
						continue;
					}
					else
					{
						throw( new Exception("Too many errors in file!") );
					}
				}

				if (hit.query.fileType == FileType.Pseudo) 
				{
					hit.query.grpIdx = p1.grpIdxFromQuery(hit.query.name);
					if (hit.query.grpIdx == -1)
					{
						throw(new Exception("Query not found: " + hit.query.name));
					}
				}
				
				if (hit.target.fileType == FileType.Pseudo) {
					hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
					if (hit.target.grpIdx == -1)
						throw(new Exception("Target not found: " + hit.target.name));
				}

				if (skipSelf && hit.query.grpIdx==hit.target.grpIdx) continue;
				if (p1.idx == p2.idx && hit.query.grpIdx > hit.target.grpIdx) continue; // for self we'll be mirroring later
				if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start < hit.target.start) continue; // we'll mirror these later

				if (checkGrpIdx1 > 0 && hit.query.grpIdx != checkGrpIdx1)
					continue;
				if (checkGrpIdx2 > 0 && hit.target.grpIdx != checkGrpIdx2)
					continue;
								
				// Diagonal hits we'll save and handle separately (because we always want to keep them,
				// and sometimes they are very long etc.)
				if (hit.query.grpIdx == hit.target.grpIdx 	&& hit.query.start == hit.target.start
						&& hit.query.end == hit.target.end)
				{
					hit.status = HitStatus.In; 
					diagHits.add(hit);
					continue;
				}
				
				if (hit.maxLength() > maxHitLength)
				{
					Vector<Hit> brokenHits = breakHit(hit);
					totalLargeHits++;
					totalBrokenHits += brokenHits.size();
					rawHits.addAll(brokenHits);
				}
				else
				{
					hit.orderEnds();
					rawHits.add(hit);
				}			
			}
		}
	
		fh.close();
		Utils.incStat("RawHits",rawHits.size());
		Utils.incStat("LargeHits",totalLargeHits);
		Utils.incStat("BrokenHits",totalBrokenHits);

		if (alignType == AlignType.Mum && !rawHits.isEmpty()) 
		{
			if (mProps.getBoolean("do_clustering") ) 											
			{
				rawHits = clusterGeneHits2( rawHits );				
			}
		}
		
		// At this point the rawHits are clustered and we'll put them in their TopN bins,
		// dropping off those that already fail topN
		Collections.sort(rawHits);
		preFilterHits(rawHits, p1, p2);
		return rawHits.size();
	}	
	
	// for fpc/pseudo only
	private int scanBlatFile(AnchorFile file, Project p1, Project p2) throws Exception
	{
		AlignType alignType = null;
		if (file.mFile.getName().endsWith(".blat"))
			alignType = AlignType.Blat;
		else return 0;
		
		BufferedReader fh = new BufferedReader(new FileReader(file.mFile));
		int lineNum = 0;

		if (alignType == AlignType.Blat) {
			// Skip header 
			fh.readLine();
			fh.readLine();
			fh.readLine();
			fh.readLine();
			fh.readLine();
		}
		
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		int numErrors = 0;
		
		while (fh.ready()) {
			if (bInterrupt) return 0;
			Hit hit = new Hit();
			hit.origHits = 1;
			hit.query.fileType = file.mType1;
			hit.target.fileType = file.mType2;
			line = fh.readLine().trim();
			lineNum++;
			if (line.length() > 0)
			{
				boolean success = false; 
				boolean reversed = (file.mType1 == FileType.Pseudo && file.mType2 != FileType.Pseudo);
				success = scanNextBlatHit(line,hit,reversed);
				
				if (!success)
				{
					if (numErrors < 5)
					{
						log.msg("Parse error on line " + lineNum + " in " + file.mFile.getName());
						numErrors++;
						continue;
					}
					else
					{
						throw( new Exception("Too many errors in file!") );
					}
				}

				if (hit.query.fileType == FileType.Bes)
				{
					if (!p1.getFPCData().parseBES(hit.query.name,hit))
						throw(new Exception("Unable to parse BES " + hit.query.name));
				}
				
				if (hit.target.fileType == FileType.Pseudo) {
					hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
					if (hit.target.grpIdx == -1)
						throw(new Exception("Target not found: " + hit.target.name));
				}
				if (checkGrpIdx1 > 0 && hit.query.grpIdx != checkGrpIdx1)
					continue;
				if (checkGrpIdx2 > 0 && hit.target.grpIdx != checkGrpIdx2)
					continue;
				
				rawHits.add(hit);
			}
		}
		fh.close();
		Utils.incStat("RawHits",rawHits.size());

		preFilterHits(rawHits, p1, p2);
		
		return lineNum;
	}		
	int countTotalHits(Vector<Hit> hits) throws Exception
	{
		int out = 0;
		for (Hit h : hits)
		{
			if (h.origHits == 0)
			{
				throw(new Exception("0 orig hits"));	
			}
			out += h.origHits;	
		}
		return out;
	}
	
	public void createPredictedGenes2(Vector<Hit> hits) throws Exception
	{		
		for (Hit h : hits) 
		{			
			Group grp1 = p1.getGrpByIdx(h.query.grpIdx);
			Group grp2 = p2.getGrpByIdx(h.target.grpIdx);
			
			grp1.testHitForAnnotOverlapAndUpdate2(h.query.start, h.query.end);
			grp2.testHitForAnnotOverlapAndUpdate2(h.target.start, h.target.end);
		}
	}

	// Cluster the mummer hits into larger hits using the annotations, including the "predicted genes".
	// So our possible hit set will now be a subset of annotations x annotations, and the 
	// actual length of the hits will be the sum of the hits that went into them.
	// The component hits are remembered and saved to show as "exons" in the viewer.

	private Vector<Hit> clusterGeneHits2(Vector<Hit> inHits) throws Exception
	{
		Vector<Hit> outHits = new Vector<Hit>(HIT_VEC_INC, HIT_VEC_INC);
		Vector<Hit> bigHits = new Vector<Hit>();
		
		HashMap<String,Vector<Hit>> hitBins = new HashMap<String,Vector<Hit>>();
		HashMap<String,AnnotElem> key2qannot = new HashMap<String,AnnotElem>();
		HashMap<String,AnnotElem> key2tannot = new HashMap<String,AnnotElem>();
		HashMap<String,HitType> binTypes = new HashMap<String,HitType>(); // hack, for stats
						
		// First put the hits into bins labeled by annotation1 x annotation2
		// Note that a given annotation can only go to one group
		for (Hit hit : inHits) 
		{		
			Group grp1 = p1.getGrpByIdx(hit.query.grpIdx);
			Group grp2 = p2.getGrpByIdx(hit.target.grpIdx);
			
			AnnotElem qAnnot = grp1.getMostOverlappingAnnot(hit.query.start, hit.query.end);
			AnnotElem tAnnot = grp2.getMostOverlappingAnnot(hit.target.start, hit.target.end);

			if (qAnnot == null)
			{
				throw(new Exception("missing query annot! grp:" + grp1.idStr() + " start:" + hit.query.start + " end:" + hit.query.end + " idx:" + hit.idx));	
			}
			if (tAnnot == null)
			{
				throw(new Exception("missing target annot! grp:" + grp2.idStr() + " start:" + hit.target.start + " end:" + hit.target.end));	
			}
			
			String key = qAnnot.mID + "_" + tAnnot.mID;
			if (!hitBins.containsKey(key))
			{
				hitBins.put(key, new Vector<Hit>());
				key2qannot.put(key,qAnnot);
				key2tannot.put(key,tAnnot);
				if (!qAnnot.isGene() && !tAnnot.isGene())
				{
					Utils.incStat("NonGeneClusters", 1);	
					binTypes.put(key,HitType.NonGene);
				}
				else if (qAnnot.isGene() && tAnnot.isGene())
				{
					Utils.incStat("GeneClusters", 1);
					binTypes.put(key,HitType.GeneGene);
				}
				else 
				{
					Utils.incStat("GeneNonGeneClusters", 1);	
					binTypes.put(key,HitType.GeneNonGene);
				}						
			}
			hitBins.get(key).add(hit);
			if (!qAnnot.isGene() && !tAnnot.isGene())
			{
				Utils.incStat("NonGeneHits", 1);	
			}
			else if (qAnnot.isGene() && tAnnot.isGene())
			{
				Utils.incStat("GeneHits", 1);	
			}
			else 
			{
				Utils.incStat("GeneNonGeneHits", 1);	
			}						
		}
		
		// Merge the binned hits
		for (String key : hitBins.keySet()) 
		{			
			Vector<Hit> binHits = hitBins.get(key);	
			Hit h = Hit.clusterHits( binHits ,binTypes.get(key));
			AnnotElem qAnnot = key2qannot.get(key);
			if (qAnnot.isGene())
			{
				h.annotIdx1 = qAnnot.idx;	
			}
			AnnotElem tAnnot = key2tannot.get(key);
			if (tAnnot.isGene())
			{
				h.annotIdx2 = tAnnot.idx;	
			}			
			outHits.add(h);			
		}
		
		Utils.incStat("TotalClusters", outHits.size());
		
		if (bigHits.size() > 0)
		{
			Utils.incStat("BigHits", bigHits.size());
			Hit.mergeOverlappingHits(bigHits);
			Utils.incStat("MergedBigHits", bigHits.size());
			outHits.addAll(bigHits);
		}
		return outHits;
	}	
	// Add hits to their topN bins, applying the topN criterion immediately when possible,
	// to reduce memory.
	private void preFilterHits(Vector<Hit> inHits, Project p1, Project p2) throws Exception
	{
		Vector<Hit> theseDiag = new Vector<Hit>();
		
		for (Hit hit : inHits) {
			// If it's a diagonal hit, keep it but do not use in further processing					
			if (hit.query.grpIdx == hit.target.grpIdx 
					&& hit.query.start == hit.target.start
					&& hit.query.end == hit.target.end)
			{
				hit.status = HitStatus.In; 
				theseDiag.add(hit); 
				Utils.incStat("DiagHits", 1);
				continue;
			}
			// Add to the appropriate TopN filter bin, unless it's
			// already clear that it will not pass the filter
			p1.checkPreFilter(hit,hit.query);
			p2.checkPreFilter(hit,hit.target);
		}
		Hit.mergeOverlappingHits(theseDiag);
		diagHits.addAll(theseDiag);
		Utils.incStat("MergedDiagHits", diagHits.size());
	}

	private boolean scanNextBlatHit(String line, Hit hit, boolean reversed)
	{
		String[] fs = line.split("\\s+");
		if (fs.length < 21)
			return false;
		
		int match = Integer.parseInt(fs[0]) + Integer.parseInt(fs[2]);
		int mis = Integer.parseInt(fs[1]);
		String strand 	= fs[8];
			
		int tstart 	= Integer.parseInt(fs[15]);
		int tend 	= Integer.parseInt(fs[16]);
		int qstart 	= Integer.parseInt(fs[11]);
		int qend 	= Integer.parseInt(fs[12]);
		int qlen 	= Integer.parseInt(fs[10]);
				
		int[] qstarts 	= Utils.strArrayToInt(fs[19].split(","));
		int[] tstarts 	= Utils.strArrayToInt(fs[20].split(","));
		int[] bsizes 	= Utils.strArrayToInt(fs[18].split(","));

		// for reverse strand hits, blat reverses the coordinates of the blocks
		if (strand.equals("-"))
		{
			for (int i = 0; i < qstarts.length; i++)
				qstarts[i] = qlen - qstarts[i] - bsizes[i];
		}

		int pctid = Math.round(100*match/(match + mis));
		strand = (strand.equals("-") ? "+/-" : "+/+");
		
		int[] qseqs = new int[qstarts.length*2];
		int[] tseqs = new int[tstarts.length*2];
		
		for (int i = 0; i < qstarts.length; i++)
		{
			int qend1 = qstarts[i] + bsizes[i] - 1;
			int tend1 = tstarts[i] + bsizes[i] - 1;
			
			qseqs[i*2] = qstarts[i];
			qseqs[i*2+1] = qend1;
			
			tseqs[i*2] = tstarts[i];
			tseqs[i*2+1] = tend1;
		}	
		
		hit.query.start  = (!reversed ? qstart : tstart);
		hit.query.end    = (!reversed ? qend   : tend);
		hit.target.start = (!reversed ? tstart : qstart);
		hit.target.end   = (!reversed ? tend   : qend);
		
		hit.query.name  = (!reversed ? fs[9]  : fs[13]);
		hit.target.name = (!reversed ? fs[13] : fs[9]);
		
		hit.query.name = hit.query.name.intern();
		hit.target.name = hit.target.name.intern();
		
		hit.matchLen = match;
		hit.pctid = pctid;
		hit.strand = strand.intern(); 
		
		hit.query.blocks = (!reversed ? qseqs : tseqs);
		hit.target.blocks = (!reversed ? tseqs : qseqs);
		
		return true;
	}
	
	private boolean scanNextMummerHit(String line, Hit hit)
	{
		String[] fs = line.split("\\s+");
		if (fs.length < 13)
			return false;
		
		int poffset = (fs.length > 13 ? 2 : 0); // nucmer vs. promer
		int tstart 	= Integer.parseInt(fs[0]);
		int tend 	= Integer.parseInt(fs[1]);
		int qstart 	= Integer.parseInt(fs[2]);
		int qend 	= Integer.parseInt(fs[3]);
		int match 	= Integer.parseInt(fs[5]);
		int pctid 	= Math.round(Float.parseFloat(fs[6]));
		String target 	= fs[11 + poffset];
		String query 	= fs[12 + poffset];
				
		String strand = (qstart <= qend ? "+" : "-") + "/" + (tstart <= tend ? "+" : "-"); 	

		hit.query.name  = query.intern();	
		hit.target.name = target.intern();
		hit.query.start = qstart;
		hit.query.end = qend;
		hit.target.start = tstart;
		hit.target.end = tend;
		hit.matchLen = match;
		hit.pctid = pctid;
		hit.strand = strand.intern(); // reduce hit memory footprint
		
		return true;
	}	

	public void uploadHit(Hit hit, Project p1, Project p2) throws Exception
	{
		// Set gene overlap field. For pseudo, we've already got this due to clustering, but for
		// FPC we've got to set it. 
		int geneOlap = 0;
		if (p1.isFPC())
		{
			Group g2 = p2.getGrpByIdx(hit.target.grpIdx);
			if (g2 != null && g2.testHitForAnnotOverlap(hit.target))
			{
				geneOlap = 1; 
			}
		}
		else
		{
			if (hit.mBT == HitType.GeneGene)
			{
				geneOlap = 2;	
			}
			else if (hit.mBT == HitType.GeneNonGene)
			{
				geneOlap = 1;	
			}
		}
		Vector<String> vals = new Vector<String>(30);
		switch (hit.query.fileType)
		{
			case Bes:
				String rf = (hit.rf == RF.R ? "r" : "f");
				vals.add("" + pairIdx);
				vals.add("" + p1.getIdx());
				vals.add("" + p2.getIdx());
				vals.add(hit.clone);
				vals.add(rf);
				vals.add("" + hit.target.grpIdx);
				vals.add("0");
				vals.add("" + hit.pctid);
				vals.add("" + hit.matchLen);
				vals.add(hit.strand);
				vals.add("" + hit.query.start);
				vals.add("" + hit.query.end);
				vals.add("" + hit.target.start);
				vals.add("" + hit.target.end);
				vals.add(Utils.intArrayToBlockStr(hit.query.blocks));
				vals.add(Utils.intArrayToBlockStr(hit.target.blocks));
				vals.add("" + geneOlap);
				pool.bulkInsertAdd("bes", vals);					
				break;
			case Mrk:
				vals.add("" + pairIdx);
				vals.add("" + p1.getIdx());
				vals.add(hit.query.name);
				vals.add("" + hit.target.grpIdx);
				vals.add("0");
				vals.add("" + hit.pctid);
				vals.add("" + hit.matchLen);
				vals.add(hit.strand);
				vals.add("" + hit.query.start);
				vals.add("" + hit.query.end);
				vals.add("" + hit.target.start);
				vals.add("" + hit.target.end);
				vals.add(Utils.intArrayToBlockStr(hit.query.blocks));
				vals.add(Utils.intArrayToBlockStr(hit.target.blocks));
				vals.add("" + geneOlap);
				pool.bulkInsertAdd("mrk", vals);				
				break;
			case Pseudo:
			case Pseudo_genemask:
			case Gene:
				vals.add("" + pairIdx);
				vals.add("" + p1.getIdx());
				vals.add("" + p2.getIdx());
				vals.add("" + hit.query.grpIdx);
				vals.add("" + hit.target.grpIdx);
				vals.add("0");
				vals.add("" + hit.pctid);
				vals.add("" + hit.matchLen);
				vals.add(hit.strand);
				vals.add("" + hit.query.start);
				vals.add("" + hit.query.end);
				vals.add("" + hit.target.start);
				vals.add("" + hit.target.end);
				vals.add(Utils.intArrayToBlockStr(hit.query.blocks));
				vals.add(Utils.intArrayToBlockStr(hit.target.blocks));
				vals.add("" + geneOlap);
				vals.add("0");
				vals.add("0");
				vals.add("" + hit.annotIdx1);
				vals.add("" + hit.annotIdx2);
				pool.bulkInsertAdd("pseudo", vals);
				break;
			default:
				throw(new Exception("Unknown file type!!"));
		}
	}
	public void addMirroredHits() throws Exception
	{
		// Create the reflected hits for self-alignment cases
		pool.executeUpdate("insert into pseudo_hits (select 0,pair_idx,proj1_idx,proj2_idx,grp2_idx,grp1_idx,evalue,pctid," + 
				"score,strand,start2,end2,start1,end1,target_seq,query_seq,gene_overlap,countpct,cvgpct,idx,annot2_idx,annot1_idx,runsize " + 
				" from pseudo_hits where pair_idx=" + pairIdx + " and refidx=0 and start1 != start2 and end1 != end2)"	
			);
		pool.executeUpdate("update pseudo_hits as ph1, pseudo_hits as ph2 set ph1.refidx=ph2.idx where ph1.idx=ph2.refidx " +
				" and ph1.refidx=0 and ph2.refidx != 0 and ph1.pair_idx=" + pairIdx + " and ph2.pair_idx=" + pairIdx);
	}
	public void checkConsistency()
	{
		int gene = Utils.mStats.get("GeneHits").intValue();	
		int gng = Utils.mStats.get("GeneNonGeneHits").intValue();	
		int ng = Utils.mStats.get("NonGeneHits").intValue();	
		int raw = Utils.mStats.get("RawHits").intValue();
		
		if (raw != gene + gng + ng)
		{
			log.msg("WARNING: hit categories don't add up! raw=" + raw + ", gene=" + gene + ", nongene=" + ng + ",gng=" + gng);	
		}
	}
	
	// Download the recently-uploaded hits (to get their db index) and 
	// enter the pseudo_hits_annot entry for the gene hits only.
	// This is kind of ineffecient because we've already found annotation overlaps
	// in doing the clustering, but it's more complicated to keep track there.
	private void hitAnnotations(Project p1, Project p2) throws SQLException
	{
		Vector<Hit> newHits = getUploadedHits(p1);
		log.msg("Scan hits for " + p2.name + " annotation overlaps ");
		HashMap<String,Integer> counts2 = new HashMap<String,Integer>();
		int count = 0;
		for (Hit h : newHits)
		{
			if (h.target.grpIdx == p2.unanchoredGrpIdx) continue;
			Group g = p2.getGrpByIdx(h.target.grpIdx);
			g.addAnnotHitOverlaps(h,h.target,pool,counts2);
			count++;
			if (count % 5000 == 0) System.out.print(count + " checked...\r"); // CAS 1/1/18 changed from log
		}
		if (counts2.keySet().size() > 0)
		{
			log.msg(p2.name + " annotation overlaps");
			for (String atype : counts2.keySet())
				log.msg("   " + atype + " " + counts2.get(atype));
		}		
		if (p1.type == ProjType.pseudo)
		{
			log.msg("Scan hits for " + p1.name + " annotation overlaps ");
			HashMap<String,Integer> counts1 = new HashMap<String,Integer>();

			count = 0;
			for (Hit h : newHits)
			{
				if (h.query.grpIdx == p1.unanchoredGrpIdx) continue;
				Group g = p1.getGrpByIdx(h.query.grpIdx);
				g.addAnnotHitOverlaps(h,h.query,pool,counts1);
				count++;
				if (count % 5000 == 0) System.out.print(count + " checked...\r"); // CAS 1/1/18 changed from log
			}
			
			if (counts1.keySet().size() > 0)
			{
				log.msg(p1.name + " annotation overlaps");
				for (String atype : counts1.keySet())
					log.msg("   " + atype + " " + counts1.get(atype));			
			}
		}
		pool.finishBulkInserts();
	}
	
	private Vector<Hit> getUploadedHits(Project p1) throws SQLException
	{
		String st;
		
		Vector<Hit> ret = new Vector<Hit>();
		
		if (p1.type == ProjType.pseudo)
		{
	      	st = "SELECT pseudo_hits.idx as hidx, pseudo_hits.start2, pseudo_hits.end2, " +
	      			" pseudo_hits.start1, pseudo_hits.end1, pseudo_hits.grp1_idx, pseudo_hits.grp2_idx" +
	      			" FROM pseudo_hits WHERE pair_idx=" + pairIdx;
			ResultSet rs = pool.executeQuery(st);
			while (rs.next())
			{
				int idx = rs.getInt("hidx");
				int s1 = rs.getInt("start1");
				int e1 = rs.getInt("end1");					
				int s2 = rs.getInt("start2");
				int e2 = rs.getInt("end2");
				Hit h = new Hit();
				h.idx = idx;
				h.query.start = s1;
				h.query.end = e1;					
				h.query.grpIdx = rs.getInt("grp1_idx");					
				h.target.start = s2;
				h.target.end = e2;
				h.target.grpIdx = rs.getInt("grp2_idx");					
				ret.add(h);
			}
			rs.close();
		}
		return ret;
	}
	
	/*
	 * Sub-classes
	 */
	
	public class AnchorFile 
	{
		File mFile;
		FileType mType1, mType2;
		
		public AnchorFile(File file, String btype1, String btype2) throws Exception
		{
			mFile = file;
			mType1 = parseFileType(btype1);
			mType2 = parseFileType(btype2);
		}
		
		public FileType parseFileType(String typename) throws Exception
		{
			for (FileType ft : FileType.values())
				if (ft.toString().equalsIgnoreCase(typename))
					return ft;

			throw(new Exception("Unable to parse file type " + typename));
		}
	}
}
