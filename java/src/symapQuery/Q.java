package symapQuery;

/**
 * Constants for Query and display
 */

public class Q {
	static final int INC=5000;
	static final String GROUP = "-", COSET = "."; // CAS563 can't change COSET easily
	static final String DOT = ".",    // dot used between chr.chr.block and chr.chr.CoSz and Chr.gene#; hard-coded everywhere
			            SDOT = "\\."; // this is for split
	
	static final String rowCol   = "Row";
	static final String blockCol = "Block"; 	// loadRow; equals TableData.ColumnComparator (for sorting)
	static final String cosetCol = "Collinear"; // loadRow; CAS517 equals TableData.ColumnComparator
	static final String hitCol =   "Hit#";		// loadRow; CAS521 used for Align text
	static final String grpCol =   "Group";		// loadRow; CAS555 show synteny; CAS563 now GrpSize.Grp#.
	static final String hitPrefix = "Hit";		// Required prefix
	static final String hitSt	  = "Hit\nSt";  // Leave \n so can search on full column in TableData; CAS563 add for Align
	
	// CAS519 was Gene for Orphan and Hit for Pairs; now it always shows the genes; Chr is always same for hit/gene
	static final String chrCol =	 "Chr";
	static final String gStartCol =	 "Gstart";
	static final String gEndCol =	 "Gend";
	static final String gLenCol = 	 "Glen";
	static final String gStrandCol = "Gst";		// endsWith TableData.ColumnComparator; CAS518
	static final String gNCol =  	 "Gene#";	// endsWith TableData.ColumnComparator; expects chr#.genenum.[suffix]
	static final String hStartCol =	 "Hstart";
	static final String hEndCol =	 "Hend";
	static final String hLenCol = 	 "Hlen";
	static final String gHitNumCol = "NumHits"; // CAS541 
	static final String gOlap = 	 "%Olap";   // CAS548
	
	static final String All_Anno =  "All_Anno";
	static final String empty    = 	".";		// Changed from "-" because was confused with negative strand; CAS563
	static final String dash	 =  "-"; 		// this used for geneTag if empty
	static final int    iNoVal   = -1;          // gene coord defaults
	
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
	static final int ASTRAND = 5;		// PA.strand
	static final int ANAME = 6;			// PA.name (description)
	static final int AGENE = 7;			// PA.genenum
	static final int ANUMHITS = 8;		// PA.numhits - For singles
	
	static final int HITIDX =   9;		// PH.idx
	static final int HITNUM =   10;		// PH.hitnum
	static final int PROJ1IDX =  11;	// PH.proj1_idx  (can get from grpIdx, but this is for sanity checking
	static final int PROJ2IDX =  12;	// PH.proj2_idx
	static final int GRP1IDX =  13;		// PH.grp1_idx
	static final int GRP2IDX =  14;		// PH.grp1_idx
	static final int HIT1START = 15;	// PH.start1
	static final int HIT2START = 16;	// PH.start2
	static final int HIT1END = 17;		// PH.end1
	static final int HIT2END = 18;		// PH.end2
	static final int PID = 19;			// PH.pctid
	static final int PSIM = 20;			// PH.cvgpct
	static final int HCNT = 21;			// PH.countpct
	static final int HST = 22;			// PH.strand
	static final int HSCORE = 23;		// PH.score, Hit Cov
	static final int HTYPE = 24;		// PH.htype, EE, EI, etc
	static final int COSIZE = 25;		// PH.runsize
	static final int CONUM = 26;		// PH.runnum, 
	
	static final int BNUM = 27;			// B.blocknum
	static final int BSCORE = 28;		// B.blockscore
	
	static final int AOLAP = 29;		// PHA.olap or exlap CAS548 add
	
	static final int ANNOT1IDX = 30;	// PH.annot1_idx
	static final int ANNOT2IDX = 31;	// PH.annot2_idx
	
	static final int PHAANNOT1IDX = 32;	// PHA.annot_idx	CAS547 for AllGenes
	static final int PHAANNOT2IDX = 33;	// PHA.annot2_idx
	
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
		    "score				 INTEGER default 0,"
			
			
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
