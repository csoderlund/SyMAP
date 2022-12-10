package dotplot;

public class DPHit implements Cloneable {
	private int x, y;
	private double pctid;
	private boolean block[] = new boolean[DotPlot.TOT_RUNS];
	private int length; 

	public DPHit(int posX, int posY,  double pctid, boolean block, int length) 
	{
		x = posX;
		y = posY;
		this.pctid = pctid;
		this.block[0] = block;
		for (int i = 1; i < this.block.length; i++) this.block[i] = false;
		this.length = length; 
	}

	public int getX() { return x; } // added for performance
	public int getY() { return y; } // added for performance
	
	public double getPctid() { return pctid; }
	
	public int getLength() { return length; } 
	public boolean isBlock() { return block[0]; }
	public boolean isBlock(int altNum) { return block[altNum]; }

	public void setAltBlock(int altNum, boolean block) {
		if (altNum != 0) /* can't change if it's a real block hit */
			this.block[altNum] = block;
	}
}
