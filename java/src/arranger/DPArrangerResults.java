package arranger;

import dotplot.*;

/**
 * Class <code>DPArrangerResults</code> will set the sub-chain run for the block when results are added.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class DPArrangerResults extends ArrangerResults {

    private Tile block;

    public DPArrangerResults(Tile block, ABlock ib) {
	super(ib);
	this.block = block;
    }

    public DPArrangerResults(Tile block, ABlock[] ibs) {
	super(ibs);
	this.block = block;
    }

    public DPArrangerResults(Tile block, int altRun) {
	super(block,altRun);
	this.block = block;
    }

    public void addResults(SubBlock[] sblocks) {
	super.addResults(sblocks);
	block.setAltBlocksRun(DotPlot.SUB_CHAIN_RUN,new AltBlocksRun(0,(ABlock[])sblocks));
    }
}
