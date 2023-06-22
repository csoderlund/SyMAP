package number;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Class GenomicsNumber holds a number value.  That it can format 
 * based on the holder of the number.
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
		unitConversion.put(BP, 1L); // CAS512 new Long(1)
		unitConversion.put(KB, 1000L);
		unitConversion.put(MB, 1000000L);
		unitConversion.put(GB, 1000000000L);
	}

	private static final NumberFormat nf = NumberFormat.getNumberInstance();
	static {
		nf.setMaximumFractionDigits(DEFAULT_FRACTION_DIGITS);
	}

	protected long number;
	protected GenomicsNumberHolder parent;

	public GenomicsNumber(GenomicsNumberHolder parent, long number) { 
		this.parent = parent;
		this.number = number;
	}
	
	public void setValue(long num) {this.number = num;}
	public long getValue()   {return number;} 
	
	public long getBPValue() {return number * parent.getBpPerCb();}

	public void setBPValue(long bpNum) {this.number = bpNum / parent.getBpPerCb();}

	public double getPixelValue() {
		return (number * parent.getBpPerCb()) / parent.getBpPerPixel();
	}

	public double getValue(String units) {
		return getValue(units,parent.getBpPerCb(),number,parent.getBpPerPixel());
	}

	public boolean equals(Object obj) {
		return ( obj instanceof GenomicsNumber && ((GenomicsNumber)obj).number == number );
	}

	public String getFormatted() {
		return getFormatted(parent.getBpPerCb(),number,parent.getBpPerPixel());
	}

	public String toString() {
		return number +" "+CB; // new Long(number).toString()+" "+CB;
	}
	
	public String toString(int bpPerCb) { 
		return number +" "+CB+" = "+getFormatted(bpPerCb,number,1);
	}
	/*********************************************************************************/
	public static double getPixelValue(int bpPerCb, double cb, double bpPerPixel) {
		return (cb * bpPerCb) / bpPerPixel;
	}

	public static double getValue(String units, int bpPerCb, double cb, double bpPerPixel) {
		double r;
		double bp = cb * bpPerCb;
		if (units.equals(PIXEL))	r = bp / bpPerPixel;
		else if (units.equals(CB))	r = cb;
		else						r = bp / ((Long)unitConversion.get(units)).doubleValue();
		return r;
	}

	public static String getFormatted(String units, int bpPerCb, double cb, double bpPerPixel, NumberFormat numFormat) {
		return numFormat.format(getValue(units,bpPerCb,cb,bpPerPixel))+" "+units;
	}

	public static String getFormatted(int bpPerCb, double cb, double bpPerPixel) {
		return getFormatted(bpPerCb,cb,bpPerPixel,nf);
	}

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

	
	public static String getFormatted(double bpSep, int bpPerCb, double cb, double bpPerPixel) {
		return getFormatted(bpSep,bpPerCb,cb,bpPerPixel,nf);
	}

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

	public static double getBpPerPixel(int bpPerCb, long cb, double pixels) {
		return (bpPerCb * cb) / pixels;
	}

	public static double getCbPerPixel(int bpPerCb, double bpPerPixel, double pixel) {
		return (1.0 / bpPerCb) * bpPerPixel * pixel;
	}

	/**
	 * @param unit GenomicsNumber.BP, GenomicsNumber.KB,  GenomicsNumber.MB, GenomicsNumber.GB.
	 * @return the number of BP taken to equal one of unit or 0 if undefined
	 */
	public static long getUnitConversion(String unit) {
		Object obj = unitConversion.get(unit);
		if (obj == null)
			return 0;
		return ((Long) obj).longValue();
	}
}
