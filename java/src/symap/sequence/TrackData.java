package symap.sequence;

/**
 * The data for the Track (i.e. project name, display name, orientation, etc...)
 * It is used for History, i.e back and forth
 * CAS551 remove hitLength and HitLine CAS551 dead private Point moveOffset;
 */
public  class TrackData {
	private Class <?> trackClass;
	private int project, otherProject;
	private int orient;
	
	private double defaultBpPerPixel, bpPerPixel;
	private int start, end, size;
	private double height, width;

	private int grpIdx;
	
	private boolean bFlipped;
	private boolean bShowRuler, bShowGap, bShowCentromere, bShowGene, bShowHitLen, bShowScoreLine;
	
	private boolean bShowAnnot, bShowGeneNum,  bShowGeneLine, bHighPopupGene, bHighHit2g2, bHighHit1g2; 
	
	private boolean bShowScoreText, bShowHitNumText, bShowBlockText, bShowCsetText, bShowNoText;   

	private Annotation selectedGeneObj=null;
	private String geneStr="";

	protected TrackData(Sequence seq) {
		trackClass        = seq.getClass();
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
		bShowRuler      = seq.sfilObj.bShowRuler;
		bShowGap        = seq.sfilObj.bShowGap;
		bShowCentromere = seq.sfilObj.bShowCentromere;
		bShowGene       = seq.sfilObj.bShowGene;
		bShowHitLen     = seq.sfilObj.bShowHitLen;
		bShowScoreLine  = seq.sfilObj.bShowScoreLine;
		
		bShowGeneNum    = seq.sfilObj.bShowGeneNum;
		bShowGeneLine  	= seq.sfilObj.bShowGeneLine;
		bShowAnnot      = seq.sfilObj.bShowAnnot;
		bHighPopupGene  = seq.sfilObj.bHighGenePopup;
		bHighHit2g2  	= seq.sfilObj.bHitHighg2x2;
		bHighHit1g2  	= seq.sfilObj.bHitHighg2x1;
		
		bShowNoText  	= seq.sfilObj.bShowNoText;	
		bShowBlockText  = seq.sfilObj.bShowBlockText;	
		bShowCsetText 	= seq.sfilObj.bShowCsetText;	
		bShowScoreText  = seq.sfilObj.bShowScoreText;	
		bShowHitNumText = seq.sfilObj.bShowHitNumText;	
			
		geneStr = seq.sfilObj.savGeneStr.trim();
		if (!geneStr.equals("")) selectedGeneObj  = seq.selectedGeneObj;
	}

	protected void setTrack(Sequence seq) {
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
		seq.sfilObj.xFlipSeq(bFlipped); 	
		seq.sfilObj.bShowRuler       = bShowRuler;
		seq.sfilObj.bShowGap         = bShowGap;
		seq.sfilObj.bShowCentromere  = bShowCentromere;
		seq.sfilObj.bShowGene        = bShowGene;
		seq.sfilObj.bShowHitLen      = bShowHitLen;
		seq.sfilObj.bShowScoreLine   = bShowScoreLine;
		
		seq.sfilObj.bShowGeneNum    = bShowGeneNum;
		seq.sfilObj.bShowGeneLine   = bShowGeneLine;
		seq.sfilObj.bShowAnnot      = bShowAnnot;
		seq.sfilObj.bHighGenePopup  = bHighPopupGene;
		seq.sfilObj.bHitHighg2x2  	= bHighHit2g2;
		seq.sfilObj.bHitHighg2x1  	= bHighHit1g2;
		
		seq.sfilObj.bShowNoText  	= bShowNoText;	
		seq.sfilObj.bShowBlockText  = bShowBlockText;	
		seq.sfilObj.bShowCsetText 	= bShowCsetText;
		seq.sfilObj.bShowScoreText 	= bShowScoreText; 
		seq.sfilObj.bShowHitNumText = bShowHitNumText; 
		
		if (!geneStr.equals("")) {
			seq.setSelectedGene(selectedGeneObj);
			seq.sfilObj.savGeneStr = geneStr;
		}
		else seq.noSelectedGene();
	}

	public Class <?> getTrackClass() {return trackClass;}
	
	public int getProject() {return project;}

	public int getOtherProject() {return otherProject;}

	protected int getGroup() {return grpIdx;}
}
