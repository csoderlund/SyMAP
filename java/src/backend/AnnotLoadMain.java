package backend;

import java.io.BufferedReader;
import java.io.File;
import java.util.Collections;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Comparator;
import java.util.HashSet;
import java.sql.PreparedStatement;

import database.DBconn2;
import symap.Globals;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ProgressDialog;
import util.Utilities;
import util.ErrorReport;

/***********************************************
 * Load gff files for sequence projects: recognize gene, mRNA, exon, gap, centromere
 * CAS557 made major changes to check mRNA parent with geneID and exon parent with mrnaID.
 * 		This now allows reading of NCBI and Ensembl files directly.
 */

public class AnnotLoadMain {
	//static public boolean GENEN_ONLY=Globals.GENEN_ONLY; // CAS560 remove; -z CAS519b to update the gene# without having to redo synteny
	private final String idKey 		= "ID"; 			// Gene and mRNA
	private final String parentKey 	= "Parent"; 		// mRNA and Exon
	
	private ProgressDialog plog;
	private DBconn2 tdbc2;
	private Mproject mProj;		// contains chromosomes and the grpIdx from reading FASTA

	private TreeMap<String,Integer> typeCounts = 	new TreeMap<String,Integer>();;
	
	// init
	private Vector<File> annotFiles = 				new Vector<File>();
	
	private final String exonAttr = "Parent"; 		// save exon attribute; CAS512 
	private TreeMap<String,Integer> userAttr = 		new TreeMap <String,Integer>();
	private TreeMap <String, Integer> ignoreAttr = 	new TreeMap <String,Integer> ();
	private int userSetMinKeywordCnt=0;
	private boolean bUserSetKeywords = false;
	private final int savAtLeastKeywords=10; 		// These are columns in SyMAP Query
	
	private boolean bSuccess=true;
	private int cntAllGene=0 , cntNoKey=0;
	
	public AnnotLoadMain(DBconn2 dbc2, ProgressDialog log, Mproject proj) throws Exception {
		this.tdbc2 = new DBconn2("AnnoLoad-"+ DBconn2.getNumConn(), dbc2);;
		this.plog = log;
		this.mProj = proj;
		proj.loadDataFromDB();
	}
	
	public boolean run(String projDBName) throws Exception {
		long startTime = Utils.getTime();
		
		plog.msg("Loading annotation for " + projDBName);
		
		initFromParams(projDBName);	if (!bSuccess) {tdbc2.close(); return false; };
		// deleteCurrentAnnotations();	if (!success) return false; CAS535 deleted in ManagerFraem
		
		if (annotFiles.size()==0) { // CAS556 was going through rest of code
			Utils.prt(plog, "Finish annotation");
			tdbc2.executeUpdate("delete from annot_key where proj_idx=" + mProj.getIdx());
			tdbc2.close();
			return true;
		}
		
/*** LOAD FILE ***/
		int nFiles = 0;
		
		long time = Utils.getTimeMem();
		
		for (File af : annotFiles) {
			nFiles++;
			loadFile(af);	if (!bSuccess) {tdbc2.close(); return false; }
		}
		Utils.prtTimeMemUsage(plog, (nFiles + " file(s) loaded"), time);
		
/** Compute gene order **/
		plog.msg("Computations for " + projDBName);
		time = Utils.getTimeMem();
		
		AnnotLoadPost alp = new AnnotLoadPost(mProj, tdbc2, plog);
		
		bSuccess = alp.run(cntAllGene>0); 	if (!bSuccess) {tdbc2.close(); return false; }
		Utils.prtTimeMemUsage(plog, "Finish computations", time);

/** Wrap up **/
		summary();		if (!bSuccess) {tdbc2.close(); return false; }
		Utils.timeDoneMsg(plog, "Finish annotation for " + projDBName + "      ", startTime);
		
		tdbc2.close();
		return true;
	}
	/************************************************************************8
	 * Load file and write to DB
	 */
	private void loadFile(File f) throws Exception {
		int grpIdx=0;
	try {
		plog.msg("Loading " + f.getName());
		BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); // CAS500
		
		String line;
		int lineNum = 0, cntBatch=0, totalLoaded = 0, numParseErrors = 0;
		HashSet <String> noGrpSet = new HashSet <String> ();
		
		int cntSkipMRNA=0, cntSkipGeneAttr=0, cntGene=0, cntExon=0;
		
		int lastGeneIdx = -1; // keep track of the last gene idx to be inserted
		int lastIdx = tdbc2.getIdx("select max(idx) from pseudo_annot");
		String geneID=null, mrnaID=null;
		
		PreparedStatement ps = tdbc2.prepareStatement("insert into pseudo_annot " +
				"(grp_idx,type,name,start,end,strand,gene_idx, genenum,tag) "
				+ "values (?,?,?,?,?,?,?,0,'')"); 			// CAS512 remove 'text' field, added gene_idx, tag
		while ((line = fh.readLine()) != null) {
			if (Cancelled.isCancelled()) {
				plog.msg("User cancelled");
				bSuccess=false;
				break;
			}	
			
			lineNum++;
			if (line.startsWith("#")) continue; 			// skip comment
			if (Utilities.isEmpty(line.trim())) continue; 	// CAS543
			
			String[] fs = line.split("\t");
			if (fs.length < 9) {
				ErrorCount.inc();
				numParseErrors++;
				if (numParseErrors <= 3) {
					plog.msgToFile("*** Parse: expecting at least 9 tab-delimited fields in gff file at line " + lineNum);
					if (numParseErrors >= 3) plog.msgToFile("*** Suppressing further parse errors");
				}
				continue; // skip this annotation
			}
			String chr 		= fs[0];
			//String source = fs[1];	// ignored
			String type 	= sqlSanitize(fs[2]);
			int start		= Integer.parseInt(fs[3]);
			int end 		= Integer.parseInt(fs[4]);
			String strand	= fs[6];
			String attr		= sqlSanitize(fs[8]); // CAS512 remove quotes
			
			if (strand == null || (!strand.equals("-") && !strand.equals("+"))) strand = "+";
		
			/** Type: After count, discard unsupported types  **/
			if (type == null || type.length() == 0) type = "unknown";
			else if (type.length() > 20) type = type.substring(0, 20);
			
			if (!typeCounts.containsKey(type)) typeCounts.put(type, 1);
			else typeCounts.put(type, 1 + typeCounts.get(type));
			
			/** only use exons from first mRNA **/
			boolean isGene = (type.contentEquals(Globals.geneType)) ? true : false; // remove hardcode
			boolean isExon = (type.contentEquals(Globals.exonType)) ? true : false;
			boolean isMRNA = (type.contentEquals("mRNA")) ? true : false;			// this is nowhere else
			boolean isGap =  (type.contentEquals(Globals.gapType))  ? true : false;
			boolean isCent = (type.contentEquals(Globals.centType)) ? true : false;
			if (!(isGene || isExon || isMRNA || isGap || isCent)) continue;
			
			String[] keyVals = attr.split(";"); 
			
			if (isGene)   {
				mrnaID = null;
				geneID = getVal(idKey, keyVals);
			} 
			else if (isMRNA) {
				if (geneID!=null && mrnaID==null) {
					mrnaID = getmRNAid(geneID, keyVals, lineNum, line); 
					if (!bSuccess) return;
				}
				else if (geneID!=null) cntSkipMRNA++;
				continue;
			}
			else if (isExon) {
				if (mrnaID==null) continue;
				
				if (!isGoodExon(mrnaID, keyVals, lineNum, line)) {
					if (!bSuccess) return;
					continue;
				}
			}
			// else isGap or isCent
			
	/** Process for write **/
			// Chromosome idx
			grpIdx = mProj.getGrpIdxRmPrefix(chr); // CAS546 was syProj.grpIdxFromQuery
			if (grpIdx < 0) {// ErrorCount.inc(); CAS502 this is not an error; can happen if scaffolds have been filtered out
				if (!noGrpSet.contains(chr)) {
					if (noGrpSet.size() < 3) plog.msgToFile("+++ Gene on sequence '" + chr + "'; sequence is not loaded - ignore");
					else if (noGrpSet.size() == 3) plog.msgToFile("+++ Suppressing further warnings of no loaded sequence");
					if (noGrpSet.size()==0) System.out.println("Valid names: " + mProj.getValidGroup());
					noGrpSet.add(chr);
				}
				continue; // skip this annotation
			}
			// Swap coordinates so that start is always less than end.
			if (start > end) {
				int tmp = start;
				start = end;
				end = tmp;
			}
		    // Annotation Keywords 	
			String parsedAttr="";	// MySQL text: Can hold up to 65k
			
			if (isGene) { // create string of only user-supplied keywords; CAS501 changed 
				cntGene++; 
				lastGeneIdx = (lastIdx+1); // for exon 
				
				for (String kv : keyVals) {
					String[] words = kv.trim().split("="); // if no '=', then no keyword; CAS513 
					if (words.length!=2) {
						if (!kv.trim().isEmpty()) { // CAS558 happens with convert no desc; two ;;
							cntSkipGeneAttr++;
							if (cntSkipGeneAttr<2) symap.Globals.tprt(line);
						}
						continue;
					}
					String key = words[0].trim();
					if (key.equals("")) continue;
					
					if (bUserSetKeywords) { 
						if (userAttr.containsKey(key)) {
							userAttr.put(key, 1 + userAttr.get(key));
							parsedAttr += kv.trim() + ";"; 
						}
						else {
							if (!ignoreAttr.containsKey(key)) ignoreAttr.put(key, 0);
							ignoreAttr.put(key, 1 + ignoreAttr.get(key));
						}
					}
					else {									// no keywords excluded
						if (!userAttr.containsKey(key)) userAttr.put(key, 0);
						userAttr.put(key, 1 + userAttr.get(key));
					}
				}
				if (bUserSetKeywords) {
					if (parsedAttr.endsWith(";")) 
						parsedAttr = parsedAttr.substring(0, parsedAttr.length()-1); // remove ; at end
				}
				else parsedAttr = attr;						// no keywords excluded
					
				if (parsedAttr.equals("")) parsedAttr="[no description]"; // CAS503
			}
			else if (isExon) { // using parent or first; CAS512 added (not used in viewSymap)
				cntExon++;
				
				for (String kv : keyVals) {
					String[] words = kv.trim().split("=");
					String key = words[0].trim();
					if (words.length!=2) continue;
					if (key.equals("")) continue;
					
					if (key.endsWith("="))
						key = key.substring(0,key.length() - 1);
					if (key.contentEquals(exonAttr))  {
						parsedAttr = kv.trim() + ";";
						break;
					}
				}
				if (parsedAttr.contentEquals("") && keyVals.length>0)
					parsedAttr = keyVals[0] + ";";
			}
			// else isGap or isCent
			
		/** Load annotation into database **/
			int gidx = (isGene) ? 0 : lastGeneIdx;
			ps.setInt(1,grpIdx);
			ps.setString(2,type);
			ps.setString(3,parsedAttr);
			ps.setInt(4,start);
			ps.setInt(5,end);
			ps.setString(6,strand);
			ps.setInt(7, gidx);
			
			ps.addBatch();
			totalLoaded++; cntBatch++; lastIdx++;
			
			if (cntBatch==1000) {
				cntBatch=0;
				ps.executeBatch();
				Globals.rprt(totalLoaded + " annotations"); // CAS42 1/1/18; CAS561 use rprt
			}
		}
		if (cntBatch> 0) ps.executeBatch();
		ps.close();
		fh.close();
		
		cntAllGene+=cntGene;
		Utils.prtNumMsg(plog, totalLoaded, "annotations loaded from " + f.getName()); // CAS558 add commas
		if (cntGene>0 || cntExon>0) { 				// CAS518 no longer supporting exons in separate file
			Utils.prtNumMsg(plog, cntGene, String.format("genes  %,d exons", cntExon));
			if (cntGene==0) plog.msg("Warning: genes and exons must be in the same file for accurate results");
		}
		if (cntSkipGeneAttr>0)  Utils.prtNumMsg(plog,cntSkipGeneAttr, "skipped gene attribute with no '='");
		if (cntSkipMRNA>0)	    Utils.prtNumMsg(plog,cntSkipMRNA, "skipped mRNA and exons");
		if (numParseErrors>0)   Utils.prtNumMsg(plog,numParseErrors, "parse errors - lines discarded");
		if (noGrpSet.size() >0) Utils.prtNumMsg(plog,noGrpSet.size(), "sequence names in annotation file not loaded into database");
	}
	catch (Exception e) {
		System.err.println("");
		System.err.println("*** Database index problem: try Load again (it generally works on 2nd try)");
		System.err.println("");
		ErrorReport.print(e, "Load file grpIdx " + grpIdx + " - try Load again."); 
		bSuccess=false;}
	}
	private String getVal(String key, String [] attrs) {
		for (String s : attrs) {
			String [] x = s.split("=");
			if (x[0].equals(key)) return x[1];
		}
		return null;
	}
	private String getmRNAid(String geneID, String [] keyVal, int lineNum, String line) {
		String mrnaID = getVal(idKey, keyVal);
		if (mrnaID==null) {
			plog.msgToFile("+++ missing mRNA ID on line " + lineNum);
			plog.msgToFile("line: " + line);
			cntNoKey++;
			if (cntNoKey>5) {
				bSuccess=false;
				plog.msgToFile("Fatal error: missing keywords ");
				return null;
			}
			String parent = getVal(parentKey, keyVal);
			if (parent==null) {
				plog.msgToFile("+++ missing mRNA parent on line " + lineNum + " Last Parent '" + geneID);
				plog.msgToFile("line: " + line);
				cntNoKey++;
				if (cntNoKey>5) {
					bSuccess=false;
					plog.msgToFile("Fatal error: missing keywords ");
					return null;
				}
			}
			if (!parent.equals(geneID)) {
				plog.msgToFile("+++ missing mRNA parent on line " + lineNum + " Last Parent '" + geneID);
				plog.msgToFile("line: " + line);
				cntNoKey++;
				if (cntNoKey>5) {
					bSuccess=false;
					plog.msgToFile("Fatal error: missing keywords ");
					return null;
				}
			}
		}
		return mrnaID;
	}
	private boolean isGoodExon(String mrnaID, String [] keyVal, int lineNum, String line) {
		String parent = getVal(parentKey, keyVal);
		if (parent==null) {
			plog.msgToFile("+++ missing mRNA parent on line " + lineNum + " Last Parent '" + mrnaID);
			plog.msgToFile("line: " + line);
			cntNoKey++;
			if (cntNoKey>5) {
				bSuccess=false;
				plog.msgToFile("Fatal error: missing keywords ");
				return false;
			}
		}
		return parent.equals(mrnaID);
	}

	// Eliminate all quotes in strings before DB insertion, though do not need with PreparedStatement. CAS541 moved from UpdatePool
	static Pattern mpQUOT = Pattern.compile("[\'\"]");;
	public String sqlSanitize(String in) {
		Matcher m = mpQUOT.matcher(in);
		return m.replaceAll("");
	}	
	/************* Initialize local data *****************/
	private void initFromParams(String projName) {
		try {		
			String projDir = 	Constants.seqDataDir + projName;
			
		// Files init
			String annoFiles = mProj.getAnnoFile();
			String saveAnnoDir="";
			long modDirDate=0;
			
			if (annoFiles.equals("")) {// Check for annotation directory
				String annotDir = 	projDir + Constants.seqAnnoDataDir;
				plog.msg("   Anno_files not specified - use " + annotDir);
				File ad = new File(annotDir);
				if (!ad.isDirectory()) {
					plog.msg("   No annotation files provided");
					return; 			// this is not considered an error
				}
				for (File f2 : ad.listFiles()) {
					if (!f2.isFile() || f2.isHidden()) continue; // CAS511 macos add ._ files in tar
					
					annotFiles.add(f2);
				}
				if (annotFiles.size()==0) { // CAS556 add
					plog.msg("   No annotation files provided");
					return; 			// this is not considered an error
				}
				saveAnnoDir=annotDir;
			}
			else {
				String[] fileList = annoFiles.split(",");
				String xxx = (fileList.length>1) ? (fileList.length + " files ") : annoFiles;
				plog.msg("   User specified annotation files - " + xxx);
				
				for (String filstr : fileList) {
					if (filstr == null) continue;
					if (filstr.trim().equals("")) continue;
					
					File f = new File(filstr);
					if (!f.exists()) {
						plog.msg("***Cannot find annotation file " + filstr);
					}
					else if (f.isDirectory()) {
						saveAnnoDir=filstr;
						for (File f2 : f.listFiles()) {
							if (!f2.isFile() || f2.isHidden()) continue; // CAS511 macos add ._ files in tar
							
							annotFiles.add(f2);
						}
					}
					else {
						saveAnnoDir=Utils.pathOnly(filstr);
						annotFiles.add(f);
					}
				}
			}
			if (saveAnnoDir!="") {// CAS532 add these to print on View
				modDirDate = new File(saveAnnoDir).lastModified();
				mProj.saveProjParam("proj_anno_date", Utils.getDateStr(modDirDate));
				mProj.saveProjParam("proj_anno_dir", saveAnnoDir);
			}
			
			// Keywords: CAS501 regardless if Keywords were entered into the Params interface,
			// they were still be shown on the 2D display. Now they are not entered into
			// the database unless they are in the list (if there is a list).
			
			// Parse user-specified types
			userSetMinKeywordCnt = mProj.getdbMinKey();
			String attrKW = mProj.getKeywords();
			if (!attrKW.contentEquals("")) plog.msg("   " + mProj.getLab(mProj.sANkeyCnt) + " " + attrKW); // CAS558 add space
			
			// if ID is not included, it all still works...
			bUserSetKeywords = (attrKW.equals("")) ? false : true;
			if (bUserSetKeywords) {
				if (attrKW.contains(",")) {
					String[] kws = attrKW.trim().split(",");
					for (String key : kws) userAttr.put(key.trim(), 0);
				}
				else userAttr.put(attrKW.trim(), 0); // CAS502
			}
		}
		catch (Exception e) {ErrorReport.print(e, "load anno init"); bSuccess=false;}
	}
	/********************** wrap up ***********************/
	private void summary() {
		try {
			// Print type counts
			plog.msg("GFF Types:");
			for (String type : typeCounts.keySet()) {
				plog.msg(String.format("   %-15s %,d", type, typeCounts.get(type)));
			}
			
			Vector<String> sortedKeys = new Vector<String>();
			sortedKeys.addAll(userAttr.keySet());
			Collections.sort(sortedKeys, new Comparator<String>() {
		        public int compare(String o1, String o2) {
		            return userAttr.get(o2).compareTo(userAttr.get(o1));
		        }
	        });
			
			int cntSav=0;
			if (bUserSetKeywords) plog.msg("User specified attribute keywords: ");
			else plog.msg("Best attribute keywords:");
		
			tdbc2.executeUpdate("delete from annot_key where proj_idx=" + mProj.getIdx());
	        for (String key : sortedKeys) {
	        	cntSav++;
	        	if (cntSav>savAtLeastKeywords) {
	        		plog.msg(sortedKeys.size() + " Attribute keywords; skip remaining....");
	        		break;
	        	}
	        	int count = userAttr.get(key);
	        	
	        	plog.msg(String.format("   %-15s %,d", key,count)); 
	        	tdbc2.executeUpdate("insert into annot_key (proj_idx,keyname,count) values (" + 
	        			mProj.getIdx() + ",'" + key + "'," + count + ")");
	        }
	        if (ignoreAttr.size()>0)  {
				plog.msg("Ignored attribute keywords: ");
				for (String key : ignoreAttr.keySet()) {
					int count = ignoreAttr.get(key);
					if (count>userSetMinKeywordCnt)
						plog.msg(String.format("   %-15s %,d", key, count));
				}
	        }
			// Release data from heap
			typeCounts = null;
			System.gc(); // Java treats this as a suggestion
		}
		catch (Exception e) {ErrorReport.print(e, "Compute gene order"); bSuccess=false;}
	}
}
