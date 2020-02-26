package backend;

/***********************************************
 * Load gff files for sequence projects
 */
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;

import java.util.Collections;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Comparator;

import java.sql.SQLException;
import java.sql.PreparedStatement;

import symap.pool.DatabaseUser;
import util.Cancelled;
import util.ErrorCount;
import util.Logger;
import util.ErrorReport;

public class AnnotLoadMain 
{
	private Logger log;
	private UpdatePool pool;
	private SyProps props;
	private Project project;
	private int projIdx;
	private String projDir;
	private TreeMap<String,Integer> typeCounts;
	private TreeSet<String> typesToLoad;
	private static Properties mDBProps = null;
	
	public AnnotLoadMain(UpdatePool pool, Logger log, Properties props) {
		this.log = log;
		this.pool = pool;
		if (props != null) mDBProps = props;
	}
	
	public boolean run(String projName) throws Exception {
		long startTime = System.currentTimeMillis();
		
		log.msg("Loading annotation for " + projName);

		pool.updateSchemaTo40();

		// Initialize local data
		typeCounts = new TreeMap<String,Integer>();
		typesToLoad = new TreeSet<String>();
		projDir = Constants.seqDataDir + projName;
		props = new SyProps(log, new File(projDir + Constants.paramsFile), mDBProps);
		project = new Project(pool, log, props, projName, ProjType.pseudo, QueryType.Either);
		projIdx = project.getIdx();
		
		// Parse user-specified types
		String annot_types = 	props.getProperty("annot_types");
		Integer keywordMin = 	props.getInt("annot_kw_mincount");
		String annotKW = 		props.getString("annot_keywords");
		boolean bHasKey = (annotKW.equals("")) ? false : true;
		
		// if no types specified then limit to types we render
		if (annot_types == null || annot_types.length() == 0) 
			annot_types = "gene,exon,frame,gap,centromere";
		
		if (annot_types != null && !annot_types.equals(""))
		{
			String [] ats = annot_types.split("\\s*,\\s*");
			for (String at : ats) {
				at = at.trim();
				if (at.length() > 0)
					typesToLoad.add(at);
			}
		}

		String annotDir = projDir + Constants.seqAnnoDataDir;
		Vector<File> annotFiles = new Vector<File>();

		if (!props.containsKey("anno_files")) props.setProperty("anno_files", "");

		if (props.getProperty("anno_files").equals(""))
		{
			// Check for annotation directory
			log.msg("anno_files not specified - use " + annotDir);
			File ad = new File(annotDir);
			if (!ad.isDirectory()) {
				log.msg("No annotation files provided");
				log.msg("");
				return true; // this is not considered an error
			}
			
			for (File f2 : ad.listFiles()) 
				annotFiles.add(f2);
		}
		else
		{
			String userFiles = props.getProperty("anno_files");
			String[] fileList = userFiles.split(",");
			log.msg("User specified annotation files - " + userFiles);
			
			for (String filstr : fileList)
			{
				if (filstr == null) continue;
				if (filstr.trim().equals("")) continue;
				File f = new File(filstr);
				if (!f.exists()) {
					log.msg("Cannot find annotation file " + filstr);
				}
				else if (f.isDirectory()) {
					for (File f2 : f.listFiles()) 
						annotFiles.add(f2);
				}
				else {
					annotFiles.add(f);
				}
			}
		}
			
		// Load annotation files
		deleteCurrentAnnotations();
		pool.executeUpdate("delete from pairs " +
				" where proj1_idx=" + projIdx + " or proj2_idx=" + projIdx);
		
		int nFiles = 0;
		
		// CAS501 regardless if Keywords were entered into the Params interface,
		// they were still be shown on the 2D display. Now they are not entered into
		// the database unless they are in the list (if there is a list).
		final TreeMap<String,Integer> keywords = new TreeMap<String,Integer>();
		TreeMap <String, Integer> ignoredKey = new TreeMap <String, Integer> ();
		
		// if ID is not included, it all still works...
		if (bHasKey) {
			if (annotKW.contains(",")) {
				String[] kws = annotKW.trim().split(",");
				for (String key : kws) keywords.put(key.trim(), 0);
			}
			else keywords.put(annotKW.trim(), 0); // CAS502
			log.msg("User specified keywords (" + keywords.size() + "): " + annotKW );
		}
		
		for (File af : annotFiles) {
			if (!af.isFile()) continue;
			
			nFiles++;
			loadFile(af, bHasKey, keywords, ignoredKey);
			if (Cancelled.isCancelled()) {
				log.msg("User cancelled");
				return false; // CAS500 dec19 was 'break'
			}
		}
		log.msg(nFiles + " file(s) loaded");
				
		pool.executeUpdate("update projects set hasannot=1,annotdate=NOW() where idx=" + projIdx);
		
		// Print type counts
		log.msg("GFF Types (* indicates not loaded):");
		for (String type : typeCounts.keySet()) {
			String ast = (!typesToLoad.contains(type) ? "*" : ""); // CAS42 1/1/18 formatted output
			log.msg(String.format("   %-15s %d%s", type, typeCounts.get(type), ast));
		}
		
		// Print annotation keyword counts
		Vector<String> sortedKeys = new Vector<String>();
		sortedKeys.addAll(keywords.keySet());
		Collections.sort(sortedKeys, new Comparator<String>() {
	        public int compare(String o1, String o2) {
	            return keywords.get(o2).compareTo(keywords.get(o1));
	        }
        });
		if (!bHasKey) 
			log.msg("All annotation keywords (with count >= " + keywordMin + "):");
		else 
			log.msg("User specified annotation keywords:");
		pool.executeUpdate("delete from annot_key where proj_idx=" + projIdx);
        for (String key : sortedKeys)
        {
	        	int count = keywords.get(key);
	        	if (!bHasKey && count < keywordMin) break; // been sorted on count
	        	
	        	log.msg(String.format("   %-15s %d", key,count)); // CAS42 1/1/18 formated output
	        	pool.executeUpdate("insert into annot_key (proj_idx,keyname,count) values (" + 
        			projIdx + ",'" + key + "'," + count + ")");
        }
        if (bHasKey && ignoredKey.size()>0)  {
			log.msg("Ignored keywords (with count >= " + keywordMin + "):" );
			for (String key : ignoredKey.keySet()) {
				int count = ignoredKey.get(key);
	        		if (count >= keywordMin) 
	        			log.msg(String.format("   %-15s %d", key, count));
			}
        }
		// Release data from heap
		typesToLoad = null;
		typeCounts = null;
		System.gc(); // Java treats this as a suggestion
		
		Utils.timeMsg(log, startTime, "Load Anno");
		
		return true;
	}
	
	private void loadFile(File f, boolean bHasKey, TreeMap<String,Integer> keywords, 
				TreeMap<String,Integer> ignoredKey) throws Exception
	{
		log.msg("Loading " + f.getName());
		BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); // CAS500
		
		String line;
		int lineNum = 0, cntBatch=0, totalLoaded = 0;
		int numParseErrors = 0, numGrpErrors = 0;
		final int MAX_ERROR_MESSAGES = 3;
		
		PreparedStatement ps = pool.prepareStatement("insert into pseudo_annot " +
				"(grp_idx,type,name,start,end,strand,text) values (?,?,?,?,?,?,'')"); 
		while ((line = fh.readLine()) != null)
		{
			if (Cancelled.isCancelled()) break;
				
			lineNum++;
			if (line.startsWith("#")) continue; // skip comment
			
			String[] fs = line.split("\t");
			if (fs.length < 9) {
				ErrorCount.inc();
				if (numParseErrors++ < MAX_ERROR_MESSAGES) {
					log.msg("Parse error: expecting at least 9 tab-delimited fields in gff file at line " + lineNum);
					if (numParseErrors >= MAX_ERROR_MESSAGES)
						log.msg("(Suppressing further errors of this type)");
				}
				continue; // skip this annotation
			}
			String chr 		= fs[0];
			String type1 	= fs[1];
			String type 		= pool.sqlSanitize(fs[2]);
			int start		= Integer.parseInt(fs[3]);
			int end 			= Integer.parseInt(fs[4]);
			String strand	= fs[6];
			String attr		= fs[8];
			
			// CHECK - should this be done? should CDS and RNA be 
			if (type.equals("CDS"))   type = "exon";
			if (type.contains("RNA") && !type.equals("mRNA")) type = "gene"; // CAS501 added !mRNA
			
			if (strand == null || (!strand.equals("-") && !strand.equals("+")))
				strand = "+";
			
		/** type **/
			if (type == null || type.length() == 0) type = "unknown";
			else if (type.length() > 20) type = type.substring(0, 20);
			
			// RepeatMasker doesn't follow the gff standard
			if (type1.equals("RepeatMasker") && type.equals("similarity"))
            		type = "RepeatMasker";
			
			if (!typeCounts.containsKey(type)) typeCounts.put(type, 1);
			else typeCounts.put(type, 1 + typeCounts.get(type));
			
			// Discard unsupported types as specified in project params file.
			if (typesToLoad.size() > 0 &&  !typesToLoad.contains(type)) 
				continue; // skip this annotation
		
		/** Chromosome **/
			int grpIdx = project.grpIdxFromQuery(chr);
			if (grpIdx < 0) {
				// ErrorCount.inc(); CAS502 this is not an error; can happen if scaffolds have been filtered out
				if (numGrpErrors++ < MAX_ERROR_MESSAGES) {
					log.msg("Warn: No loaded group (e.g. chr) sequence for " + chr + " on line " + lineNum);
					if (numGrpErrors >= MAX_ERROR_MESSAGES)
						log.msg("(Suppressing further errors of this type)");
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
			// Don't load exon descriptions as they are the same as the corresponding gene descriptions.
			if (type.equals("exon")) attr="";
			
			// CAS501 changed to create string of only user-supplied keywords
			String parsedAttr="";
			
			String[] fields = attr.split(";"); // 	IF CHANGED, CHANGE CODE IN QUERY PAGE PARSING ALSO
			for (String field : fields)
			{
				String[] words = field.trim().split("[\\s,=]");
				String key = words[0].trim();
				if (key.equals("")) continue;
				
				if (key.endsWith("="))
					key = key.substring(0,key.length() - 1);
				
				if (bHasKey) { 
					if (keywords.containsKey(key)) {
						keywords.put(key, 1 + keywords.get(key));
						parsedAttr += field + ";"; 
					}
					else {
						if (!ignoredKey.containsKey(key)) ignoredKey.put(key, 0);
						ignoredKey.put(key, 1 + ignoredKey.get(key));
					}
				}
				else {
					if (!keywords.containsKey(key)) keywords.put(key, 0);
					keywords.put(key, 1 + keywords.get(key));
				}
			}
			if (bHasKey) {
				if (parsedAttr.endsWith(";")) 
					parsedAttr = parsedAttr.substring(0, parsedAttr.length()-1);
			}
			else parsedAttr = attr;
				
			// Load annotation into database
			ps.setInt(1,grpIdx);
			ps.setString(2,type);
			ps.setString(3,parsedAttr);
			ps.setInt(4,start);
			ps.setInt(5,end);
			ps.setString(6,strand);
			
			ps.addBatch();
			totalLoaded++; cntBatch++;
			if (cntBatch==1000) {
				cntBatch=0;
				ps.executeBatch();
				System.out.print(totalLoaded + " annotations...\r"); // CAS42 1/1/18
			}	
		}
		if (cntBatch> 0) ps.executeBatch();
		ps.close();
		fh.close();
		
		log.msg("   " + totalLoaded + " annotations loaded from " + f.getName());
		if (numParseErrors>0) log.msg("   " + numParseErrors + " parse errors - lines discarded");
		if (numGrpErrors>0) log.msg("   " + numGrpErrors + " group not found - lines discarded");
	}
	
	private void deleteCurrentAnnotations() throws SQLException
	{
		String st = "DELETE FROM pseudo_annot USING pseudo_annot,groups " +
					" WHERE groups.proj_idx='" + projIdx + 
					"' AND pseudo_annot.grp_idx=groups.idx";
		pool.executeUpdate(st);
	}
	public static void main(String[] args) 
	{
		try {
			if (args.length < 1) {
				System.out.println("Usage:  annotation <project>\n");
				System.exit(-1);
			}
			
			FileInputStream propFH = new FileInputStream(Utils.getParamsName());
			mDBProps = new Properties();
			mDBProps.load(propFH);
	
			String dbstr = DatabaseUser.getDatabaseURL(mDBProps.getProperty("db_server"), mDBProps.getProperty("db_name"));
			UpdatePool pool = new UpdatePool(dbstr,
					mDBProps.getProperty("db_adminuser"),
					mDBProps.getProperty("db_adminpasswd"));
	
			AnnotLoadMain annot = new AnnotLoadMain(pool, new Log("symap.log"), null);
			annot.run(args[0]);
			DatabaseUser.shutdown();
		}
		catch (Exception e) {
			DatabaseUser.shutdown();
			ErrorReport.die(e, "Loading annotation files");
		}
	}
}
