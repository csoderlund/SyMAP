package symap.closeup;

import java.awt.geom.Dimension2D;

/***************************************************
 * Used in CloseUp methods
 */
public class DoubleDimension extends Dimension2D {
    protected double width;
    protected double height;

    protected DoubleDimension() {
    	width = 0;
    	height = 0;
    }
    protected DoubleDimension(double width, double height) {
    	this.width = width;
    	this.height = height;
    }
    public void setSize(double width, double height) {
    	this.width = width;
    	this.height = height;
    }
    public double getWidth() {return width;}
    public double getHeight() {return height;}
}

