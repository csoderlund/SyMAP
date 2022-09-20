package backend;

/*******************************************
 * Params parameters for both project and synteny
 */
import java.util.Enumeration;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;

import java.sql.SQLException;
import java.util.Properties;
import java.util.HashMap;

import util.ErrorReport;
import util.Logger;
import util.PropertiesReader;
import util.Utilities;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyProps extends PropertiesReader
{	
	// copied from PairPropertyFrame; only want to print/save what can be changed in this interface
	private final String [] SYMBOLS = { 
		"mindots", "topn", "merge_blocks", 
		"do_synteny", "do_clustering", "no_overlapping_blocks", 
		"blat_args", "nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only" };
			
	public SyProps() {
		setDefaults();
	}
	// For Loads: Loads the data/fpc/<p>/params file and data/seq/<p>/params
	//    params are written from the Project Parameter window.
	//    Called from FPCLoadMain and SeqLoadMain
	public SyProps(Logger log, File file)
	{
		System.out.println("Read " + file);
		setDefaults();
		try {
			load(new FileInputStream(file));
			fixProps();
		}
		catch (java.io.FileNotFoundException e) {
			if (log != null)
				log.msg(file.getAbsolutePath() + " not found");
		}
		catch (java.io.IOException e2) {
			if (log != null)
				log.msg(file.getAbsolutePath() + " read error");
		}
	}
	// Load: added to allow properties from main params file as well
	// called from AnnotLoadMain
	public SyProps(Logger log, File file, Properties defprops)
	{
		System.out.println("Read " + file);
		setDefaults();
		
		Enumeration <?> keys = defprops.propertyNames(); // CAS512 add <?>
		while (keys.hasMoreElements())
		{
			String key = keys.nextElement().toString();
			String val = defprops.getProperty(key);
			if (containsKey(key)) 
				setProperty(key, val.trim());				
		}
		try {
			load(new FileInputStream(file));
			fixProps();
		}
		catch (java.io.FileNotFoundException e) {
			if (log != null)
				log.msg(file.getAbsolutePath() + " not found");
		}
		catch (java.io.IOException e2) {
			if (log != null)
				log.msg(file.getAbsolutePath() + " read error");
		}
	}
	// The only properties that appear changable are those in the 
	// Parameter interfaces. Lots of DEAD stuff here.
	private void setDefaults()
	{
		// *********NOTE*********** 
		// if properties are added, they also have to be added to 
		// upload lists (look for uploadPairProps,uploadProjProps)
		// PairPropertiesFrame
		//
		/**********************************************************
		 * Alignment parameters
		 **********************************************************/
		setProperty("blat_args",			"-minScore=30 -minIdentity=70 -tileSize=10 -qMask=lower -maxIntron=10000"); 		
		setProperty("nucmer_args",			""); 		
		setProperty("promer_args",			""); 
		setProperty("self_args",             ""); // CAS501 removed --maxmatch in AlignMain
		setProperty("nucmer_only",          "0");	
		setProperty("promer_only",          "0");	

		/**********************************************************
		 * Anchor loading parameters
		 **********************************************************/
		setProperty("topn_and", 			"1");
		setProperty("topn", 				"2"); 		
		setProperty("topn_maxwindow", 	"100000");
		setProperty("annot_in1",			""); 		// obsolete annotation types to include for proj 1
		setProperty("annot_in2",			""); 		// obsolete annotation types to include for proj 2
		setProperty("annot_out1",		""); 		// obsolete annotation types to exclude for proj 1
		setProperty("annot_out2",		""); 		// obsolete annotation types to exclude for proj 2
		setProperty("annot_mark1",		"gene"); 	// obsolete gene is assumed for everything
		setProperty("annot_mark2",		"gene"); 	// obsolete 
		setProperty("annot_bin",			"30000"); 	// bp size of annotation bin
		setProperty("hit_bin",			"10000"); 	// # hits per hit bin
		setProperty("use_genemask",	"0"); 		// use to enable gene-to-gene alignment, must reload project after setting
		setProperty("gene_pad",			"1000");	// bp pad before start and after end for gene extraction
		setProperty("do_clustering",     "1");		
		setProperty("max_cluster_gap",   "1000"); 	
		setProperty("max_cluster_size",  "50000");	
		setProperty("print_stats",       "0");	
		setProperty("keep_gene_gene",    "0");	
		setProperty("keep_gene",         "0");	
		setProperty("only_gene_gene",    "0");	
		setProperty("only_gene",         "0");	
		
		/**********************************************************
		 * Synteny algorithm parameters
		 **********************************************************/
		// All distances in basepairs, converted for FPC using cbsize in FPC file
		final int mindots = 7; // master block score requirement
		setProperty("joinfact", 		".25");	// controls block-merge phase
		setProperty("merge_blocks", 	"0");	// perform block merge
		setProperty("no_overlapping_blocks", 	"0");	// prune the duplicated blocks
		setProperty("mindots", 			Integer.toString(mindots));
		setProperty("keep_best", 		"0");	// keep the best alignment for each ctg even if below thresh
		setProperty("mindots_keepbest", "3");	// minimal score for keep_best
		
		setProperty("mingap1", 		"1000");	// minimum gap param considered in binary search
		setProperty("mingap1_cb",	"10");		// minimum gap param considered in binary search
		setProperty("mingap2", 		"1000");	//      "		"			"
		setProperty("search_factor",".5");		// binary search factor
	
		setProperty("corr1_A", ".8");	
		setProperty("corr1_B", ".98");	
		setProperty("corr1_C", ".96");	
		setProperty("corr1_D", ".94");	
	
		setProperty("corr2_A", ".7");	
		setProperty("corr2_B", ".9");	
		setProperty("corr2_C", ".8");	
		setProperty("corr2_D", ".7");	
	
		// these are set later based on density of hits, if not set here
		setProperty("avg1_A",  "0");	
		setProperty("avg2_A",  "0");
		setProperty("maxgap1", "0");		
		setProperty("maxgap2", "0");
		
		// Synteny - FPC only
		setProperty("do_unanchored", 		"1");
		setProperty("unanch_join_dist_bp", 	"300000");
		setProperty("subblock_multiple", 	"3"); // for bes fixing
		setProperty("do_bes_fixing", 		"1");
		
		// Testing
		setProperty("checkctg1", "0");	
		setProperty("checkgrp1", "0");	
		setProperty("checkgrp2", "0");	
		
		setProperty("maxjoin_cb", 	"1000000");
		setProperty("maxjoin_bp", 	"1000000000");
		
		setProperty("do_synteny", "1"); 
	
		/**********************************************************
		 * FPC project parameters
		 **********************************************************/
		setProperty("max_mrk_ctgs_hit", 	"10");
		setProperty("min_mrk_clones_hit", 	"1");
		setProperty("min_ctg_size", 		"0");
		setProperty("cbsize", 		"1200"); // typical for hicf
	
		/**********************************************************
		 * Pseudo project parameters
		 **********************************************************/
		setProperty("grp_type", 	"Chromosome");
		setProperty("grp_sort", 	"alpha");
		setProperty("grp_prefix", 	"Chr");
		setProperty("grp_order", 	"");
		setProperty("order_against", 	"");
		setProperty("replace_bad_char", 	"0");
		setProperty("min_size", 	"100000");
		setProperty("min_display_size_bp", 	"0");
		setProperty("annot_id_field","ID");
		setProperty("annot_types", 	"");
		setProperty("annot_files", 	"");
		setProperty("sequence_files", 	"");
		setProperty("annot_kw_mincount","50"); // CAS512 change default from 0
		setProperty("annot_keywords","");
		setProperty("all_annot",    "0"); 
		setProperty("mask_all_but_genes", "0"); 
	}
	
	private void fixProps()
	{
		Enumeration <?> keys = propertyNames();
		while (keys.hasMoreElements())
		{
			String key = keys.nextElement().toString();
			String val = getProperty(key);

			// Remove comments and leading/trailing whitespace
			val = val.replaceAll("#.*", "");
			val = val.trim();
			setProperty(key, val);
		}
	}
	
	// FPCLoadMain and SeqLoadMain
	public void uploadProjProps(UpdatePool pool, int pidx, String[] keys) throws SQLException
	{
		for (String key : keys) {
			String val = getProperty(key);
			String st = "INSERT INTO proj_props VALUES('" + pidx + "','" + key + "','" + val + "')";
			pool.executeUpdate(st);
		}
	}
	
	/*****************************************************
	 * Pair property methods (CAS501 added for 5.0.1)
	 */
	public void copyProps(SyProps p) {
		Enumeration <?> em = p.keys();
		while (em.hasMoreElements()) {
			String key = (String)em.nextElement();
			for (String x : SYMBOLS) {
				if (x.equals(key)) {
					String def = p.getProperty(key);
					setProperty(key, def);
					break;
				}
			}
		}
	}
	
	public void printNonDefault(Logger log, String title) {
		HashMap <String, String> chgVal = getNonDef();
		
		if (chgVal.size()==0) {
			if (log!=null) log.msg(title + ": use default parameters");
			else System.out.println(title + ": use default parameters");	
		}
		else {
			if (log==null) {
				System.out.println(title + " parameters: ");
				for (String key : chgVal.keySet()) 
					System.out.format("   %s=%s\n", key, chgVal.get(key));
			}
			else {
				log.msg(title + " parameters: ");
				for (String key : chgVal.keySet()) 
					log.msg(String.format("  %s=%s", key, chgVal.get(key)));
			}	
		}
	}
	// For anything different from defaults, print to terminal, print to file, save to db
	public void saveNonDefaulted(Logger log, String dir, int idx1, int idx2, int pairIdx, String n1, String n2, UpdatePool pool) {
		HashMap <String, String> chgVal = getNonDef();
		
		String title = "Pair " + n1 + "," + n2;
		if (chgVal.size()==0) {
			if (log!=null) log.msg(title + ": use default parameters");
			else System.out.println(title + ": use default parameters");	
		}
		else {
			if (log==null) {
				System.out.println(title + " parameters: ");
				for (String key : chgVal.keySet()) 
					System.out.format("   %s=%s\n", key, chgVal.get(key));
			}
			else {
				log.msg(title + " parameters: ");
				for (String key : chgVal.keySet()) 
					log.msg(String.format("  %s=%s", key, chgVal.get(key)));
			}	
		}
		writeFile(dir, chgVal); // write empty file if chgVal.size()==0
		uploadPairProps(pool, idx1, idx2, pairIdx, chgVal); // Delete existing if chgVal.size()==0
	}
	private HashMap <String, String> getNonDef() {
		HashMap <String, String> chgVal = new HashMap <String, String> ();
		SyProps defProps = new SyProps();
		Enumeration <?> em = defProps.keys();
		while (em.hasMoreElements()) {
			String key = (String)em.nextElement();
			for (String x : SYMBOLS) {
				if (x.equals(key)) {
					String def = defProps.getProperty(key);
					String cur = getProperty(key);
					if (!cur.equals(def)) {
						chgVal.put(key, cur);
					}
					break;
				}
			}
		}
		return chgVal;
	}
	private void uploadPairProps(UpdatePool pool, int pidx1, int pidx2, int pairIdx, 
			HashMap <String, String> chgVal)
	{
		try {
			String st = "DELETE FROM pair_props WHERE pair_idx=" + pairIdx;
			pool.executeUpdate(st);
			
			for (String key : chgVal.keySet()) {
				String val = chgVal.get(key);
				st = "INSERT INTO pair_props VALUES ('" + 
						pairIdx + "','" + pidx1 + "','" + pidx2 + "','" + key + "','" + val + "')";
				pool.executeUpdate(st);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot save pair parameters");}
	}
	/**************************************************
	 * Writes the param file, though this is never read. Parameters are stored in database.
	 */
	private void writeFile(String dir, HashMap <String, String> chgVal) {
		Utilities.checkCreateDir(Constants.seqRunDir, true); // CAS516 if change params before 1st align
		Utilities.checkCreateDir(dir, true); 
		
		File pfile = new File(dir,Constants.paramsFile);
		//if (!pfile.exists()) CAS511 don't need to output this
		//	System.out.println("Create parameter file " + dir + Constants.paramsFile);
		try {
			PrintWriter out = new PrintWriter(pfile);
			out.println("#");
			out.println("# Pairs parameter file ");
			out.println("# Created " + ErrorReport.getDate());
			out.println("# Note: changes MUST be made in SyMAP parameter window");
			
			if (chgVal.size()==0) {
				out.println("# No parameters set - use defaults");
			}
			else {
				for (String key : chgVal.keySet()) 
					out.format("%s=%s\n", key, chgVal.get(key));
			}
			out.close();
		} catch(Exception e) {ErrorReport.print(e, "Creating params file");}
	}
}
