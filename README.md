### SyMAP (Synteny Mapping and Analysis Program) ###

**Description**: SyMAP is a software package for detecting, displaying, and querying syntenic relationships between a fully sequenced genome and   
(i) another sequenced genome, (ii) draft sequence, and (iii) self-synteny.  It is designed for divergent eukaryotic genomes (not bacteria).

**Download SyMAP tarball**: https://github.com/csoderlund/SyMAP/releases. The SyMAP tarball contains the executable jar file, all necessary external software, and demo files.

**Requirements**: Java v17 or later and MySQL (or MariaDB). SyMAP uses [MUMmer](https://mummer4.github.io) for the sequence to sequence alignment, which requires Perl.   
SyMAP has been tested on Linux and MacOS.

**To use**: 
1. Download the latest SyMAP tarball.
2. Put the tarball in the location you want the symap_5 directory and untar it (tar xf symap_5.tar.gz).
3. Follow the instructions in https://csoderlund.github.io/SyMAP/SystemGuide.html#demo to try the demo.

**Full Documentation**: https://csoderlund.github.io/SyMAP

**SyMAP github code**: To build, see SyMAP/java/README.

**References**:  
C. Soderlund, M. Bomhoff, and W. Nelson (2011) SyMAP v3.4: a turnkey synteny system with application to plant genomes. Nucleic Acids Research 39(10):e68C. [Link](https://academic.oup.com/nar/article/39/10/e68/1310457).  

C. Soderlund, W. Nelson, A. Shoemaker and A. Paterson (2006) SyMAP: A system for discovering and viewing syntenic regions of FPC maps. Genome Research 16:1159-1168. [Link](http://genome.cshlp.org/content/16/9/1159.abstract).

