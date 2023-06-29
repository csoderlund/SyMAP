package symap.sequence;

import symap.track.TrackData;
import symap.track.Track;

/**
 * The data of a Sequence (i.e. Sequence name, show ruler or not, and sequence offset).  
 * CAS542 not sure if this does anything - some where not set and did not matter
 */
public class SequenceTrackData extends TrackData {
	private int group;
	
	private boolean showGene, showGeneLine, showFullGene;
	private boolean showGap, showCentromere;
	private boolean showRuler, showAnnot;
	private boolean showHitLen, showScoreLine, showScoreValue, showHitNum; 	
	private boolean flipped; 						

	protected SequenceTrackData(Track sequence) {
		super(sequence);

		Sequence seq = (Sequence)sequence;
		group          = seq.group;
		showRuler      = seq.bShowRuler;
		showGene       = seq.bShowGene;
		showGeneLine  = seq.bShowGeneLine;
		showAnnot      = seq.bShowAnnot;
		showGap        = seq.bShowGap;
		showCentromere = seq.bShowCentromere;
		showFullGene   = seq.bShowFullGene;
		showScoreLine  = seq.bShowScoreLine;	
		showHitLen 		= seq.bShowHitLen;	
		showScoreValue = seq.bShowScoreText;	
		showHitNum 		= seq.bShowHitNumText;	
		flipped        = seq.isFlipped();		
	}

	protected void setTrack(Track sequence) {
		super.setTrack(sequence);

		Sequence seq = (Sequence)sequence;
		seq.group          = group;
		seq.bShowRuler      = showRuler;
		seq.bShowGene       = showGene;
		seq.bShowGeneLine   = showGeneLine;
		seq.bShowAnnot      = showAnnot;
		seq.bShowGap        = showGap;
		seq.bShowCentromere = showCentromere;
		seq.bShowFullGene   = showFullGene;
		seq.bShowScoreLine  = showScoreLine; 
		seq.bShowHitLen 		= showHitLen;	
		seq.bShowScoreText = showScoreValue; 
		seq.bShowHitNumText = showHitNum; 
		seq.flipSeq(flipped); 						
	}

	protected int getGroup() {
		return group;
	}
}
