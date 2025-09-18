package dotplot;

/*****************************************************
 * Represents a hit for DotPlot; populated in Data.DBload
 */
public class DPHit { // CAS573 remove Cloneable
	private int x, y;
	private double pctid;
	private boolean block;
	private int length, geneOlap; 

	protected DPHit(int posX, int posY,  double pctid, int geneOlap, boolean block, int length) {
		x = posX;
		y = posY;
		this.pctid = pctid;
		this.geneOlap = geneOlap;
		this.block = block;
		this.length = length; 
	}
	protected void swap() { // swap reference
		int t=x; x=y; y=t;
	}
	protected int getX() {return x; } 
	protected int getY() {return y; } 
	
	protected double getPctid()   {return pctid; }
	protected int getScalePctid(double min) {// scale 1-5
		double x = (pctid-min)+1;
		return (int) (x/20.0); 
	} 
	protected boolean bothGene() {return geneOlap==2;}
	protected boolean oneGene() {return geneOlap==1;}
	protected boolean noGene() {return geneOlap==0;}
	protected int getLength()  {return length; } 
	protected boolean isBlock(){return block; }
}
