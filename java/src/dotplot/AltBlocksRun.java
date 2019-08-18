package dotplot;

import java.util.Collection;

public class AltBlocksRun {

	public int rid;
	public ABlock[] ablocks;

	public AltBlocksRun(int rid, ABlock[] blocks) {
		this.rid = rid;
		this.ablocks = blocks == null ? new ABlock[0] : blocks;
	}

	public AltBlocksRun(int rid, Collection<ABlock> blocks) {
		this(rid,blocks == null ? new ABlock[0] : blocks.toArray(new ABlock[blocks.size()]));
	}

	public ABlock[] getABlocks() {
		return ablocks == null ? new ABlock[0] : ablocks;
	}

	public int getRID() {
		return rid;
	}

	public boolean equals(Object obj) {
		return obj instanceof AltBlocksRun && ((AltBlocksRun)obj).rid == rid;
	}

	public String toString() {
		return "AltBlocksRun "+rid;
	}
}
