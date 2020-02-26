/*******************************************************

 Don't add fields to the middle of any of these tables
 because there are places in the code where values are inserted
 with order assumed!!!!!!! 
Also if you add fields to the end of these tables you probably
should change the code that populates the tables. 

*****************************************************/

CREATE TABLE props (
    name            VARCHAR(40) NOT NULL,
    value           VARCHAR(255) NOT NULL
    )  ENGINE = InnoDB;

CREATE TABLE projects (
    idx             INTEGER NOT NULL AUTO_INCREMENT,
    type            enum('fpc','pseudo')  NOT NULL,
    name            VARCHAR(40) NOT NULL,
	loaddate		datetime NOT NULL,
	hasannot		boolean default 0,
	annotdate		datetime,
    PRIMARY KEY (idx),
    UNIQUE INDEX (name),
    INDEX (type)
    )  ENGINE = InnoDB;

CREATE TABLE proj_props (
    proj_idx        INTEGER NOT NULL,
    name            VARCHAR(40) NOT NULL,
    value           VARCHAR(255) NOT NULL,
    UNIQUE INDEX (proj_idx,name),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE groups (
    idx                     INTEGER NOT NULL AUTO_INCREMENT,
    proj_idx                INTEGER NOT NULL,
    name                    VARCHAR(40) NOT NULL,
    fullname                    VARCHAR(40) NOT NULL,
    sort_order              INTEGER UNSIGNED NOT NULL,
	flipped					BOOLEAN default 0,
    PRIMARY KEY (idx),
    UNIQUE INDEX (proj_idx,name),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE pairs (
    idx                     INTEGER AUTO_INCREMENT PRIMARY KEY,
    proj1_idx               INTEGER NOT NULL,
    proj2_idx               INTEGER NOT NULL,
	aligned					BOOLEAN default 0,
    aligndate				datetime,
    UNIQUE INDEX (proj1_idx,proj2_idx),
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE pair_props (
    pair_idx        INTEGER NOT NULL,
    proj1_idx       INTEGER NOT NULL,
    proj2_idx       INTEGER NOT NULL,
    name            VARCHAR(400) NOT NULL,
    value           VARCHAR(255) NOT NULL,
    UNIQUE INDEX(proj1_idx,proj2_idx,name),
    INDEX (proj2_idx),
    FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE projgrp (
    idx             INTEGER NOT NULL AUTO_INCREMENT,
    name            VARCHAR(40) NOT NULL,
    PRIMARY KEY (idx),
    UNIQUE INDEX (name)
    )  ENGINE = InnoDB;

CREATE TABLE projgrp_memb (
    proj_grp_idx        INTEGER NOT NULL,
   	proj_idx			INTEGER NOT NULL,
    INDEX (proj_grp_idx),
    INDEX (proj_idx),
    FOREIGN KEY (proj_grp_idx) REFERENCES projgrp (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
        )  ENGINE = InnoDB;

CREATE TABLE pseudos (
    grp_idx             INTEGER NOT NULL,
    file                TEXT NOT NULL,
    length              INTEGER NOT NULL,
    PRIMARY KEY (grp_idx),
    FOREIGN KEY (grp_idx) REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE pseudo_annot (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    grp_idx             INTEGER NOT NULL,
    type                VARCHAR(20) NOT NULL,
    name                TEXT  NOT NULL,
    start               INTEGER NOT NULL,
    end                 INTEGER NOT NULL,
    strand              ENUM('+','-') NOT NULL,
    text                TEXT  NOT NULL,
    INDEX (grp_idx,type),
    FOREIGN KEY (grp_idx) REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;  
  
CREATE Table annot_key ( 
				idx 				INTEGER AUTO_INCREMENT PRIMARY KEY,	 
				proj_idx			INTEGER NOT NULL, 
				keyname				TEXT, 
				count				BIGINT DEFAULT 0, 
				FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE 
				) ENGINE = InnoDB;

CREATE TABLE pseudo_seq2 (
    grp_idx             INTEGER NOT NULL,
    chunk               INTEGER NOT NULL,
    seq                 LONGTEXT  NOT NULL,
    INDEX (grp_idx),
    FOREIGN KEY (grp_idx) REFERENCES groups (idx) ON DELETE CASCADE
    ) ENGINE = InnoDB; 

CREATE TABLE contigs (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    proj_idx            INTEGER NOT NULL,
    number              INTEGER NOT NULL,
    grp_idx             INTEGER  NOT NULL,
    anchor_pos          REAL  NOT NULL,
    size                INTEGER NOT NULL,
    ccb                 INTEGER NOT NULL,
    remarks             TEXT NOT NULL,
    INDEX (proj_idx,number),
    INDEX (proj_idx,grp_idx),
    INDEX (grp_idx),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp_idx) REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;    

CREATE TABLE clones (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    proj_idx            INTEGER NOT NULL,
    name                VARCHAR(40) NOT NULL,
    ctg_idx             INTEGER  NOT NULL,
    cb1                 INTEGER  NOT NULL,
    cb2                 INTEGER  NOT NULL,
    bes1                VARCHAR(1) NOT NULL,
    bes2                VARCHAR(1) NOT NULL,
    remarks             TEXT NOT NULL,
    UNIQUE INDEX (proj_idx,name),
    INDEX (ctg_idx),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctg_idx) REFERENCES contigs (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;    

CREATE TABLE markers (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    proj_idx            INTEGER NOT NULL,
    name                VARCHAR(40) NOT NULL,
    type                VARCHAR(40) NOT NULL,
    remarks             TEXT NOT NULL,
    UNIQUE INDEX (proj_idx,name),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;   

CREATE TABLE mrk_ctg (
    mrk_idx             INTEGER NOT NULL,
    ctg_idx             INTEGER NOT NULL,
    pos                 INTEGER NOT NULL,
    nhits               INTEGER NOT NULL,
    UNIQUE INDEX (mrk_idx,ctg_idx),
    INDEX (ctg_idx),
    FOREIGN KEY (mrk_idx) REFERENCES markers (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctg_idx) REFERENCES contigs (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;   

CREATE TABLE mrk_clone (
    mrk_idx             INTEGER NOT NULL,
    clone_idx           INTEGER NOT NULL,
    UNIQUE INDEX (mrk_idx,clone_idx),
    INDEX (clone_idx),
    FOREIGN KEY (mrk_idx) REFERENCES markers (idx) ON DELETE CASCADE,
    FOREIGN KEY (clone_idx) REFERENCES clones (idx) ON DELETE CASCADE
    ) ENGINE = InnoDB;    

CREATE TABLE bes_seq (
    proj_idx            INTEGER NOT NULL,
    clone               VARCHAR(40) NOT NULL,
    type                ENUM('r','f') NOT NULL,
    seq                 TEXT NOT NULL,
    rep                 INTEGER NOT NULL,
    UNIQUE INDEX (proj_idx, clone, type),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE mrk_seq (
    proj_idx            INTEGER NOT NULL,
    marker              VARCHAR(40) NOT NULL,
    seq                 MEDIUMTEXT NOT NULL,
    UNIQUE INDEX (proj_idx, marker),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE clone_remarks (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    proj_idx            INTEGER NOT NULL,
    remark              TEXT, 
    count               INTEGER NOT NULL,  
    INDEX(proj_idx),
    FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE clone_remarks_byctg (
    ctg_idx             INTEGER NOT NULL,
    remark_idx          INTEGER NOT NULL,
    count               INTEGER NOT NULL,  
    UNIQUE INDEX(remark_idx, ctg_idx),
    FOREIGN KEY (ctg_idx) REFERENCES contigs (idx) ON DELETE CASCADE,
    FOREIGN KEY (remark_idx) REFERENCES clone_remarks (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE bes_hits (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    pair_idx            INTEGER NOT NULL,
    proj1_idx           INTEGER NOT NULL,
    proj2_idx           INTEGER NOT NULL,
    clone               VARCHAR(40) NOT NULL,
    bes_type            VARCHAR(1) NOT NULL,
    grp2_idx            INTEGER NOT NULL,
    evalue              DOUBLE NOT NULL,
    pctid               REAL NOT NULL,
    score              	INTEGER NOT NULL,
    strand              TEXT NOT NULL,
    start1              INTEGER NOT NULL,
    end1                INTEGER NOT NULL, 
    start2              INTEGER NOT NULL,
    end2                INTEGER NOT NULL,  
    query_seq           TEXT  NOT NULL,
    target_seq          TEXT  NOT NULL,
    gene_overlap		TINYINT NOT NULL, 
    INDEX (proj1_idx, proj2_idx,grp2_idx,clone),
    INDEX (proj1_idx, proj2_idx, clone, bes_type),
    FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp2_idx) REFERENCES groups (idx) ON DELETE CASCADE
    ) ENGINE = InnoDB; 

CREATE TABLE mrk_hits (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    pair_idx            INTEGER NOT NULL,
    proj1_idx           INTEGER NOT NULL,
    marker              VARCHAR(40) NOT NULL,
    grp2_idx            INTEGER NOT NULL,
    evalue              DOUBLE NOT NULL,
    pctid               REAL NOT NULL,
    score               INTEGER	 NOT NULL,
    strand              TEXT NOT NULL,
    start1              INTEGER NOT NULL,
    end1                INTEGER NOT NULL, 
    start2              INTEGER NOT NULL,
    end2                INTEGER NOT NULL,   
    query_seq           TEXT  NOT NULL,
    target_seq          TEXT  NOT NULL,
    gene_overlap		TINYINT NOT NULL, 
    INDEX (proj1_idx,grp2_idx,marker),
    FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp2_idx) REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE pseudo_hits (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    pair_idx            INTEGER NOT NULL,
    proj1_idx           INTEGER NOT NULL,
    proj2_idx           INTEGER NOT NULL,
    grp1_idx            INTEGER NOT NULL,
    grp2_idx            INTEGER NOT NULL,
    evalue              DOUBLE NOT NULL,
    pctid               REAL NOT NULL,
    score               INTEGER NOT NULL,
    strand              TEXT NOT NULL,
    start1              INTEGER NOT NULL,
    end1                INTEGER NOT NULL, 
    start2              INTEGER NOT NULL,
    end2                INTEGER NOT NULL,   
    query_seq           MEDIUMTEXT  NOT NULL,
    target_seq          MEDIUMTEXT  NOT NULL,
    gene_overlap		TINYINT NOT NULL, 
    countpct			TINYINT UNSIGNED NOT NULL,
    cvgpct				TINYINT UNSIGNED NOT NULL,
    refidx				INTEGER default 0,
	annot1_idx			INTEGER default 0,
	annot2_idx			INTEGER default 0,
    runsize		INTEGER default 0,
    INDEX (proj1_idx,proj2_idx,grp1_idx,grp2_idx),
    FOREIGN KEY (pair_idx)  REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp1_idx)  REFERENCES groups (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp2_idx)  REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE pseudo_hits_annot (
    hit_idx             INTEGER NOT NULL,
    annot_idx           INTEGER NOT NULL,
    olap	            INTEGER NOT NULL,
    UNIQUE (hit_idx, annot_idx),
    INDEX (annot_idx),
    FOREIGN KEY (hit_idx)  REFERENCES pseudo_hits (idx) ON DELETE CASCADE,
    FOREIGN KEY (annot_idx) REFERENCES pseudo_annot (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE fp_hits (
    idx                 INTEGER AUTO_INCREMENT PRIMARY KEY,
    pair_idx            INTEGER NOT NULL,
    proj1_idx           INTEGER NOT NULL,
    proj2_idx           INTEGER NOT NULL,
    clone1              VARCHAR(40) NOT NULL,
    clone2              VARCHAR(40) NOT NULL,
    ctghit_idx          INTEGER  NOT NULL,
    score               DOUBLE NOT NULL,
    INDEX(proj1_idx,proj2_idx,clone1),
    INDEX(proj1_idx,proj2_idx,clone2),
    FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE ctghits (
    idx                     INTEGER AUTO_INCREMENT PRIMARY KEY,
    pair_idx                INTEGER NOT NULL,
    ctg1_idx                INTEGER NOT NULL,
    start1                  INTEGER  NOT NULL,
    end1                    INTEGER  NOT NULL,
    grp2_idx                INTEGER NOT NULL,
    ctg2_idx                INTEGER NOT NULL,
    start2                  INTEGER NOT NULL,
    end2                    INTEGER NOT NULL,
    score                   INTEGER NOT NULL,
    slope                   ENUM('+','-') NOT NULL,
    block_num               INTEGER NOT NULL,
    comment                 TEXT NOT NULL,
    INDEX (ctg1_idx),
    INDEX (grp2_idx),
    FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctg1_idx) REFERENCES contigs (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp2_idx) REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE blocks (
    idx                     INTEGER AUTO_INCREMENT PRIMARY KEY,
    pair_idx                INTEGER NOT NULL,
    blocknum                INTEGER NOT NULL,
    proj1_idx               INTEGER NOT NULL,
    grp1_idx                INTEGER NOT NULL,
    start1                  INTEGER NOT NULL,
    end1                    INTEGER NOT NULL,
    ctgs1                   TEXT NOT NULL,
    proj2_idx               INTEGER NOT NULL,
    grp2_idx                INTEGER NOT NULL,
    start2                  INTEGER NOT NULL,
    end2                    INTEGER NOT NULL,
    ctgs2                   TEXT NOT NULL,
    level                   INTEGER NOT NULL,
    contained               INTEGER NOT NULL,
    comment                 TEXT NOT NULL,
	corr					float default 0,
	score					INTEGER default 0,
	ngene1					integer default 0,
	ngene2					integer default 0,
	genef1					float default 0,
	genef2					float default 0,
    INDEX (proj1_idx,grp1_idx),
    INDEX (proj2_idx,grp2_idx),
    FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj1_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (proj2_idx) REFERENCES projects (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp1_idx) REFERENCES groups (idx) ON DELETE CASCADE,
    FOREIGN KEY (grp2_idx) REFERENCES groups (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE mrk_block_hits (
    hit_idx                 INTEGER NOT NULL,
    ctghit_idx              INTEGER NOT NULL,
    INDEX (hit_idx),
    INDEX (ctghit_idx),
    FOREIGN KEY (hit_idx) REFERENCES mrk_hits (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE bes_block_hits (
    hit_idx                 INTEGER NOT NULL,
    ctghit_idx              INTEGER NOT NULL,
    INDEX (hit_idx),
    INDEX (ctghit_idx),
    FOREIGN KEY (hit_idx) REFERENCES bes_hits (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE fp_block_hits (
    hit_idx                 INTEGER NOT NULL,
    ctghit_idx              INTEGER NOT NULL,
    INDEX (hit_idx),
    INDEX (ctghit_idx),
    FOREIGN KEY (hit_idx) REFERENCES fp_hits (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE shared_mrk_block_hits (
    mrk_idx                 INTEGER NOT NULL,
    ctghit_idx              INTEGER NOT NULL,
    UNIQUE INDEX (ctghit_idx,mrk_idx),
    FOREIGN KEY (mrk_idx) REFERENCES markers (idx) ON DELETE CASCADE,
    FOREIGN KEY (ctghit_idx) REFERENCES ctghits (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB; 

CREATE TABLE pseudo_block_hits (
    hit_idx                 INTEGER NOT NULL,
    block_idx               INTEGER NOT NULL,
    INDEX (hit_idx),
    INDEX (block_idx),
    FOREIGN KEY (hit_idx) REFERENCES pseudo_hits (idx) ON DELETE CASCADE,
    FOREIGN KEY (block_idx) REFERENCES blocks (idx) ON DELETE CASCADE
    )  ENGINE = InnoDB;

CREATE TABLE mrk_filter (
    pair_idx                INTEGER NOT NULL,
    mrk_idx                 INTEGER NOT NULL,
    proj2_idx               INTEGER NOT NULL,
    filter_code             INTEGER NOT NULL,
    in_block                INTEGER NOT NULL
    )  ENGINE = InnoDB; 

CREATE TABLE bes_filter (
    pair_idx                INTEGER NOT NULL,
    clone_idx               INTEGER NOT NULL,
    bes_type                ENUM('r','f') NOT NULL,
    proj2_idx               INTEGER NOT NULL,
    filter_code             INTEGER NOT NULL,
    in_block                INTEGER NOT NULL
    )  ENGINE = InnoDB; 

CREATE TABLE pseudo_filter (
    pair_idx                INTEGER NOT NULL,
    proj1_idx               INTEGER NOT NULL,
    grp2_idx                INTEGER NOT NULL,
    start                   INTEGER NOT NULL,
    end                     INTEGER NOT NULL,
    filter_code             INTEGER NOT NULL
    )  ENGINE = InnoDB; 

CREATE TABLE shared_mrk_filter (
    pair_idx                INTEGER NOT NULL,
    proj1_idx               INTEGER NOT NULL,
    proj2_idx               INTEGER NOT NULL,
    name                    VARCHAR(40) NOT NULL,
    reason                  INTEGER NOT NULL
    )  ENGINE = InnoDB; 

CREATE TABLE fp_filter (
    pair_idx                INTEGER NOT NULL,
    clone1_idx              INTEGER NOT NULL,
    proj2_idx               INTEGER NOT NULL,
    filter                  INTEGER NOT NULL,
    block                   INTEGER NOT NULL
    )  ENGINE = InnoDB; 


SET FOREIGN_KEY_CHECKS = 1;
INSERT INTO props (name,value) VALUES ('VERSION', '3.0'); 
INSERT INTO props (name,value) VALUES ('INSTALLED', '" . localtime() . "'); 

