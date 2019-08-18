package dotplot;

public class DPHit extends Hit implements Cloneable {
	private final int x, y;
	private final double evalue, pctid;
	private final int type;
	private final boolean repetitive;
	private final boolean block[] = new boolean[DotPlot.TOT_RUNS];
	private final int length; // mdb added 12/17/07 #149

	public DPHit(int posX, int posY, double evalue, double pctid, int type, 
			boolean repetitive,	boolean block, int length) // mdb added length 12/17/07 #149
	{
		x = posX;
		y = posY;
		this.evalue = evalue;
		this.pctid = pctid;
		this.type = type;
		this.repetitive = repetitive;
		this.block[0] = block;
		for (int i = 1; i < this.block.length; i++) this.block[i] = false;
		this.length = length; // mdb added 12/17/07 #149
	}

	public Object clone() { return super.clone(); }
	public int getCoord(int axis) { return axis == X ? x : y; }
	public int getX() { return x; } // added for performance
	public int getY() { return y; } // added for performance
	public double getEvalue() { return evalue; }
	public double getPctid() { return pctid; }
	public int getType() { return type; }
	public int getLength() { return length; } // mdb added 12/17/07 #149
	public boolean isRepetitive() { return repetitive; }
	public boolean isBlock() { return block[0]; }
	public boolean isBlock(int altNum) { return block[altNum]; }

	public void setAltBlock(int altNum, boolean block) {
		if (altNum != 0) /* can't change if it's a real block hit */
			this.block[altNum] = block;
	}
}
