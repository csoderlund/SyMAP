package symap.sequence;

/**
 * The data for the Track (i.e. project name, display name, orientation, etc...)
 * It is used for History, i.e back and forth
 * CAS551 remove hitLength and HitLine
 */
public  class TrackData {
	private Class <?> trackClass;
	private int project, otherProject;
	private int orient;
	// CAS551 dead private Point moveOffset;
	private double defaultBpPerPixel, bpPerPixel;
	private int start, end, size;
	private double height, width;

	private int grpIdx;
	
	private boolean bFlipped;
	private boolean bShowRuler, bShowGap, bShowCentromere, bShowGene, bShowHitLen, bShowScoreLine;
	
	private boolean bShowAnnot, bShowGeneNum,  bShowGeneLine, bHighPopupGene, bHighConserved; 
	
	private boolean bShowScoreText, bShowHitNumText, bShowBlockText, bShowCsetText, bShowNoText;   

	private Annotation selectedGeneObj=null;

	protected TrackData(Sequence seq) {
		trackClass        = seq.getClass();
		project           = seq.projIdx;
		start             = seq.gnDstart;
		end               = seq.gnDend;
		size              = seq.gnSize;
		orient            = seq.orient;
		defaultBpPerPixel = seq.defaultBpPerPixel;
		bpPerPixel        = seq.bpPerPixel;
		height            = seq.height;
		width             = seq.width;
		otherProject      = seq.otherProjIdx;
		
		grpIdx          = seq.grpIdx;
		
		bFlipped        = seq.isFlipped();	
		bShowRuler      = seq.bShowRuler;
		bShowGap        = seq.bShowGap;
		bShowCentromere = seq.bShowCentromere;
		bShowGene      = seq.bShowGene;
		bShowHitLen     = seq.bShowHitLen;
		bShowScoreLine = seq.bShowScoreLine;
		
		bShowGeneNum    = seq.bShowGeneNum;
		bShowGeneLine  	= seq.bShowGeneLine;
		bShowAnnot      = seq.bShowAnnot;
		bHighPopupGene = seq.bHighGenePopup;
		bHighConserved = seq.bHighConserved;
		
		bShowNoText  	= seq.bShowNoText;	
		bShowBlockText  = seq.bShowBlockText;	
		bShowCsetText 	= seq.bShowCsetText;	
		bShowScoreText  = seq.bShowScoreText;	
		bShowHitNumText = seq.bShowHitNumText;	
			
		selectedGeneObj  = seq.selectedGeneObj;
	}

	protected void setTrack(Sequence seq) {
		seq.gnDstart = start;
		seq.gnDend = end;
		seq.gnSize = size;
		seq.projIdx           = project;
		seq.orient            = orient;
		seq.defaultBpPerPixel = defaultBpPerPixel;
		seq.bpPerPixel        = bpPerPixel;
		seq.height            = height;
		seq.width             = width;
		seq.otherProjIdx      = otherProject;
		seq.setAllBuild(); // set Track.hasBuild = false; for all seqs
		
		seq.grpIdx          = grpIdx;
		seq.flipSeq(bFlipped); 	
		seq.bShowRuler      = bShowRuler;
		seq.bShowGap        = bShowGap;
		seq.bShowCentromere = bShowCentromere;
		seq.bShowGene        = bShowGene;
		seq.bShowHitLen      = bShowHitLen;
		seq.bShowScoreLine   = bShowScoreLine;
		
		seq.bShowGeneNum    = bShowGeneNum;
		seq.bShowGeneLine   = bShowGeneLine;
		seq.bShowAnnot      = bShowAnnot;
		seq.bHighGenePopup = bHighPopupGene;
		seq.bHighConserved = bHighConserved;
		
		seq.bShowNoText  	= bShowNoText;	
		seq.bShowBlockText  = bShowBlockText;	
		seq.bShowCsetText 	= bShowCsetText;
		seq.bShowScoreText 	= bShowScoreText; 
		seq.bShowHitNumText = bShowHitNumText; 
		
		seq.selectedGeneObj =  selectedGeneObj;
	}

	public Class <?> getTrackClass() {return trackClass;}
	
	public int getProject() {return project;}

	public int getOtherProject() {return otherProject;}

	protected int getGroup() {return grpIdx;}
}
