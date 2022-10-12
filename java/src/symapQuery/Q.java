package symapQuery;

/**
 * Constants for Query and display
 */

public class Q {
	public static boolean TEST_TRACE=false; // Write to /tmp and other trace output
	
	static final int INC=5000;
	static final String rowCol   = "Row";
	static final String blockCol = "Block"; // equals TableData.ColumnComparator
	static final String runCol =   "Collinear";   // startsWith TableData.ColumnComparator
	
	static final String chrCol =	"Chr";
	static final String startCol =	"Start";
	static final String endCol =	"End";
	static final String lenCol = 	"Len";
	static final String geneNCol =  "Gene#";
	static final String All_Anno =  "All_Anno";
	static final String empty    = 	"-";
	
	static final String delim = "\n";	// uses as delimiter between Species::annoKey, and put annoKey on 2nd column line
	static final String delim2 = ":";	// used for other keys
	
	// for SQL tables - see below for tables and fields used
	static final String PA = 	"PA"; 	// pseudo_annot
	static final String PH = 	"PH"; 	// pseudo_hits
	static final String PHA = 	"PHA"; 	// pseudo_annot_hits
	static final String PBH = 	"PBH"; 	// pseudo_block_hits
	static final String B = 	"B"; 	// block
	
	static final int AIDX = 1;			// PA.idx
	static final int AGIDX = 2;			// PA.grp_idx
	static final int ASTART = 3;		// PA.start
	static final int AEND = 4;			// PA.end
	static final int ANAME = 5;			// PA.name (description)
	static final int AGENE = 6;			// PA.genenum CAS514 added; was computed in DBdata
	
	static final int HITIDX =   7;		// PH.idx
	static final int PROJ1IDX =  8;		// PH.proj1_idx  (can get from grpIdx, but this is for sanity checking
	static final int PROJ2IDX =  9;		// PH.proj2_idx
	static final int GRP1IDX =  10;		// PH.grp1_idx
	static final int GRP2IDX =  11;		// PH.grp1_idx
	static final int GRP1START = 12;	// PH.start1
	static final int GRP2START = 13;	// PH.start2
	static final int GRP1END = 14;		// PH.end1
	static final int GRP2END = 15;		// PH.end2
	static final int RSIZE = 16;		// PH.runsize
	static final int PID = 17;			// PH.pctid
	static final int PSIM = 18;			// PH.cvgpct
	static final int HCNT = 19;			// PH.countpct
	
	static final int BNUM = 20;			// B.blocknum
	static final int BSCORE = 21;		// B.blockscore
	static final int ANNOT1IDX = 22;	// PH.annot1_idx
	static final int ANNOT2IDX = 23;	// PH.annot2_idx
	
	static final String ANNO = "ANNO";	// populated by keyword 
	static final String GSIZE = "GSIZE";// computed PgFsize
	static final String GRP = "GRP";	// computed PgeneF
	static final String PGF = "PGF";	// Compute
	
	/**************************************************************
	 * Relations:
	 * pseudo_annot 
	 * 		"idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +  // annot_idx     
			"grp_idx             INTEGER NOT NULL," +					// xgroups.idx  find species
		 	"type                VARCHAR(20) NOT NULL," +				// 'gene'		check for orphan
			"name                TEXT  NOT NULL," +						// description	orphan and hit
			"start               INTEGER NOT NULL," +					// chr start	orphan		
			"end                 INTEGER NOT NULL," +					// chr end		orphan		
			
	 * pseudo_hits 
	 * 		"idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +  // hit_id			
	 * 		"pair_idx            INTEGER NOT NULL," +					// PH.pair_idx in (list)
	 * 		"proj1_idx           INTEGER NOT NULL," + // proj_props.proj_idx
			"proj2_idx           INTEGER NOT NULL," + // proj_props.proj_idx
			"grp1_idx            INTEGER NOT NULL," + // xgroups.idx
			"grp2_idx            INTEGER NOT NULL," + // xgroups.idx
			"annot1_idx			 INTEGER default 0," + // is gene check
			"annot2_idx			 INTEGER default 0," +
			"start1              INTEGER NOT NULL," +
			"end1                INTEGER NOT NULL," + 
			"start2              INTEGER NOT NULL," +
			"end2                INTEGER NOT NULL, " +  
			"runsize			 INTEGER default 0," +
			"pctid               TINYINT UNSIGNED NOT NULL," +    // Col6, Avg %ID CAS515 
			"countpct			INTEGER UNSIGNED default 0," +  // unused 0; CAS515 number of merged, tinyint->integer
		    "cvgpct				TINYINT UNSIGNED NOT NULL," +  //was unused 0 -> CAS515 Col7 %sim
			
			
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
	LEFT JOIN pseudo_annot 		    AS PA 	ON PHA.annot_idx = PA.idx
	 */	
}
