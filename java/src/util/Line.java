package util;

public class Line {
    public int x1, x2, y1, y2;

    public Line() { }

    public Line(int x1, int y1, int x2, int y2) {
	setLine(x1,y1,x2,y2);
    }

    public void setLine(int x1, int y1, int x2, int y2) {
	this.x1 = x1;
	this.y1 = y1;
	this.x2 = x2;
	this.y2 = y2;
    }

    public double getX1() {
	return x1;
    }

    public double getY1() {
	return y1;
    }

    public double getX2() {
	return x2;
    }

    public double getY2() {
	return y2;
    }

    public void setX1(double v) {
	x1 = (int)Math.round(v);
    }

    public void setY1(double v) {
	y1 = (int)Math.round(v);
    }

    public void setX2(double v) {
	x2 = (int)Math.round(v);
    }

    public void setY2(double v) {
	y2 = (int)Math.round(v);
    }
}
