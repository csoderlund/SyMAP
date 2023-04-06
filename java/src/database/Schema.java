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
 */
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
			"loaddate		datetime," +	// db create, CAS520 update synteny 
			"syver			tinytext," +	// CAS520 version of load sequence and/or annotation
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
		    "aligndate				datetime," +
		    "syver					tinytext," +	// CAS520 version of load sequence and/or annotation
			"params					VARCHAR (128)," +	// CAS511
		    "summary				text, " +		// CAS540 (no DB update, will create in SumFrame)
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
		
// Blocks - SyntenyMain will crash if remove/add field; see symmetrizeBlocks
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
		sql = "CREATE TABLE pseudos (" +
		    "grp_idx             INTEGER NOT NULL," +
		    "file                TEXT NOT NULL," +
		    "length              INTEGER NOT NULL," +
		    "PRIMARY KEY (grp_idx)," +
		    "FOREIGN KEY (grp_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
		sql = "CREATE TABLE pseudo_annot (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +  // annot_idx
		    "grp_idx             INTEGER NOT NULL," +	// xgroups.idx
		    "type                VARCHAR(20) NOT NULL," +
		    "name                TEXT  NOT NULL," +
		    "start               INTEGER NOT NULL," +
		    "end                 INTEGER NOT NULL," +
		    "strand              ENUM('+','-') NOT NULL," +
		    "genenum             INTEGER default 0," +  // was being added in SyntenyMain
		    "gene_idx            INTEGER default 0," +  // CAS512 add - gene idx for exon
		    "tag				 VARCHAR(30)," +		// CAS512 add - Gene(#exons) or Exon #N
		    "numhits			 tinyint unsigned default 0," +  // CAS520 show in Query; max 255
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
		
// Sequence hits
	    sql = "CREATE TABLE pseudo_hits (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," + // hit_id
	    	"hitnum				 INTEGER default 0,"+			// CAS520 relative to location along chr
		    "pair_idx            INTEGER NOT NULL," +
		    "proj1_idx           INTEGER NOT NULL," + 			 // proj_props.proj_idx
		    "proj2_idx           INTEGER NOT NULL," + 			 // proj_props.proj_idx
		    "grp1_idx            INTEGER NOT NULL," + 			 // xgroups.idx
		    "grp2_idx            INTEGER NOT NULL," + 			 // xgroups.idx
		    "evalue              DOUBLE NOT NULL," +  			 // No value, though accessed (FPC?)
		    "pctid               TINYINT UNSIGNED NOT NULL," +   // Col6, Avg %ID CAS515 REAL->TINYINT (rounded percent) 
		    "score               INTEGER NOT NULL," +            // Col5, queryLen - not used
		    "strand              TEXT NOT NULL," +
		    "start1              INTEGER NOT NULL," +
		    "end1                INTEGER NOT NULL," + 
		    "start2              INTEGER NOT NULL," +
		    "end2                INTEGER NOT NULL, " +  
		    "query_seq           MEDIUMTEXT  NOT NULL," +      	// start-end of each merged hit
		    "target_seq          MEDIUMTEXT  NOT NULL," +
		    "gene_overlap		TINYINT NOT NULL, " +	       	// number of genes it overlaps
		    "countpct			INTEGER UNSIGNED default 0," + 	// unused 0; CAS515 number of merged, tinyint->integer
		    "cvgpct				TINYINT UNSIGNED NOT NULL," +  	// unused 0; CAS515 Col7 %sim
		    "refidx				INTEGER default 0," +          	// used in self-synteny
			"annot1_idx			INTEGER default 0," +
			"annot2_idx			INTEGER default 0," +
			"runnum				INTEGER default 0," +			// CAS520 number consecutive collinear groups
		    "runsize			INTEGER default 0," +			// size of collinear set
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
		    "olap	            INTEGER NOT NULL," +
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
		    
		 /** CAS506 add DBVer and UPDATE, change other two **/
		 sql = "INSERT INTO props (name,value) VALUES ('VERSION','" +  Globals.VERSION + "'); ";
		 executeUpdate(sql);
		    
		 // referenced in Version
		 sql = "INSERT INTO props (name,value) VALUES ('DBVER','" +  Globals.DBVERSTR + "'); ";
		 executeUpdate(sql);
		 
		 String date = Utilities.getDateOnly();
		 sql = "INSERT INTO props (name,value) VALUES ('INSTALLED', '" + date + "'); ";
		 executeUpdate(sql);
		 
		 sql = "INSERT INTO props (name,value) VALUES ('UPDATE', '" + date + "'); ";
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
