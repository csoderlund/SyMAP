package symapQuery;

// Constants for Query
public class Q {

	static final String PA = 	"PA"; 	// pseudo_annot
	static final String PH = 	"PH"; 	// pseudo_hits
	static final String PHA = 	"PHA"; 	// pseudo_annot_hits
	static final String PBH = 	"PBH"; 	// pseudo_block_hits
	static final String B = 		"B"; 	// block
	static final String P1 = 	"P1";	// proj_props AS P1 ON P1.proj_idx = PH.proj1_idx 
	static final String P2 = 	"P2";	// proj_props AS P2 ON P2.proj_idx = PH.proj2_idx
	static final String G1 = 	"G1";	// groups as G1 ON G1.idx = PH.grp1_idx  
	static final String G2 = 	"G2";	// groups as G2 ON G2.idx = PH.grp2_idx  
	static final String G  = 	"G";		// groups as G  ON G.idx =  PA.grp_idx 
	
	static final String SPECNAME1 = "SPECNAME1"; 	// P1.value 
	static final String SPECNAME2 = "SPECNAME2"; 	// P2.value
	
	static final String GRPNAME1 = "GRPNAME1";		// G1.name
	static final String GRPNAME2 = "GRPNAME2";		// G2.name
	static final String GRPIDX1  = "GRPIDX1";		// G1.idx   
	static final String GRPIDX2 =  "GRPIDX2";		// G2.idx
	static final String PROJIDX =  "PROJIDX";			// G1.proj_idx
	
	static final String PROJ1IDX = "PROJ1IDX";		// PH.proj1_idx
	static final String PROJ2IDX = "PROJ2IDX";		// PH.proj2_idx
	static final String HITIDX =   "HITIDX";			// PH.idx
	static final String GRP1IDX =  "GRP1IDX";		// PH.grp1_idx
	static final String GRP2IDX =  "GRP2IDX";		// PH.grp1_idx
	static final String GRP1START = "GRP1START";		// PH.start1
	static final String GRP2START = "GRP2START";		// PH.start2
	static final String GRP1END = "GRP1END";			// PH.end1
	static final String GRP2END = "GRP2END";			// PH.end2
	static final String RSIZE = "RSIZE";				// PH.runsize
	static final String PGF = "PGF";					// PH.runsize + n
	
	static final String AGIDX = "AGIDX";				// PA.grp_idx
	static final String AIDX = "AIDX";				// PA.idx
	static final String ATYPE = "ATYPE";				// PA.type
	static final String ASTART = "ASTART";			// PA.start
	static final String AEND = "AEND";				// PA.end
	static final String ANAME = "ANAME";				// PA.name (description)
	
	static final String BNUM = "BNUM";				// B.blocknum
	static final String BSCORE = "BSCORE";			// B.blockscore
	
	static final String isAnnot = "isAnnot";			// set to 0 or 1 in where statement
	static final String ANNO = "ANNO";				// populated by keyword 
	static final String GSIZE = "GSIZE";				// computed PgFsize
	static final String GRP = "GRP";					// computed PgeneF
	
	static final String All_Anno =   "All_Anno";
	static final String annotation = "Annotation";	// hiddent fieldname for PA.name
	static final String pgenef =     "PgeneF";
	static final String blocknum =   "BlockNum";
	static final String nRGN		=	"#RGN";
	static final String RGNn		=	"RGN#";
}
