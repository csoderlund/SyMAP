package number;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Class <code>GenomicsNumber</code> holds a number value.  That it can format 
 * based on the holder of the number.
 *
 * @see GenomicsNumberHolder
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class GenomicsNumber {
	public static final int DEFAULT_FRACTION_DIGITS = 2;

	public static final String CB = "CB";
	public static final String BP = "BP";
	public static final String KB = "KB";
	public static final String MB = "MB";
	public static final String GB = "GB";

	public static final String[] UNITS = { CB, BP, KB, MB, GB };

	public static final String[] ABS_UNITS = { BP, KB, MB, GB };

	public static final String PIXEL = "pixel";

	private static final Map<String,Long> unitConversion = new HashMap<String,Long>();
	static {
		unitConversion.put(BP, new Long(1));
		unitConversion.put(KB, new Long(1000));
		unitConversion.put(MB, new Long(1000000));
		unitConversion.put(GB, new Long(1000000000));
	}

	private static final NumberFormat nf = NumberFormat.getNumberInstance();
	static {
		nf.setMaximumFractionDigits(DEFAULT_FRACTION_DIGITS);
	}

	protected long number;
	protected GenomicsNumberHolder parent;

	/**
	 * Creates a new <code>GenomicsNumber</code> instance.
	 *
	 * @param parent a <code>GenomicsNumberHolder</code> value of the parent
	 * @param number a <code>long</code> value of the cb value (or bp value if not applicable)
	 */
	public GenomicsNumber(GenomicsNumberHolder parent, long number) { 
		this.parent = parent;
		this.number = number;
	}

	/**
	 * Creates a new <code>GenomicsNumber</code> instance with the same parent and value as src.
	 *
	 * @param src a <code>GenomicsNumber</code> value
	 */
	public GenomicsNumber(GenomicsNumber src) {
		this.parent = src.parent;
		this.number = src.number;
	}

	/**
	 * Method <code>setValue</code> sets the value of the number
	 *
	 * @param num a <code>long</code> value
	 */
	public void setValue(long num) {
		this.number = num;
	}

	/**
	 * Method <code>getValue</code> gets the cb value
	 *
	 * @return a <code>long</code> value
	 */
	public long getValue() {
		return number;
	}

	/**
	 * Method <code>getBPValue</code> gets the base pair value
	 *
	 * @return a <code>long</code> value
	 */
	public long getBPValue() {
		return number * parent.getBpPerCb();
	}

	/**
	 * Method <code>setBPValue</code> sets the value based on the bp value and the bp per cb value of the parent
	 *
	 * @param bpNum a <code>long</code> value
	 */
	public void setBPValue(long bpNum) {
		this.number = bpNum / parent.getBpPerCb();
	}

	/**
	 * Method <code>getPixelValue</code> returns the value in pixels
	 *
	 * @return a <code>double</code> value
	 */
	public double getPixelValue() {
		return (number * parent.getBpPerCb()) / parent.getBpPerPixel();
	}

	/**
	 * Method <code>getValue</code> gets the value for this instance in units.
	 *
	 * @param units a <code>String</code> value
	 * @return a <code>double</code> value
	 */
	public double getValue(String units) {
		return getValue(units,parent.getBpPerCb(),number,parent.getBpPerPixel());
	}

	public boolean equals(Object obj) {
		return ( obj instanceof GenomicsNumber && ((GenomicsNumber)obj).number == number );
	}

	/**
	 * Method <code>getFormatted</code> returns the GenomicsNumber formatted.
	 *
	 * @return a <code>String</code> value
	 */
	public String getFormatted() {
		return getFormatted(parent.getBpPerCb(),number,parent.getBpPerPixel());
	}

	/**
	 * Method <code>toString</code> returns the number+" "+CB
	 *
	 * @return a <code>String</code> value
	 */
	public String toString() {
		return new Long(number).toString()+" "+CB;
	}
	
	// mdb added 8/9/07 #139
	public String toString(int bpPerCb) { 
		return new Long(number).toString()+" "+CB+" = "+getFormatted(bpPerCb,number,1);
	}

	/**
	 * Method <code>getPixelValue</code> gets the pixel value
	 *
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @return a <code>double</code> value
	 */
	public static double getPixelValue(int bpPerCb, double cb, double bpPerPixel) {
		return (cb * bpPerCb) / bpPerPixel;
	}

	/**
	 * Method <code>getValue</code> returns the value of cb in units <code>units</code> given the other arguments
	 *
	 * @param units a <code>String</code> value
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @return a <code>double</code> value
	 */
	public static double getValue(String units, int bpPerCb, double cb, double bpPerPixel) {
		double r;
		double bp = cb * bpPerCb;
		if (units.equals(PIXEL))
			r = bp / bpPerPixel;
		else if (units.equals(CB))
			r = cb;
		else
			r = bp / ((Long)unitConversion.get(units)).doubleValue();
		return r;
	}

	/**
	 * Method <code>getFormatted</code> gets the formatted string in units using the default number format.
	 *
	 * @param units a <code>String</code> value
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @return a <code>String</code> value
	 */
// mdb unused 8/10/07
//	public static String getFormatted(String units, int bpPerCb, double cb, double bpPerPixel) {
//		return getFormatted(units,bpPerCb,cb,bpPerPixel,nf);
//	}

	/**
	 * Method <code>getFormatted</code> gets the formatted string in units
	 *
	 * @param units a <code>String</code> value
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @param numFormat a <code>NumberFormat</code> value
	 * @return a <code>String</code> value
	 */
	public static String getFormatted(String units, int bpPerCb, double cb, double bpPerPixel, NumberFormat numFormat) {
		return numFormat.format(getValue(units,bpPerCb,cb,bpPerPixel))+" "+units;
	}

	/**
	 * Method <code>getFormatted</code> gets the formatted string using the default number format
	 *
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @return a <code>String</code> value
	 */
	public static String getFormatted(int bpPerCb, double cb, double bpPerPixel) {
		return getFormatted(bpPerCb,cb,bpPerPixel,nf);
	}

	/**
	 * Method <code>getFormatted</code> gets the formatted string
	 *
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @param numFormat a <code>NumberFormat</code> value
	 * @return a <code>String</code> value
	 */
	public static String getFormatted(int bpPerCb, double cb, double bpPerPixel, NumberFormat numFormat) {
		String unit = GB;
		double bp = getValue(BP,bpPerCb,cb,bpPerPixel);
		long mult = ((Long) unitConversion.get(unit)).longValue();
		if (bp < mult) {
			unit = MB;
			mult = ((Long) unitConversion.get(unit)).longValue();
			if (bp < mult) {
				unit = KB;
				mult = ((Long) unitConversion.get(unit)).longValue();
				if (bp < mult) {
					unit = BP;
				}
			}
		}
		return getFormatted(unit,bpPerCb,cb,bpPerPixel,numFormat);
	}

	/**
	 * Method <code>getFormatted</code> returns a formatted string
	 * insuring that the given formated string will be a different value
	 * than for a GenomicsNumber with bpSep greater value.
	 *
	 * @param bpSep a <code>double</code> value
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @return a <code>String</code> value
	 */
	public static String getFormatted(double bpSep, int bpPerCb, double cb, double bpPerPixel) {
		return getFormatted(bpSep,bpPerCb,cb,bpPerPixel,nf);
	}

	/**
	 * Method <code>getFormatted</code> returns a formatted string
	 * insuring that the given formated string will be a different value
	 * than for a GenomicsNumber with bpSep greater value.
	 *
	 * @param bpSep a <code>double</code> value
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>double</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @param numFormat a <code>NumberFormat</code> value
	 * @return a <code>String</code> value
	 */
	public static String getFormatted(double bpSep, int bpPerCb, double cb, double bpPerPixel, NumberFormat numFormat) {
		String unit = GB;
		double bp = getValue(BP,bpPerCb,cb,bpPerPixel);
		long mult = ((Long) unitConversion.get(unit)).longValue();
		if (bp < mult || bpSep < mult/100.0) {
			unit = MB;
			mult = ((Long) unitConversion.get(unit)).longValue();
			if (bp < mult || bpSep < mult/100.0) {
				unit = KB;
				mult = ((Long) unitConversion.get(unit)).longValue();
				if (bp < mult || bpSep < mult/100.0) {
					unit = BP;
				}
			}
		}
		return getFormatted(unit,bpPerCb,cb,bpPerPixel,numFormat);
	}


	/**
	 * Method <code>getBpPerPixel</code> returns the base pair per pixel as determined by
	 * the given values.
	 *
	 * @param bpPerCb an <code>int</code> value
	 * @param cb a <code>long</code> value
	 * @param pixels a <code>double</code> value
	 * @return a <code>double</code> value
	 */
	public static double getBpPerPixel(int bpPerCb, long cb, double pixels) {
		return (bpPerCb * cb) / pixels;
	}

	/**
	 * Method <code>getCbPerPixel</code>
	 *
	 * @param bpPerCb an <code>int</code> value
	 * @param bpPerPixel a <code>double</code> value
	 * @param pixel a <code>double</code> value
	 * @return a <code>double</code> value
	 */
	public static double getCbPerPixel(int bpPerCb, double bpPerPixel, double pixel) {
		return (1.0 / bpPerCb) * bpPerPixel * pixel;
	}

	/**
	 * 
	 * @param unit <code>GenomicsNumber.BP</code>, <code>GenomicsNumber.KB</code>, 
	 *             <code>GenomicsNumber.MB</code>, <code>GenomicsNumber.GB</code>.
	 * @return the number of BP taken to equal one of <code>unit</code> or 0 if undefined
	 */
	public static long getUnitConversion(String unit) {
		Object obj = unitConversion.get(unit);
		if (obj == null)
			return 0;
		return ((Long) obj).longValue();
	}
}
