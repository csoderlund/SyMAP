package database;

import java.sql.Connection;
import java.sql.Statement;

import symap.Globals;
import util.ErrorReport;
import util.Utilities;

/********************************************
 * CAS504 moved from scripts/symap.sql to here. Called in DatabaseUser.createDatabase
 * Put it here now so I can add comments and I moved some tables...
 * CAS506 MySQL v8 uses groups as a keyword, so changed ALL groups to xgroups
 * The update schema code was all over the place. So added mysql version and update
 * CAS522 remove FPC
 * 
 */
/***
* CHAR: tinytext 1 byte, text 2 byte, mediumtext 2 byte
* max	256 char			65k char		 16M char
* 		use VARCHAR for fields that need to be searched
* INT	tinyint 1 byte, smallint 2 byte,  mediumint 3 byte, int 4 byte, bigint 8 byte
* max	256               65k                16M				4394M		
* 		float 4 byte,  double 8 byte
*****/

public class Schema {

	public Schema(Connection conn) {
		mConn = conn;
// Projects
		String sql = "CREATE TABLE props (" +
		    "name            VARCHAR(40) NOT NULL," +
		    "value           VARCHAR(255) NOT NULL" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);

		sql = "CREATE TABLE projects (" +
		    "idx            INTEGER NOT NULL AUTO_INCREMENT," + // proj_idx
		    "type           enum('fpc','pseudo')  NOT NULL," +
		    "name           VARCHAR(40) NOT NULL," +
			"hasannot		boolean default 0," +
			"annotdate		datetime," +	// date of load sequence and/or annotation
			"loaddate		datetime," +	// db create, update synteny 
			"syver			tinytext," +	// version of load sequence and/or annotation
		    "PRIMARY KEY (idx)," +
		    "UNIQUE INDEX (name)," +
		    "INDEX (type)" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
		sql = "CREATE TABLE proj_props (" +
		    "proj_idx        INTEGER NOT NULL," +
		    "name            VARCHAR(40) NOT NULL," +
		    "value           VARCHAR(255) NOT NULL," +
		    "UNIQUE INDEX (proj_idx,name)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
	    sql = "CREATE TABLE xgroups (" +
		    "idx                     INTEGER NOT NULL AUTO_INCREMENT," + // grp_idx
		    "proj_idx                INTEGER NOT NULL," +
		    "name                    VARCHAR(40) NOT NULL," +
		    "fullname                VARCHAR(40) NOT NULL," +
		    "sort_order              INTEGER UNSIGNED NOT NULL," +
			"flipped				 BOOLEAN default 0," +
		    "PRIMARY KEY (idx)," +
		    "UNIQUE INDEX (proj_idx,name)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
	    executeUpdate(sql);
	    
		sql = "CREATE TABLE pairs (" +
		    "idx                     INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "proj1_idx               INTEGER NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
			"aligned				BOOLEAN default 0," +
		    "aligndate				datetime," +	// update date;  used by summary (added Mpairs.saveUpdate)
		    "syver					tinytext," +	// update version; version of load sequence and/or annotation
			"params					text," +	// parameters used for MUMmer; CAS568 VARCHAR(128)->text; checked in Mpair
		    "summary				text, " +		// full summary
		    "UNIQUE INDEX (proj1_idx,proj2_idx)," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
		sql = "CREATE TABLE pair_props (" +
		    "pair_idx        INTEGER NOT NULL," +
		    "proj1_idx       INTEGER NOT NULL," +
		    "proj2_idx       INTEGER NOT NULL," +
		    "name            VARCHAR(400) NOT NULL," +
		    "value           VARCHAR(255) NOT NULL," +
		    "UNIQUE INDEX(proj1_idx,proj2_idx,name)," +
		    "INDEX (proj2_idx)," +
		    "FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
// Blocks - Keep fields in order; only add to end and then update  synteny.RWdb.symmetrizeBlocks
		sql = "CREATE TABLE blocks (" +
		    "idx                     INTEGER AUTO_INCREMENT PRIMARY KEY," + // block_idx
		    "pair_idx                INTEGER NOT NULL," +
		    "blocknum                INTEGER NOT NULL," +
		    "proj1_idx               INTEGER NOT NULL," +
		    "grp1_idx                INTEGER NOT NULL," +
		    "start1                  INTEGER NOT NULL," +
		    "end1                    INTEGER NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
		    "grp2_idx                INTEGER NOT NULL," +
		    "start2                  INTEGER NOT NULL," +
		    "end2                    INTEGER NOT NULL," +
		    "comment                 TEXT NOT NULL," +
			"corr					float default 0," +   // <0 is inverted
			"score					INTEGER default 0," +
			"ngene1					integer default 0," +
			"ngene2					integer default 0," +
			"genef1					float default 0," +
			"genef2					float default 0," +
			
			"avgGap1		INTEGER default 0," + // for report; CAS572 add; Version update in synteny.RWdb(); not new DBVER
			"avgGap2		INTEGER default 0," + // in report; calc approx if not exist
			"stdDev1		integer default 0," + // stdDev usually double, but they are so big, only the int part is significant
			"stdDev2		integer default 0," +

		    "INDEX (proj1_idx,grp1_idx)," +
		    "INDEX (proj2_idx,grp2_idx)," +
		    "FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp1_idx) REFERENCES xgroups (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp2_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
			
// Sequence	      
		sql = "CREATE TABLE pseudos (" +			// These are chromosome, scaffolds, etc (from pseudomolecule)
		    "grp_idx             INTEGER NOT NULL," +
		    "file                TEXT NOT NULL," +
		    "length              INTEGER NOT NULL," +
		    "PRIMARY KEY (grp_idx)," +
		    "FOREIGN KEY (grp_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
		sql = "CREATE TABLE pseudo_annot (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +  // annot_idx
		    "grp_idx             INTEGER NOT NULL," +	  // xgroups.idx
		    "type                VARCHAR(20) NOT NULL," + // gene, exon, centromere, gap, pseudo;  
		    "genenum             INTEGER default 0," +      
		    // Gene: genenum.{suffix} (#Exons len); Exon: exon#
		    "tag				 VARCHAR(30)," +		
		    "gene_idx            INTEGER default 0," +  // gene idx for exon
			// numhits for Gene: # of hits across all pairs for Query; Updated in AnchorMain;
		    // numhits for Pseudo: pairIdx for remove Number Pseudo; Updated in AnchorMain; 
		    "numhits			 INTEGER unsigned default 0," +  //used for Pseudo for pairIdx; 
		    "name                TEXT  NOT NULL," +				 // description, list of keyword=value
		    "strand              ENUM('+','-') NOT NULL," +
		    "start               INTEGER NOT NULL," +
		    "end                 INTEGER NOT NULL," +
		    "INDEX (grp_idx,type)," +
		    "FOREIGN KEY (grp_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; "; 
		executeUpdate(sql);
		
		 // keywords for project annotation
		sql = "CREATE Table annot_key ( " +
			"idx 				INTEGER AUTO_INCREMENT PRIMARY KEY," +	 // not reference
			"proj_idx			INTEGER NOT NULL, " +
			"keyname			TEXT, " +
			"count				BIGINT DEFAULT 0, " +
			"FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE " +
			") ENGINE = InnoDB;";
		executeUpdate(sql);
		
		sql = "CREATE TABLE pseudo_seq2 ( " +
		    "grp_idx             INTEGER NOT NULL," +
		    "chunk               INTEGER NOT NULL," +
		    "seq                 LONGTEXT  NOT NULL," +
		    "INDEX (grp_idx)," +
		    "FOREIGN KEY (grp_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ") ENGINE = InnoDB; ";
		executeUpdate(sql);	
		
// Sequence hits; changes here need to be reflected in AnchorsMain.addMirrorHits
// hitnum, pair_idx, proj1_idx, proj2_idx, grp1_idx, grp2_idx, pctid, cvgpct, countpct, score, type, gene_overlap
// annot1_idx, annot2_idx, strand, start1, end1, start2, end2, query_seq, target_seq, runnum, runsize, refidx
	    sql = "CREATE TABLE pseudo_hits (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," + // hit_id
	    	"hitnum				 INTEGER default 0,"+			// relative to location along chr
		    "pair_idx            INTEGER NOT NULL," +
		    "proj1_idx           INTEGER NOT NULL," + 			 // proj_props.proj_idx
		    "proj2_idx           INTEGER NOT NULL," + 			 // proj_props.proj_idx
		    "grp1_idx            INTEGER NOT NULL," + 			 // xgroups.idx
		    "grp2_idx            INTEGER NOT NULL," + 			 // xgroups.idx
		    "pctid               TINYINT UNSIGNED NOT NULL," +   // avg %id Col6     
		    "cvgpct				 TINYINT UNSIGNED NOT NULL," +   // avg %sim  Col7     
		    "countpct			 INTEGER UNSIGNED default 0," +  // number of merged   
		    "score               INTEGER NOT NULL," +            // summed length Col5 - displayed in Query
		    "htype             	 TINYTEXT," +   				 // EE, EI, IE, En, nE, II, In, nI, nn; g2, g1, g0
		    "gene_overlap		 TINYINT NOT NULL, " +	       	 // 0,1,2
		    "annot1_idx			 INTEGER default 0," +			 // can have>0 when pseudo_annot.type='pseudo'
			"annot2_idx			 INTEGER default 0," +
		    "strand              TEXT NOT NULL," +
		    "start1              INTEGER NOT NULL," +
		    "end1                INTEGER NOT NULL," + 
		    "start2              INTEGER NOT NULL," +
		    "end2                INTEGER NOT NULL, " +   
		    "query_seq           MEDIUMTEXT  NOT NULL," +      	// start-end of each merged hit
		    "target_seq          MEDIUMTEXT  NOT NULL," +
			"runnum				INTEGER default 0," +			// number for collinear group
		    "runsize			INTEGER default 0," +			// size of collinear set
		    "refidx				INTEGER default 0," +          	// used in self-synteny
		    "INDEX (proj1_idx,proj2_idx,grp1_idx,grp2_idx)," +
		    "FOREIGN KEY (pair_idx)  REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp1_idx)  REFERENCES xgroups (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp2_idx)  REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
	    executeUpdate(sql);
	    
		sql = "CREATE TABLE pseudo_hits_annot (" +
		    "hit_idx             INTEGER NOT NULL," +	// pseudo_hits.idx
		    "annot_idx           INTEGER NOT NULL," +	// pseudo_annot.idx
		    "olap	             INTEGER default 0," +   // gene overlap; algo1 basepairs; algo2 percent
		    "exlap	             tinyint default 0," +   // exon percentoverlap
		    "annot2_idx			 INTEGER default 0," +   // not present for type pseudo; 
		    "UNIQUE (hit_idx, annot_idx)," +
		    "INDEX (annot_idx)," +
		    "FOREIGN KEY (hit_idx)  REFERENCES pseudo_hits (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (annot_idx) REFERENCES pseudo_annot (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		 
		 sql = "CREATE TABLE pseudo_block_hits (" +
		    "hit_idx                 INTEGER NOT NULL," + 
		    "block_idx               INTEGER NOT NULL," + 
		    "INDEX (hit_idx)," +
		    "INDEX (block_idx)," +
		    "FOREIGN KEY (hit_idx)   REFERENCES pseudo_hits (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (block_idx) REFERENCES blocks (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		 executeUpdate(sql);					

		 sql = "SET FOREIGN_KEY_CHECKS = 1;";
		 executeUpdate(sql);
		    
		 String date = Utilities.getDateOnly();
		 
		// never change
		 sql = "INSERT INTO props (name,value) VALUES ('INIT', '" + date + "'); ";
		 executeUpdate(sql);
		
		 sql = "INSERT INTO props (name,value) VALUES ('INITV','" +  Globals.VERSION + "'); ";
		 executeUpdate(sql);
		 
		 sql = "INSERT INTO props (name,value) VALUES ('INITDB','" +  Globals.DBVERSTR + "'); ";
		 executeUpdate(sql);
				 
		// updated in Version 
		 sql = "INSERT INTO props (name,value) VALUES ('UPDATE', '" + date + "'); ";
		 executeUpdate(sql);
		 
		 sql = "INSERT INTO props (name,value) VALUES ('VERSION','" +  Globals.VERSION + "'); ";
		 executeUpdate(sql);
		 
		 sql = "INSERT INTO props (name,value) VALUES ('DBVER','" +  Globals.DBVERSTR + "'); ";
		 executeUpdate(sql);
	}
	private int executeUpdate(String sql) {
		int ret=0;
		try{
			Statement stmt = mConn.createStatement();
			ret = stmt.executeUpdate(sql);
			stmt.close();
		}
		catch (Exception e){ErrorReport.die(e, "Query failed:" + sql);}
		
		return ret;
	}
	private Connection mConn;
}
