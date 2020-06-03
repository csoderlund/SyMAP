package symapQuery;

/**
 * Constants for Query and display
 */

public class Q {
	public static boolean TEST_TRACE=false; // Write to /tmp and other trace output
	
	static final int INC=5000;
	static final String rowCol = "Row";
	static final String blockCol = "Block"; // if used in 'contain', this will also match Block Score
	
	static final String chrCol =	"Chr";
	static final String startCol =	"Start";
	static final String endCol =	"End";
	static final String geneHitCol = "#Gene";
	static final String All_Anno =   "All_Anno";
	static final String empty    = "-";
	
	static final String delim = "\n";	// uses as delimiter between Species::annoKey, and put annoKey on 2nd column line
	static final String delim2 = ":";	// used for other keys
	
	// for SQL tables - see below for tables and fields used
	static final String PA = 	"PA"; 	// pseudo_annot
	static final String PH = 	"PH"; 	// pseudo_hits
	static final String PHA = 	"PHA"; 	// pseudo_annot_hits
	static final String PBH = 	"PBH"; 	// pseudo_block_hits
	static final String B = 		"B"; 	// block
	
	static final int AIDX = 1;				// PA.idx
	static final int AGIDX = 2;				// PA.grp_idx
	static final int ASTART = 3;				// PA.start
	static final int AEND = 4;				// PA.end
	static final int ANAME = 5;				// PA.name (description)
	
	static final int HITIDX =   6;			// PH.idx
	static final int PROJ1IDX =  7;		// PH.proj1_idx  (can get from grpIdx, but this is for sanity checking
	static final int PROJ2IDX =  8;		// PH.proj2_idx
	static final int GRP1IDX =  9;		// PH.grp1_idx
	static final int GRP2IDX =  10;		// PH.grp1_idx
	static final int GRP1START = 11;		// PH.start1
	static final int GRP2START = 12;		// PH.start2
	static final int GRP1END = 13;			// PH.end1
	static final int GRP2END = 14;			// PH.end2
	static final int RSIZE = 15;				// PH.runsize
	
	static final int BNUM = 16;				// B.blocknum
	static final int BSCORE = 17;			// B.blockscore
	static final int ANNOT1IDX = 18;		// PH.annot1_idx
	static final int ANNOT2IDX = 19;		// PH.annot2_idx
	
	static final String ANNO = "ANNO";				// populated by keyword 
	static final String GSIZE = "GSIZE";				// computed PgFsize
	static final String GRP = "GRP";					// computed PgeneF
	static final String PGF = "PGF";					// Compute
	

	/**************************************************************
	 * Relations:
	 * pseudo_annot 
	 * 		"idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +  // annot_idx     
			"grp_idx             INTEGER NOT NULL," +					// xgroups.idx   find species
		 	"type                VARCHAR(20) NOT NULL," +				// 'gene'		check for orphan
			"name                TEXT  NOT NULL," +						// description	orphan and hit
			"start               INTEGER NOT NULL," +					// chr start		orphan		
			"end                 INTEGER NOT NULL," +					// chr end		orphan		
			
	 * pseudo_hits 
	 * 		"idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +  // hit_id			
	 * 		"pair_idx            INTEGER NOT NULL," +					// PH.pair_idx in (list)
	 * 		"proj1_idx           INTEGER NOT NULL," + // proj_props.proj_idx
			"proj2_idx           INTEGER NOT NULL," + // proj_props.proj_idx
			"grp1_idx            INTEGER NOT NULL," + // xgroups.idx
			"grp2_idx            INTEGER NOT NULL," + // xgroups.idx
			"annot1_idx			INTEGER default 0," + // is gene check
			"annot2_idx			INTEGER default 0," +
			"start1              INTEGER NOT NULL," +
			"end1                INTEGER NOT NULL," + 
			"start2              INTEGER NOT NULL," +
			"end2                INTEGER NOT NULL, " +  
			"runsize		INTEGER default 0," +
			
	 * pseudo_block_hits
	 * 		"hit_idx                 INTEGER NOT NULL," + 
			"block_idx               INTEGER NOT NULL," + 
			
	 * blocks
			"idx                     INTEGER AUTO_INCREMENT PRIMARY KEY," + // block_idx
			"blocknum                INTEGER NOT NULL," +	// is block check
			"score					INTEGER default 0," +
				 
	LEFT JOIN pseudo_block_hits 	as PBH 	on PBH.hit_idx=PH.idx
	LEFT JOIN blocks            	as B   	on B.idx=PBH.block_idx
	LEFT JOIN pseudo_hits_annot 	AS PHA 	ON PH.idx = PHA.hit_idx
	LEFT JOIN pseudo_annot 		AS PA 	ON PHA.annot_idx = PA.idx
	 */	
}
