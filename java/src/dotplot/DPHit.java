package dotplot;

/*****************************************************
 * Represents a hit for DotPlot; populated in Data.DBload
 */
public class DPHit { 
	private int x, y;
	private double pctid;
	private boolean isBlock;
	private int length, geneOlap; 

	protected DPHit(int posX, int posY,  double pctid, int geneOlap, boolean isBlock, int length) {
		x = posX;
		y = posY;
		this.pctid = pctid;  // pctid of 0 is isSelf diagonal
		this.geneOlap = geneOlap;
		this.isBlock = isBlock;
		this.length = length; 
	}
	protected void swap() { // swap reference
		int t=x; x=y; y=t;
	}
	protected int getX() {return x; } 
	protected int getY() {return y; } 
	
	protected double getPctid()   {return pctid; }
	protected int getScalePctid(double min) {// scale 1-5
		if (pctid==Data.DIAG_HIT) return 1;
		double x = (pctid-min)+1;
		return (int) (x/20.0); 
	} 
	protected boolean bothGene() {return geneOlap==2;}
	protected boolean oneGene() {return geneOlap==1;}
	protected boolean noGene() {return geneOlap==0;}
	protected int getLength()  {return length; } 
	protected boolean isBlock(){return isBlock; }
	protected boolean isDiagHit() {return pctid==Data.DIAG_HIT;} // for diagonal
}
