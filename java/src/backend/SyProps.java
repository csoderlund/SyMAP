package backend;

import java.util.Enumeration;

import java.io.File;
import java.io.FileInputStream;

import java.sql.SQLException;
import java.util.Properties;
import util.Logger;
import util.PropertiesReader;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyProps extends PropertiesReader
{
	//Added MW - used to get defaults only
	public SyProps() {
		setDefaults();
	}
	
	public SyProps(Logger log, File file)
	{
		setDefaults();
		try {
			load(new FileInputStream(file));
			fixProps();
			//printProps(log);
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
	// WMN added to allow properties from main params file as well
	public SyProps(Logger log, File file, Properties defprops)
	{
		setDefaults();
		Enumeration keys = defprops.propertyNames();
		while (keys.hasMoreElements())
		{
			String key = keys.nextElement().toString();
			String val = defprops.getProperty(key);
			if (containsKey(key))
			{
				setProperty(key, val.trim());				
			}
		}
		try {
			load(new FileInputStream(file));
			fixProps();
			//printProps(log);
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
	private void setDefaults()
	{
		//
		// *********NOTE*********** 
		// if properties are added, they also have to be added to 
		// upload lists (look for uploadPairProps,uploadProjProps)
		//
		
		/**********************************************************
		 * Alignment parameters
		 **********************************************************/
		setProperty("blat_args",			"-minScore=30 -minIdentity=70 -tileSize=10 -qMask=lower -maxIntron=10000"); 		
		setProperty("nucmer_args",			""); 		
		setProperty("promer_args",			""); 		
		setProperty("nucmer_only",          "0");	
		setProperty("promer_only",          "0");	

		/**********************************************************
		 * Anchor loading parameters
		 **********************************************************/
		setProperty("topn_and", 			"1");
		setProperty("topn", 				"2"); 		
		setProperty("topn_maxwindow", 		"100000");
		setProperty("annot_in1",			""); 		// annotation types to include for proj 1
		setProperty("annot_in2",			""); 		// annotation types to include for proj 2
		setProperty("annot_out1",			""); 		// annotation types to exclude for proj 1
		setProperty("annot_out2",			""); 		// annotation types to exclude for proj 2
		setProperty("annot_mark1",			"gene"); 	// mdb added 7/29/09 #167 - annotation types to mark for proj 1
		setProperty("annot_mark2",			"gene"); 	// mdb added 7/29/09 #167 - annotation types to mark for proj 2
		setProperty("annot_bin",			"30000"); 	// bp size of annotation bin
		setProperty("hit_bin",				"10000"); 	// # hits per hit bin
//		setProperty("genes_only",			"0"); 		// mdb removed 5/13/09
		setProperty("use_genemask",	"0"); 		// use to enable gene-to-gene alignment, must reload project after setting
		setProperty("gene_pad",				"1000");	// bp pad before start and after end for gene extraction
		setProperty("do_clustering",        "1");		// mdb added 7/28/09 #167
		setProperty("max_cluster_gap",          "1000"); 	// mdb added 8/11/09 #167
		setProperty("max_cluster_size",          "50000");	// mdb added 8/11/09 #167
		setProperty("print_stats",          "0");	
		setProperty("keep_gene_gene",          "0");	
		setProperty("keep_gene",          "0");	
		setProperty("only_gene_gene",          "0");	
		setProperty("only_gene",          "0");	
		
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
	
		// min dots, in-block correlations, and full-rectangle correlations
		// for the 4 successive block tests
		
// mdb removed 9/4/09 - hardcoding these params in SyntenyMain, they weren't getting recalculated when mindots changed
//		int mindotsA = mindots;
//		int mindotsB = mindots;
//		int mindotsC = 3*mindots/2;
//		int mindotsD = 2*mindots;
//		setProperty("mindots_A", Integer.toString(mindotsA));	
//		setProperty("mindots_B", Integer.toString(mindotsB));	
//		setProperty("mindots_C", Integer.toString(mindotsC));	
//		setProperty("mindots_D", Integer.toString(mindotsD));	
	
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
		
		setProperty("do_synteny", "1"); // mdb added 5/12/09
	
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
		setProperty("annot_kw_mincount","0");
		setProperty("annot_keywords","");
		setProperty("all_annot",    "0"); 
		setProperty("mask_all_but_genes", "0"); 
	}
	
	private void fixProps()
	{
		Enumeration keys = propertyNames();
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
	
	public void printProps(Logger log) 
	{
		Enumeration keys = propertyNames();
		log.msg("Parameters:");
		while (keys.hasMoreElements())
		{
			String key = keys.nextElement().toString();
			String value = getProperty(key);
			if (value != null && value.length() > 0)
				log.msg("   " + key + ":" + value);
		}
		log.msg("");
	}
	
	public void uploadProjProps(UpdatePool pool, int pidx, String[] keys) throws SQLException
	{
		for (String key : keys)
			uploadProjProps(pool, pidx, key);
	}
	
	public void uploadProjProps(UpdatePool pool, int pidx, String key) throws SQLException
	{
		String val = getProperty(key);
		String st = "INSERT INTO proj_props VALUES('" + pidx + "','" + key + "','" + val + "')";
		pool.executeUpdate(st);
	}
	
	public void uploadPairProps(UpdatePool pool, int pidx1, int pidx2, int pair_idx, String[] keys) throws SQLException
	{
		for (String key : keys)
			uploadPairProps(pool, pidx1, pidx2, pair_idx, key);
	}
	
	public void uploadPairProps(UpdatePool pool, int pidx1, int pidx2, int pair_idx, String key) throws SQLException
	{
		String val = getProperty(key);
		String st = "INSERT INTO pair_props VALUES ('" + pair_idx + "','" + pidx1 + "','" + pidx2 + "','" + key + "','" + val + "')";
		pool.executeUpdate(st);
	}
	public void printNonDefaulted(Logger log)
	{
		SyProps defProps = new SyProps();
		Enumeration em = defProps.keys();
		while (em.hasMoreElements())
		{
			String key = (String)em.nextElement();
			String def = defProps.getProperty(key);
			String cur = getProperty(key);
			if (!cur.equals(def))
			{
				log.msg("Parameter:" + key + "=" + cur);
			}
		}		
	}
}
