package symap.sequence;

import java.awt.Point;

/**
 * The data for the Track (i.e. project name, display name, orientation, etc...)
 * It is used for History, i.e back and forth
 */
public  class TrackData {
	private Class <?> trackClass;
	private int project, otherProject;
	private int orient;
	private Point moveOffset;
	private double defaultBpPerPixel;
	private double bpPerPixel;
	private long start, end, size;
	private double height, width;
	
	private int grpIdx;
	
	private boolean bShowRuler, bShowGene, bShowAnnot, bShowGeneLine, bFlipped; 
	private boolean bShowGap, bShowCentromere;
	private boolean bShowScoreLine, bShowHitLen; 	
	private boolean bShowScoreText, bShowHitNumText; 
	private boolean bShowBlockText, bShowCsetText, bShowNoText;   
	private boolean bHighPopupGene; 
	private boolean bHighConserved;
	private Annotation selectedGeneObj=null;

	protected TrackData(Track track) {
		trackClass        = track.getClass();
		project           = track.projIdx;
		start             = track.start.getValue();
		end               = track.end.getValue();
		size              = track.size.getValue();
		orient            = track.orient;
		moveOffset        = new Point(track.moveOffset);
		defaultBpPerPixel = track.defaultBpPerPixel;
		bpPerPixel        = track.bpPerPixel;
		height            = track.height;
		width             = track.width;
		otherProject      = track.otherProjIdx;
		
		Sequence seq = (Sequence) track;
		grpIdx          = seq.grpIdx;
		
		bShowRuler      = seq.bShowRuler;
		bShowGene       = seq.bShowGene;
		bShowGeneLine  	= seq.bShowGeneLine;
		bShowAnnot      = seq.bShowAnnot;
		bShowGap        = seq.bShowGap;
		bShowCentromere = seq.bShowCentromere;
		bShowScoreLine  = seq.bShowScoreLine;	
		bShowHitLen 	= seq.bShowHitLen;	
		
		bShowNoText  	= seq.bShowNoText;	
		bShowBlockText  = seq.bShowBlockText;	
		bShowCsetText 	= seq.bShowCsetText;	
		bShowScoreText  = seq.bShowScoreText;	
		bShowHitNumText = seq.bShowHitNumText;	
		bFlipped        = seq.isFlipped();	
		
		bHighPopupGene = seq.bHighGenePopup;
		bHighConserved = seq.bHighConserved;
		selectedGeneObj  = seq.selectedGeneObj;
	}

	protected void setTrack(Track track) {
		track.start.setValue(start);
		track.end.setValue(end);
		track.size.setValue(size);
		track.projIdx           = project;
		track.orient            = orient;
		track.moveOffset.setLocation(moveOffset);
		track.defaultBpPerPixel = defaultBpPerPixel;
		track.bpPerPixel        = bpPerPixel;
		track.height            = height;
		track.width             = width;
		track.otherProjIdx      = otherProject;
		track.clearAllBuild(); // set Track.hasBuild = false; for all tracks
		
		Sequence seq = (Sequence)track;
		seq.grpIdx          = grpIdx;
		seq.bShowRuler      = bShowRuler;
		seq.bShowGene       = bShowGene;
		seq.bShowGap        = bShowGap;
		seq.bShowCentromere = bShowCentromere;
		
		seq.bShowGeneLine   = bShowGeneLine;
		seq.bShowAnnot      = bShowAnnot;
		
		seq.bShowScoreLine  = bShowScoreLine; 
		seq.bShowHitLen 	= bShowHitLen;	
		
		seq.bShowNoText  	= bShowNoText;	
		seq.bShowBlockText  = bShowBlockText;	
		seq.bShowCsetText 	= bShowCsetText;
		seq.bShowScoreText 	= bShowScoreText; 
		seq.bShowHitNumText = bShowHitNumText; 
		seq.flipSeq(bFlipped); 	
		
		seq.bHighGenePopup = bHighPopupGene;
		seq.bHighConserved = bHighConserved;
		seq.selectedGeneObj =  selectedGeneObj;
	}

	public Class <?> getTrackClass() {return trackClass;}
	
	public int getProject() {return project;}

	public int getOtherProject() {return otherProject;}

	protected int getGroup() {return grpIdx;}
}
