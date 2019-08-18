package util;

import java.awt.geom.Dimension2D;

public class DoubleDimension extends Dimension2D {
    public double width;
    public double height;

    public DoubleDimension() {
	width = 0;
	height = 0;
    }

    public DoubleDimension(double width, double height) {
	this.width = width;
	this.height = height;
    }

    public void setSize(double width, double height) {
	this.width = width;
	this.height = height;
    }

    public double getWidth() {
	return width;
    }

    public double getHeight() {
	return height;
    }
}
