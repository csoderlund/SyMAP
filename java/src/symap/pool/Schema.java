package symap.pool;

import java.sql.Connection;
import java.sql.Statement;

import symap.SyMAP;
import util.ErrorReport;
import util.Utilities;

public class Schema {

	public Schema(Connection conn) {
		/********************************************
		 * CAS506 MySQL v8 uses groups as a keyword, so changed ALL groups to xgroups
		 * The update schema code was all over the place. So added mysql version and update
		 */
		/*********************************************
		 * CAS504 moved from scripts/symap.sql to here. Called in DatabaseUser.createDatabase
		 * Put it here now so I can add comments and I moved some tables...
		 */
		/*******************************************************
		 Don't add fields to the middle of any of these tables because there are places in the code 
		 where values are inserted with order assumed!!!!!!!  bad!
		 Also if you add fields to the end of these tables you probably
		 should change the code that populates the tables. ick!
		*****************************************************/
		mConn = conn;
// Projects
		String sql = "CREATE TABLE props (" +
		    "name            VARCHAR(40) NOT NULL," +
		    "value           VARCHAR(255) NOT NULL" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);

		sql = "CREATE TABLE projects (" +
		    "idx             INTEGER NOT NULL AUTO_INCREMENT," + // proj_idx
		    "type            enum('fpc','pseudo')  NOT NULL," +
		    "name            VARCHAR(40) NOT NULL," +
			"loaddate		datetime NOT NULL," +
			"hasannot		boolean default 0," +
			"annotdate		datetime," +
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
			"flipped					 BOOLEAN default 0," +
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
			"params					VARCHAR (128)," +	// CAS511
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
		
		 /** blank table CAS514 not used
		sql = "CREATE TABLE projgrp (" +
		    "idx             INTEGER NOT NULL AUTO_INCREMENT," +
		    "name            VARCHAR(40) NOT NULL," +
		    "PRIMARY KEY (idx)," +
		    "UNIQUE INDEX (name)" +
		    ")  ENGINE = InnoDB;";
		executeUpdate(sql);
		
		sql = "CREATE TABLE projgrp_memb (" +
		    "proj_grp_idx        INTEGER NOT NULL," +
		   	"proj_idx			INTEGER NOT NULL," +
		    "INDEX (proj_grp_idx)," +
		    "INDEX (proj_idx)," +
		    "FOREIGN KEY (proj_grp_idx) REFERENCES projgrp (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    "    )  ENGINE = InnoDB;";
		executeUpdate(sql);
		**/
// Blocks
		sql = "CREATE TABLE blocks (" +
		    "idx                     INTEGER AUTO_INCREMENT PRIMARY KEY," + // block_idx
		    "pair_idx                INTEGER NOT NULL," +
		    "blocknum                INTEGER NOT NULL," +
		    "proj1_idx               INTEGER NOT NULL," +
		    "grp1_idx                INTEGER NOT NULL," +
		    "start1                  INTEGER NOT NULL," +
		    "end1                    INTEGER NOT NULL," +
		    "ctgs1                   TEXT NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
		    "grp2_idx                INTEGER NOT NULL," +
		    "start2                  INTEGER NOT NULL," +
		    "end2                    INTEGER NOT NULL," +
		    "ctgs2                   TEXT NOT NULL," +
		    "level                   INTEGER NOT NULL," +
		    "contained               INTEGER NOT NULL," +
		    "comment                 TEXT NOT NULL," +
			"corr					float default 0," +
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
		
		sql = "CREATE TABLE pseudo_filter (" +
		    "pair_idx                INTEGER NOT NULL," +
		    "proj1_idx               INTEGER NOT NULL," +
		    "grp2_idx                INTEGER NOT NULL," +
		    "start                   INTEGER NOT NULL," +
		    "end                     INTEGER NOT NULL," +
		    "filter_code             INTEGER NOT NULL" +
		    ")  ENGINE = InnoDB; ";
		executeUpdate(sql);
		
// Sequence hits
	    sql = "CREATE TABLE pseudo_hits (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," + // hit_id
		    "pair_idx            INTEGER NOT NULL," +
		    "proj1_idx           INTEGER NOT NULL," + // proj_props.proj_idx
		    "proj2_idx           INTEGER NOT NULL," + // proj_props.proj_idx
		    "grp1_idx            INTEGER NOT NULL," + // xgroups.idx
		    "grp2_idx            INTEGER NOT NULL," + // xgroups.idx
		    "evalue              DOUBLE NOT NULL," +  // No value, though accessed
		    "pctid               TINYINT UNSIGNED NOT NULL," +    // Col6, Avg %ID CAS515 REAL->TINYINT (rounded percent) 
		    "score               INTEGER NOT NULL," + // Col5, queryLen - not used
		    "strand              TEXT NOT NULL," +
		    "start1              INTEGER NOT NULL," +
		    "end1                INTEGER NOT NULL," + 
		    "start2              INTEGER NOT NULL," +
		    "end2                INTEGER NOT NULL, " +  
		    "query_seq           MEDIUMTEXT  NOT NULL," + // start-end of each merged hit
		    "target_seq          MEDIUMTEXT  NOT NULL," +
		    "gene_overlap		TINYINT NOT NULL, " +	  // number of genes it overlaps
		    "countpct			INTEGER UNSIGNED default 0," +  // unused 0; CAS515 number of merged, tinyint->integer
		    "cvgpct				TINYINT UNSIGNED NOT NULL," +  //was unused 0 -> CAS515 Col7 %sim
		    "refidx				INTEGER default 0," +          //used in self-synteny
			"annot1_idx			INTEGER default 0," +
			"annot2_idx			INTEGER default 0," +
		    "runsize			INTEGER default 0," +		// size of colinear set
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
		
// FPC
		sql = "CREATE TABLE contigs (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "proj_idx            INTEGER NOT NULL," +
		    "number              INTEGER NOT NULL," +
		    "grp_idx             INTEGER  NOT NULL," +
		    "anchor_pos          REAL  NOT NULL," +
		    "size                INTEGER NOT NULL," +
		    "ccb                 INTEGER NOT NULL," +
		    "remarks             TEXT NOT NULL," +
		    "INDEX (proj_idx,number)," +
		    "INDEX (proj_idx,grp_idx)," +
		    "INDEX (grp_idx)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;  ";  
		 executeUpdate(sql);
		 
		 sql = "CREATE TABLE clones (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "proj_idx            INTEGER NOT NULL," +
		    "name                VARCHAR(40) NOT NULL," +
		    "ctg_idx             INTEGER  NOT NULL," +
		    "cb1                 INTEGER  NOT NULL," +
		    "cb2                 INTEGER  NOT NULL," +
		    "bes1                VARCHAR(1) NOT NULL," +
		    "bes2                VARCHAR(1) NOT NULL," +
		    "remarks             TEXT NOT NULL," +
		    "UNIQUE INDEX (proj_idx,name)," +
		    "INDEX (ctg_idx)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctg_idx) REFERENCES contigs (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;  ";  
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE markers (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "proj_idx            INTEGER NOT NULL," +
		    "name                VARCHAR(40) NOT NULL," +
		    "type                VARCHAR(40) NOT NULL," +
		    "remarks             TEXT NOT NULL," +
		    "UNIQUE INDEX (proj_idx,name)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;   ";
		 executeUpdate(sql);
		 
		 sql = "CREATE TABLE mrk_ctg (" +
		    "mrk_idx             INTEGER NOT NULL," +
		    "ctg_idx             INTEGER NOT NULL," +
		    "pos                 INTEGER NOT NULL," +
		    "nhits               INTEGER NOT NULL," +
		    "UNIQUE INDEX (mrk_idx,ctg_idx)," +
		    "INDEX (ctg_idx)," +
		    "FOREIGN KEY (mrk_idx) REFERENCES markers (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctg_idx) REFERENCES contigs (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;   ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE mrk_clone (" +
		    "mrk_idx             INTEGER NOT NULL," +
		    "clone_idx           INTEGER NOT NULL," +
		    "UNIQUE INDEX (mrk_idx,clone_idx)," +
		    "INDEX (clone_idx)," +
		    "FOREIGN KEY (mrk_idx) REFERENCES markers (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (clone_idx) REFERENCES clones (idx) ON DELETE CASCADE" +
		    ") ENGINE = InnoDB;  ";  
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE bes_seq (" +
		    "proj_idx            INTEGER NOT NULL," +
		    "clone               VARCHAR(40) NOT NULL," +
		    "type                ENUM('r','f') NOT NULL," +
		    "seq                 TEXT NOT NULL," +
		    "rep                 INTEGER NOT NULL," +
		    "UNIQUE INDEX (proj_idx, clone, type)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE mrk_seq (" +
		    "proj_idx            INTEGER NOT NULL," +
		    "marker              VARCHAR(40) NOT NULL," +
		    "seq                 MEDIUMTEXT NOT NULL," +
		    "UNIQUE INDEX (proj_idx, marker)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE clone_remarks (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "proj_idx            INTEGER NOT NULL," +
		    "remark              TEXT, " +
		    "count               INTEGER NOT NULL, " + 
		    "INDEX(proj_idx)," +
		    "FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE clone_remarks_byctg (" +
		    "ctg_idx             INTEGER NOT NULL," +
		    "remark_idx          INTEGER NOT NULL," +
		    "count               INTEGER NOT NULL, " + 
		    "UNIQUE INDEX(remark_idx, ctg_idx)," +
		    "FOREIGN KEY (ctg_idx) REFERENCES contigs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (remark_idx) REFERENCES clone_remarks (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
// FPC hits
		 sql = "CREATE TABLE bes_hits (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "pair_idx            INTEGER NOT NULL," +
		    "proj1_idx           INTEGER NOT NULL," +
		    "proj2_idx           INTEGER NOT NULL," +
		    "clone               VARCHAR(40) NOT NULL," +
		    "bes_type            VARCHAR(1) NOT NULL," +
		    "grp2_idx            INTEGER NOT NULL," +
		    "evalue              DOUBLE NOT NULL," +
		    "pctid               REAL NOT NULL," +
		    "score              	INTEGER NOT NULL," +
		    "strand              TEXT NOT NULL," +
		    "start1              INTEGER NOT NULL," +
		    "end1                INTEGER NOT NULL, " +
		    "start2              INTEGER NOT NULL," +
		    "end2                INTEGER NOT NULL," +  
		    "query_seq           TEXT  NOT NULL," +
		    "target_seq          TEXT  NOT NULL," +
		    "gene_overlap		TINYINT NOT NULL, " +
		    "INDEX (proj1_idx, proj2_idx,grp2_idx,clone)," +
		    "INDEX (proj1_idx, proj2_idx, clone, bes_type)," +
		    "FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp2_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ") ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE mrk_hits (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "pair_idx            INTEGER NOT NULL," +
		    "proj1_idx           INTEGER NOT NULL," +
		    "marker              VARCHAR(40) NOT NULL," +
		    "grp2_idx            INTEGER NOT NULL," +
		    "evalue              DOUBLE NOT NULL," +
		    "pctid               REAL NOT NULL," +
		    "score               INTEGER	 NOT NULL," +
		    "strand              TEXT NOT NULL," +
		    "start1              INTEGER NOT NULL," +
		    "end1                INTEGER NOT NULL, " +
		    "start2              INTEGER NOT NULL," +
		    "end2                INTEGER NOT NULL, " +  
		    "query_seq           TEXT  NOT NULL," +
		    "target_seq          TEXT  NOT NULL," +
		    "gene_overlap		TINYINT NOT NULL, " +
		    "INDEX (proj1_idx,grp2_idx,marker)," +
		    "FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp2_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
	
		 sql = "CREATE TABLE fp_hits (" +
		    "idx                 INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "pair_idx            INTEGER NOT NULL," +
		    "proj1_idx           INTEGER NOT NULL," +
		    "proj2_idx           INTEGER NOT NULL," +
		    "clone1              VARCHAR(40) NOT NULL," +
		    "clone2              VARCHAR(40) NOT NULL," +
		    "ctghit_idx          INTEGER  NOT NULL," +
		    "score               DOUBLE NOT NULL," +
		    "INDEX(proj1_idx,proj2_idx,clone1)," +
		    "INDEX(proj1_idx,proj2_idx,clone2)," +
		    "FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE ctghits (" +
		    "idx                     INTEGER AUTO_INCREMENT PRIMARY KEY," +
		    "pair_idx                INTEGER NOT NULL," +
		    "ctg1_idx                INTEGER NOT NULL," +
		    "start1                  INTEGER  NOT NULL," +
		    "end1                    INTEGER  NOT NULL," +
		    "grp2_idx                INTEGER NOT NULL," +
		    "ctg2_idx                INTEGER NOT NULL," +
		    "start2                  INTEGER NOT NULL," +
		    "end2                    INTEGER NOT NULL," +
		    "score                   INTEGER NOT NULL," +
		    "slope                   ENUM('+','-') NOT NULL," +
		    "block_num               INTEGER NOT NULL," +
		    "comment                 TEXT NOT NULL," +
		    "INDEX (ctg1_idx)," +
		    "INDEX (grp2_idx)," +
		    "FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctg1_idx) REFERENCES contigs (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (grp2_idx) REFERENCES xgroups (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB;";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE mrk_block_hits (" +
		    "hit_idx                 INTEGER NOT NULL," +
		    "ctghit_idx              INTEGER NOT NULL," +
		    "INDEX (hit_idx)," +
		    "INDEX (ctghit_idx)," +
		    "FOREIGN KEY (hit_idx) REFERENCES mrk_hits (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE bes_block_hits (" +
		    "hit_idx                 INTEGER NOT NULL," +
		    "ctghit_idx              INTEGER NOT NULL," +
		    "INDEX (hit_idx)," +
		    "INDEX (ctghit_idx)," +
		    "FOREIGN KEY (hit_idx) REFERENCES bes_hits (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE fp_block_hits (" +
		    "hit_idx                 INTEGER NOT NULL," +
		    "ctghit_idx              INTEGER NOT NULL," +
		    "INDEX (hit_idx)," +
		    "INDEX (ctghit_idx)," +
		    "FOREIGN KEY (hit_idx) REFERENCES fp_hits (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE shared_mrk_block_hits (" +
		    "mrk_idx                 INTEGER NOT NULL," +
		    "ctghit_idx              INTEGER NOT NULL," +
		    "UNIQUE INDEX (ctghit_idx,mrk_idx)," +
		    "FOREIGN KEY (mrk_idx) REFERENCES markers (idx) ON DELETE CASCADE," +
		    "FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE mrk_filter (" +
		    "pair_idx                INTEGER NOT NULL," +
		    "mrk_idx                 INTEGER NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
		    "filter_code             INTEGER NOT NULL," +
		    "in_block                INTEGER NOT NULL" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		    
		 sql = "CREATE TABLE bes_filter (" +
		    "pair_idx                INTEGER NOT NULL," +
		    "clone_idx               INTEGER NOT NULL," +
		    "bes_type                ENUM('r','f') NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
		    "filter_code             INTEGER NOT NULL," +
		    "in_block                INTEGER NOT NULL" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		   
		 /** CAS515 only reference
		 sql = "CREATE TABLE shared_mrk_filter (" +
		    "pair_idx                INTEGER NOT NULL," +
		    "proj1_idx               INTEGER NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
		    "name                    VARCHAR(40) NOT NULL," +
		    "reason                  INTEGER NOT NULL" +
		    ")  ENGINE = InnoDB; ";
		 executeUpdate(sql);
		 **/
		 sql = "CREATE TABLE fp_filter (" +
		    "pair_idx                INTEGER NOT NULL," +
		    "clone1_idx              INTEGER NOT NULL," +
		    "proj2_idx               INTEGER NOT NULL," +
		    "filter                  INTEGER NOT NULL," +
		    "block                   INTEGER NOT NULL" +
		    ")  ENGINE = InnoDB;"; 
		 executeUpdate(sql);

		 sql = "SET FOREIGN_KEY_CHECKS = 1;";
		 executeUpdate(sql);
		    
		 /** CAS506 add DBVer and UPDATE, change other two **/
		 // sql = "INSERT INTO props (name,value) VALUES ('VERSION', '3.0'); ";
		 sql = "INSERT INTO props (name,value) VALUES ('VERSION','" +  SyMAP.VERSION + "'); ";
		 executeUpdate(sql);
		    
		 // referenced in Version
		 sql = "INSERT INTO props (name,value) VALUES ('DBVER','" +  SyMAP.DBVERSTR + "'); ";
		 executeUpdate(sql);
		 
		 // sql = "INSERT INTO props (name,value) VALUES ('INSTALLED', '. localtime() . '); "; enter string, not date
		 String date = Utilities.getDateOnly();
		 sql = "INSERT INTO props (name,value) VALUES ('INSTALLED', '" + date + "'); ";
		 executeUpdate(sql);
		 
		 sql = "INSERT INTO props (name,value) VALUES ('UPDATE', '" + date + "'); ";
		 executeUpdate(sql);
	}
	public int executeUpdate(String sql) 
	{
		//String x = sql;
		//if (sql.contains("(")) x = sql.substring(0, sql.indexOf("("));
		//System.out.println(x);
		
		int ret=0;
		try
		{
			Statement stmt = mConn.createStatement();
			ret = stmt.executeUpdate(sql);
			stmt.close();
		}
		catch (Exception e){ErrorReport.die(e, "Query failed:" + sql);}
		
		return ret;
	}
	Connection mConn;
}
