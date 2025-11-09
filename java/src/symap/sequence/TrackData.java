package symap.sequence;

/**
 * The data for the Track (i.e. project name, display name, orientation, etc...)
 * It is used for History, i.e back and forth
 */
public  class TrackData {
	private int project, otherProject;
	private int orient;
	
	private double defaultBpPerPixel, bpPerPixel;
	private int start, end, size;
	private double height, width;

	private int grpIdx;
	
	private boolean bShowRuler, bShowGap, bShowCentromere, bShowGene, bShowHitLen, bShowScoreLine;
	private boolean bShowGeneNum,  bShowAnnot, bShowGeneNumHit,  bShowAnnotHit;
	private boolean bShowGeneLine, bHighPopupGene;
	private boolean bShowScoreText, bShowHitNumText, bShowBlockText, bShowBlock1stText, bShowCsetText, bShowNoText;   

	private boolean bFlipped;
	private boolean bGeneNCheck;
	private Annotation selectGeneObj=null; 
	
	private boolean bHighHit2g2, bHighHit2g1, bHighHit2g0; 
	
	// This is called every time there is a change to the Display; save current state for back button
	protected TrackData(Sequence seq) { 
		project           = seq.projIdx;
		start             = seq.chrDisplayStart;
		end               = seq.chrDisplayEnd;
		size              = seq.chrSize;
		orient            = seq.orient;
		defaultBpPerPixel = seq.defaultBpPerPixel;
		bpPerPixel        = seq.bpPerPixel;
		height            = seq.height;
		width             = seq.width;
		otherProject      = seq.otherProjIdx;
		
		grpIdx          = seq.grpIdx;
		
		bFlipped        = seq.isFlipped();	
		
		Sfilter sf = seq.sfilObj;
		bShowRuler      = sf.bShowRuler;
		bShowGap        = sf.bShowGap;
		bShowCentromere = sf.bShowCentromere;
		bShowGene       = sf.bShowGene;
		bShowHitLen     = sf.bShowHitLen;
		bShowScoreLine  = sf.bShowScoreLine;
		
		bShowGeneNum    = sf.bShowGeneNum;
		bShowGeneNumHit = sf.bShowGeneNumHit;
		bShowAnnotHit   = sf.bShowAnnotHit;
		bShowGeneLine  	= sf.bShowGeneLine;
		bHighPopupGene  = sf.bHighGenePopup;
		
		bShowNoText  	= sf.bShowNoText;	
		bShowBlock1stText = sf.bShowBlock1stText; 
		bShowBlockText  = sf.bShowBlockText;	
		bShowCsetText 	= sf.bShowCsetText;	
		bShowScoreText  = sf.bShowScoreText;	
		bShowHitNumText = sf.bShowHitNumText;	
		
		bGeneNCheck 	= sf.bGeneNCheck;		 
		selectGeneObj   = sf.selectGeneObj;
		
		bHighHit2g2  	= sf.bHighG2x2;
		bHighHit2g1  	= sf.bHighG2x1;
		bHighHit2g0  	= sf.bHighG2x0;
	}

	// this gets called on History back/forth to reinstate state
	protected void setTrack(Sequence seq) {
		Sfilter sf = seq.sfilObj;
		
		seq.chrDisplayStart   = start;
		seq.chrDisplayEnd 	  = end;
		seq.chrSize 		  = size;
		seq.projIdx           = project;
		seq.orient            = orient;
		seq.defaultBpPerPixel = defaultBpPerPixel;
		seq.bpPerPixel        = bpPerPixel;
		seq.height            = height;
		seq.width             = width;
		seq.otherProjIdx      = otherProject;
		seq.setAllBuild(); // set Track.hasBuild = false; for all seqs
		
		seq.grpIdx          		 = grpIdx;
		sf.xFlipSeq(bFlipped); 	
		sf.bShowRuler       = bShowRuler;
		sf.bShowGap         = bShowGap;
		sf.bShowCentromere  = bShowCentromere;
		sf.bShowGene 		 = bShowGene;
		sf.bShowHitLen      = bShowHitLen;
		sf.bShowScoreLine   = bShowScoreLine;
		
		sf.bShowGeneNum    = bShowGeneNum;
		sf.bShowGeneNumHit = bShowGeneNumHit;
		sf.bShowAnnot      = bShowAnnot;
		sf.bShowAnnotHit   = bShowAnnotHit;
		sf.bShowGeneLine   = bShowGeneLine;
		sf.bHighGenePopup  = bHighPopupGene;
		
		sf.bShowNoText  	= bShowNoText;	
		sf.bShowBlock1stText = bShowBlock1stText;
		sf.bShowBlockText  = bShowBlockText;	
		sf.bShowCsetText 	= bShowCsetText;
		sf.bShowScoreText 	= bShowScoreText; 
		sf.bShowHitNumText = bShowHitNumText; 
		
		if (sf.selectGeneObj!=null) seq.setSelectGene(null);
		if (selectGeneObj!=null)    seq.setSelectGene(selectGeneObj);
		sf.selectGeneObj = selectGeneObj;
		sf.bGeneNCheck   = bGeneNCheck;
		
		sf.bHighG2x2  	= bHighHit2g2;
		sf.bHighG2x1  	= bHighHit2g1;
		sf.bHighG2x0  	= bHighHit2g0;
	}
}
