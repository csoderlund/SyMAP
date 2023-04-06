package backend;

import java.io.BufferedReader;
import java.io.File;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Comparator;
import java.util.HashSet;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import symap.manager.Mproject;
import symap.Globals;
import util.Cancelled;
import util.ErrorCount;
import util.ProgressDialog;
import util.Utilities;
import util.ErrorReport;

/***********************************************
 * Load gff files for sequence projects
 */

public class AnnotLoadMain {
	static public boolean GENEN_ONLY=Globals.GENEN_ONLY; // -z CAS519b to update the gene# without having to redo synteny
	
	private ProgressDialog plog;
	private UpdatePool pool;
	private SyProj syProj;
	private Mproject mProj;

	private final String defaultTypes = 			"gene,exon,gap,centromere";
	private TreeMap<String,Integer> typeCounts = 	new TreeMap<String,Integer>();;
	private TreeSet<String> typesToLoad = 			new TreeSet<String>();
	
	// init
	private Vector<File> annotFiles = 				new Vector<File>();
	
	private final String exonAttr = "Parent"; // CAS512 save exon attribute
	private TreeMap<String,Integer> userAttr = 		new TreeMap <String,Integer>();
	private TreeMap <String, Integer> ignoreAttr = 	new TreeMap <String,Integer> ();
	private int userSetMinKeywordCnt=0;
	private boolean bUserSetKeywords = false;
	private final int savAtLeastKeywords=10; // These are columns in SyMAP Query
	
	private boolean success=true;
	private int cntGeneIdx=0;
	
	public AnnotLoadMain(UpdatePool pool, ProgressDialog log, Mproject proj) throws Exception {
		this.plog = log;
		this.pool = pool;
		this.mProj = proj;
		
		syProj = 	new SyProj(pool, log, mProj, proj.getDBName(), -1, QueryType.Either);
	}
	
	public boolean run(String projDBName) throws Exception {
		long startTime = Utils.getTime();
		
		if (GENEN_ONLY) { // CAS519b Run Gene# assignment algorithm only
			geneNOnly(projDBName);
			return success;
		}
		
		plog.msg("Loading annotation for " + projDBName);
		
		initFromParams(projDBName);	if (!success) return false;
		// deleteCurrentAnnotations();	if (!success) return false; CAS535 deleted in ManagerFraem
		
/*** LOAD FILE ***/
		int nFiles = 0;
		
		long time = Utils.getTimeMem();
		
		for (File af : annotFiles) {
			nFiles++;
			loadFile(af);	if (!success) return false;
		}
		Utils.prtMemUsage(plog, (nFiles + " file(s) loaded"), time);
		
/** Compute gene order **/
		plog.msg("Computations for " + projDBName);
		time = Utils.getTimeMem();
		
		AnnotLoadPost alp = new AnnotLoadPost(syProj, pool, plog);
		
		success = alp.run(cntGeneIdx); 	if (!success) return false;
		Utils.prtMemUsage(plog, "Computations", time);

/** Wrap up **/
		summary();		if (!success) return false;
		Utils.timeDoneMsg(plog, "All load Anno for " + projDBName, startTime);
		
		return true;
	}
	/************************************************************************8
	 * Load file - old method still used for separate gene/exon
	 */
	private void loadFile(File f) throws Exception {
	try {
		plog.msg("Loading " + f.getName());
		BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); // CAS500
		
		String line;
		int lineNum = 0, cntBatch=0, totalLoaded = 0, numParseErrors = 0;
		HashSet <String> noGrpSet = new HashSet <String> ();
		
		boolean bSkipExons=false; // only use Exons from first mRNA
		int cntMRNA=0, cntSkipMRNA=0, cntSkipGeneAttr=0, cntGene=0, cntExon=0;
		
		int lastGeneIdx=-1; // keep track of the last gene idx to be inserted
		int lastIdx=pool.getIdx("select max(idx) from pseudo_annot");
		
		PreparedStatement ps = pool.prepareStatement("insert into pseudo_annot " +
				"(grp_idx,type,name,start,end,strand,gene_idx, genenum,tag) "
				+ "values (?,?,?,?,?,?,?,0,'')"); // CAS512 remove 'text' field, added gene_idx, tag
		while ((line = fh.readLine()) != null) {
			if (Cancelled.isCancelled()) {
				plog.msg("User cancelled");
				success=false;
				break;
			}	
			lineNum++;
			if (line.startsWith("#")) continue; // skip comment
			
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
			String type 	= pool.sqlSanitize(fs[2]);
			int start		= Integer.parseInt(fs[3]);
			int end 		= Integer.parseInt(fs[4]);
			String strand	= fs[6];
			String attr		= pool.sqlSanitize(fs[8]); // CAS512 remove quotes
			
			boolean isGene = (type.contentEquals("gene")) ? true : false;
			boolean isExon = (type.contentEquals("exon")) ? true : false;
			
			// if (type.equals("CDS"))   type = "exon"; CAS512 quit using, often same as exon
			// if (type.contains("RNA") && !type.equals("mRNA")) type = "gene"; // CAS501 added !mRNA; CAS512 why RNA
			
			if (strand == null || (!strand.equals("-") && !strand.equals("+")))
				strand = "+";
			
			/** Type: After count, discard unsupported types  **/
			if (type == null || type.length() == 0) type = "unknown";
			else if (type.length() > 20) type = type.substring(0, 20);
			
			if (!typeCounts.containsKey(type)) typeCounts.put(type, 1);
			else typeCounts.put(type, 1 + typeCounts.get(type));
			
			/** only use exons from first mRNA **/
			if (type.equals("mRNA")) {
				cntMRNA++; 
				if (cntMRNA>1) 					 {bSkipExons=true; cntSkipMRNA++;}
			}
			else if (type.contentEquals("gene")) {bSkipExons=false; cntMRNA=0;}
			
			if (typesToLoad.size() > 0 &&  !typesToLoad.contains(type)) continue;   
			if (bSkipExons) continue; 
			
	/** Process for write **/
			/** Chromosome **/
			int grpIdx = syProj.grpIdxFromQuery(chr);
			if (grpIdx < 0) {// ErrorCount.inc(); CAS502 this is not an error; can happen if scaffolds have been filtered out
				if (!noGrpSet.contains(chr)) {
					if (noGrpSet.size() < 3) plog.msgToFile("+++ No loaded sequence for '" + chr + "' on line " + lineNum);
					else if (noGrpSet.size() == 3) plog.msgToFile("+++ Suppressing further warnings of no loaded sequence");
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
		/** Annotation Keywords **/	
			String parsedAttr="";	// MySQL text: Can hold up to 65k
			
			if (isExon) { // CAS512 was not using any attr; now using parent or first
				cntExon++;
				String[] keyValExon = attr.split(";"); 
				
				for (String keyVal : keyValExon) {
					String[] words = keyVal.trim().split("=");
					String key = words[0].trim();
					if (words.length!=2) continue;
					if (key.equals("")) continue;
					
					if (key.endsWith("="))
						key = key.substring(0,key.length() - 1);
					if (key.contentEquals(exonAttr))  {
						parsedAttr = keyVal.trim() + ";";
						break;
					}
				}
				if (parsedAttr.contentEquals("") && keyValExon.length>0)
					parsedAttr = keyValExon[0] + ";";
			}
			else if (isGene) { // CAS501 changed to create string of only user-supplied keywords
				cntGene++;
				String[] keyValGene = attr.split(";"); // 	IF CHANGED, CHANGE CODE IN QUERY PAGE PARSING ALSO
				
				for (String keyVal : keyValGene) {
					String[] words = keyVal.trim().split("="); // CAS513 if no '=', then no keyword
					if (words.length!=2) {
						cntSkipGeneAttr++;
						continue;
					}
					String key = words[0].trim();
					if (key.equals("")) continue;
					
					if (bUserSetKeywords) { 
						if (userAttr.containsKey(key)) {
							userAttr.put(key, 1 + userAttr.get(key));
							parsedAttr += keyVal.trim() + ";"; 
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
					
				if (parsedAttr.equals("") && type.equals("gene")) parsedAttr="[no description]"; // CAS503
			}
			else parsedAttr = attr;
			
			int gene_idx=0;
			if (isExon) {
				if (lastGeneIdx>0) {
					gene_idx=lastGeneIdx;
					cntGeneIdx++; // for AnnotLoadPost
				}
			}
	
		/** Load annotation into database **/
			ps.setInt(1,grpIdx);
			ps.setString(2,type);
			ps.setString(3,parsedAttr);
			ps.setInt(4,start);
			ps.setInt(5,end);
			ps.setString(6,strand);
			ps.setInt(7, gene_idx);
			
			ps.addBatch();
			totalLoaded++; cntBatch++; lastIdx++;
			
			if (cntBatch==1000) {
				cntBatch=0;
				ps.executeBatch();
				System.out.print("   " + totalLoaded + " annotations...\r"); // CAS42 1/1/18
			}
			if (isGene) {
				lastGeneIdx=lastIdx;
			}
		}
		if (cntBatch> 0) ps.executeBatch();
		ps.close();
		fh.close();
		
		plog.msg("   " + totalLoaded + " annotations loaded from " + f.getName());
		if (cntGene>0 || cntExon>0) { // CAS518 no longer supporting exons in separate file
			plog.msg("   " + cntGene + " genes; " + cntExon + " exons");
			if (cntGene==0) plog.msg("Warning: genes and exons must be in the same file for accurate results");
		}
		if (cntSkipGeneAttr>0) plog.msg("   " + cntSkipGeneAttr + " skipped gene attribute with no '='");
		if (cntSkipMRNA>0)	   plog.msg("   " + cntSkipMRNA + " skipped mRNA and exons");
		if (numParseErrors>0)  plog.msg("   " + numParseErrors + " parse errors - lines discarded");
		if (noGrpSet.size() >0)   plog.msg("   " + noGrpSet.size() + " sequence names in annotation file not loaded into database");
	}
	catch (Exception e) {ErrorReport.print(e, "Load file"); success=false;}
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
					plog.msg("");
					return; 			// this is not considered an error
				}
				for (File f2 : ad.listFiles()) {
					if (!f2.isFile() || f2.isHidden()) continue; // CAS511 macos add ._ files in tar
					
					annotFiles.add(f2);
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
			
			// Types: if no types specified then limit to types we render
			String annot_types =  mProj.getAnnoType(); // not a parameter
			
			if (annot_types == null || annot_types.length() == 0) 
				annot_types = defaultTypes;
			
			String [] ats = annot_types.split("\\s*,\\s*");
			for (String at : ats) {
				at = at.trim();
				if (at.length() > 0) typesToLoad.add(at);
			}
		
			// Keywords: CAS501 regardless if Keywords were entered into the Params interface,
			// they were still be shown on the 2D display. Now they are not entered into
			// the database unless they are in the list (if there is a list).
			
			// Parse user-specified types
			userSetMinKeywordCnt = mProj.getdbMinKey();
			String attrKW = mProj.getKeywords();
			if (!attrKW.contentEquals("")) plog.msg("  " + mProj.getLab(mProj.sANkeyCnt) + " " + attrKW);
			
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
		catch (Exception e) {ErrorReport.print(e, "load anno init"); success=false;}
	}
	/********************** wrap up ***********************/
	private void summary() {
		try {
			// Print type counts
			plog.msg("GFF Types (* indicates not loaded):");
			for (String type : typeCounts.keySet()) {
				String ast = (!typesToLoad.contains(type) ? "*" : ""); // CAS42 1/1/18 formatted output
				plog.msg(String.format("   %-15s %d%s", type, typeCounts.get(type), ast));
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
		
			pool.executeUpdate("delete from annot_key where proj_idx=" + syProj.getIdx());
	        for (String key : sortedKeys) {
	        	cntSav++;
	        	if (cntSav>savAtLeastKeywords) {
	        		plog.msg(sortedKeys.size() + " Attribute keywords; skip remaining....");
	        		break;
	        	}
	        	int count = userAttr.get(key);
	        	
	        	plog.msg(String.format("   %-15s %d", key,count)); 
	        	pool.executeUpdate("insert into annot_key (proj_idx,keyname,count) values (" + 
	        			syProj.getIdx() + ",'" + key + "'," + count + ")");
	        }
	        if (ignoreAttr.size()>0)  {
				plog.msg("Ignored attribute keywords: ");
				for (String key : ignoreAttr.keySet()) {
					int count = ignoreAttr.get(key);
					if (count>userSetMinKeywordCnt)
						plog.msg(String.format("   %-15s %d", key, count));
				}
	        }
			// Release data from heap
			typesToLoad = null;
			typeCounts = null;
			System.gc(); // Java treats this as a suggestion
		}
		catch (Exception e) {ErrorReport.print(e, "Compute gene order"); success=false;}
	}
	private boolean geneNOnly(String projName) { // CAS519b
		try {
			ResultSet rs = pool.executeQuery("select hasannot from projects where idx=" + syProj.getIdx());
			int cnt=0;
			if (rs.next()) cnt=rs.getInt(1);
			if (cnt>0) {
				rs = pool.executeQuery("select count(*) from pseudo_annot " + 
						"join xgroups on pseudo_annot.grp_idx = xgroups.idx " + 
						"WHERE pseudo_annot.type = 'gene' and xgroups.proj_idx = " + syProj.getIdx());
				if (rs.next()) cnt=rs.getInt(1);
			}
			rs.close();
			if (cnt==0) {
				Utilities.showWarningMessage("Project has no annotation, so cannot update Gene#");
				return true;
			}
				
			plog.msg("Run Gene# assignment algorithm for " + syProj.getName());
			long time = Utils.getTime();
			
			AnnotLoadPost alp = new AnnotLoadPost(syProj, pool, plog);
			success = alp.run(cnt); 	if (!success) return false;
			
			Utils.timeDoneMsg(plog, "Computations", time);
		}
		catch (Exception e) {ErrorReport.print(e, "checking for annnotations"); }
		return false;
	}
}
