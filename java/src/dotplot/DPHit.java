package dotplot;

/*****************************************************
 * Represents a hit for DotPlot; populated in DotPlotDBUser
 */
public class DPHit implements Cloneable {
	private int x, y;
	private double pctid;
	private boolean block; // CAS533 was an array; removed altblk stuff
	private int length; 

	public DPHit(int posX, int posY,  double pctid, boolean block, int length) {
		x = posX;
		y = posY;
		this.pctid = pctid;
		this.block = block;
		this.length = length; 
	}
	public void swap() { // CAS533 add to swap reference
		int t=x; x=y; y=t;
	}
	public int getX() {return x; } 
	public int getY() {return y; } 
	
	public double getPctid()   {return pctid; }
	public int getScalePctid(double min) {// CAS533 scale 1-5
		double x = (pctid-min)+1;
		return (int) (x/20.0); 
	} 
	
	public int getLength()  {return length; } 
	public boolean isBlock(){return block; }
}
