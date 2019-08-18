package dotplot;

import java.util.List;
import java.util.Iterator;
import java.math.BigInteger;
import java.math.BigDecimal;

import util.Coordinate2D;

public abstract class Hit extends Coordinate2D 
						  implements DotPlotConstants, Comparable<Coordinate2D>, Cloneable 
{
	public Hit() {
		super();
	}

	public static double pearson_exact(List path, Hit[] hits) {
		int i;
		BigInteger x, y;
		BigInteger xx_sum = BigInteger.ZERO, x_sum = BigInteger.ZERO, yy_sum = BigInteger.ZERO, y_sum = BigInteger.ZERO, xy_sum = BigInteger.ZERO;
		Iterator iter;

		for (iter = path.iterator(); iter.hasNext();) {
			i = ((Integer) iter.next()).intValue();
			x = BigInteger.valueOf(hits[i].getX());
			y = BigInteger.valueOf(hits[i].getY());

			x_sum = x_sum.add(x);
			y_sum = y_sum.add(y);

			xx_sum = xx_sum.add(x.multiply(x));
			yy_sum = yy_sum.add(y.multiply(y));

			xy_sum = xy_sum.add(x.multiply(y));
		}

		return figurePearson(path.size(), x_sum, y_sum, xx_sum, yy_sum, xy_sum);
	}

	public static double pearson_exact(List hits) {
		Hit h;
		BigInteger x, y;
		BigInteger xx_sum = BigInteger.ZERO, x_sum = BigInteger.ZERO, yy_sum = BigInteger.ZERO, y_sum = BigInteger.ZERO, xy_sum = BigInteger.ZERO;
		Iterator iter;

		for (iter = hits.iterator(); iter.hasNext();) {
			h = (Hit) iter.next();
			x = BigInteger.valueOf(h.getX());
			y = BigInteger.valueOf(h.getY());

			x_sum = x_sum.add(x);
			y_sum = y_sum.add(y);

			xx_sum = xx_sum.add(x.multiply(x));
			yy_sum = yy_sum.add(y.multiply(y));

			xy_sum = xy_sum.add(x.multiply(y));
		}

		return figurePearson(hits.size(), x_sum, y_sum, xx_sum, yy_sum, xy_sum);
	}

	public static double pearson(List path, Hit[] hits) {
		int i;
		double x, y;
		double xx_sum = 0, x_sum = 0, yy_sum = 0, y_sum = 0, xy_sum = 0;
		Iterator iter;

		for (iter = path.iterator(); iter.hasNext();) {
			i = ((Integer) iter.next()).intValue();
			x = hits[i].getX();
			y = hits[i].getY();

			x_sum += x;
			y_sum += y;

			xx_sum += (x * x);
			yy_sum += (y * y);

			xy_sum += (x * y);
		}

		return figurePearson(path.size(), x_sum, y_sum, xx_sum, yy_sum, xy_sum);
	}

	public static double pearson(List hits) {
		Hit h;
		double x, y;
		double xx_sum = 0, x_sum = 0, yy_sum = 0, y_sum = 0, xy_sum = 0;
		Iterator iter;

		for (iter = hits.iterator(); iter.hasNext();) {
			h = (Hit) iter.next();
			x = h.getX();
			y = h.getY();

			x_sum += x;
			y_sum += y;

			xx_sum += (x * x);
			yy_sum += (y * y);

			xy_sum += (x * y);
		}

		return figurePearson(hits.size(), x_sum, y_sum, xx_sum, yy_sum, xy_sum);
	}

	protected static final double figurePearson(int num, BigInteger x_sum,
			BigInteger y_sum, BigInteger xx_sum, BigInteger yy_sum,
			BigInteger xy_sum) {
		double sd;
		BigDecimal xy_avg, dx, dy;
		BigDecimal n = BigDecimal.valueOf(num);

		if (num <= 2)
			return 0;

		xy_avg = new BigDecimal(x_sum.multiply(y_sum)).divide(n,
				BigDecimal.ROUND_DOWN);

		dx = new BigDecimal(xx_sum).subtract(new BigDecimal(x_sum
				.multiply(x_sum)).divide(n, BigDecimal.ROUND_DOWN));
		dy = new BigDecimal(yy_sum).subtract(new BigDecimal(y_sum
				.multiply(y_sum)).divide(n, BigDecimal.ROUND_DOWN));

		sd = Math.sqrt(dx.multiply(dy).doubleValue());

		return sd > 0 ? new BigDecimal(xy_sum).subtract(xy_avg).doubleValue()
				/ sd : 0;
	}

	protected static final double figurePearson(int num, double x_sum,
			double y_sum, double xx_sum, double yy_sum, double xy_sum) {
		double sd, xy_avg, dx, dy;
		double n = (double) num;

		if (num <= 2)
			return 0;

		xy_avg = (x_sum * y_sum) / n;

		dx = xx_sum - ((x_sum * x_sum) / n);
		dy = yy_sum - ((y_sum * y_sum) / n);

		sd = Math.sqrt(dx * dy);

		return sd > 0 ? (xy_sum - xy_avg) / sd : 0;
	}

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError();
		}
	}

	public String toString() {
		return "[Hit (" + getX() + "," + getY() + ") type = " + getType()
				+ " evalue = " + getEvalue() + " block = " + isBlock()
				+ " repetitive = " + isRepetitive() + "]";
	}

	public boolean isMrkHit() { return getType() == MRK; }
	public boolean isFpHit()  { return getType() == FP;  }
	public boolean isBesHit() { return getType() == BES; }
	public abstract double getEvalue();
	public abstract double getPctid();
	public abstract int getType();
	public abstract int getLength(); // mdb added 12/17/07 #149
	public abstract boolean isRepetitive();
	public abstract boolean isBlock();
	public abstract boolean isBlock(int altNum);
	public abstract void setAltBlock(int altNum, boolean block);
}
