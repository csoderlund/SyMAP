package symap.sequence;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * CAS550 This was in a separate class called number, along with 2 other files.
 * It was moved to Sequence and all references to getBpPerCb() removed since it was always 1.
 * Then removed all BpNumber objects from Sequence, and this is what is left.
 * This originally was written for FPC CB units and Seq BP units.
 */
public class BpNumber {
	public static final int DEFAULT_FRACTION_DIGITS = 2;

	private static final String BP = "BP";
	protected static final String KB = "KB";
	private static final String MB = "MB";
	private static final String GB = "GB";
	
	protected static final String[] ABS_UNITS = { BP, KB, MB, GB };

	public static final String PIXEL = "pixel";

	protected static final Map<String,Long> unitConversion = new HashMap<String,Long>();
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

	protected static double getCbPerPixel(double bpPerPixel, double pixel) {return bpPerPixel * pixel;}
	
	protected static double getPixelValue(double cb, double bpPerPixel) {return cb / bpPerPixel;}

	protected static double getValue(String units, double bp, double bpPerPixel) {
		double r;
		if (units.equals(PIXEL))	r = bp / bpPerPixel;
		else						r = bp / ((Long)unitConversion.get(units)).doubleValue();
		return r;
	}

	protected static String getFormatted(String units, double bp, double bpPerPixel, NumberFormat numFormat) {
		return numFormat.format(getValue(units, bp,bpPerPixel))+" "+units;
	}

	protected static String getFormatted(double bp, double bpPerPixel) {
		return getFormatted(bp,bpPerPixel,nf);
	}

	protected static String getFormatted(double bp0, double bpPerPixel, NumberFormat numFormat) {
		String unit = GB;
		double bp = getValue(BP, bp0,bpPerPixel);
		long mult = ((Long) unitConversion.get(unit)).longValue();
		if (bp < mult) {
			unit = MB;
			mult = ((Long) unitConversion.get(unit)).longValue();
			if (bp < mult) {
				unit = KB;
				mult = ((Long) unitConversion.get(unit)).longValue();
				if (bp < mult) unit = BP;
			}
		}
		return getFormatted(unit, bp0,bpPerPixel,numFormat);
	}

	
	protected static String getFormatted(double bpSep, double cb, double bpPerPixel) {
		return getFormatted(bpSep, cb,bpPerPixel,nf);
	}

	protected static String getFormatted(double bpSep, double cb, double bpPerPixel, NumberFormat numFormat) {
		String unit = GB;
		double bp = getValue(BP, cb,bpPerPixel);
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
		return getFormatted(unit, cb,bpPerPixel,numFormat);
	}

	protected static double getBpPerPixel(long cb, double pixels) {return cb / pixels;}
	protected static long getUnitConversion(String unit) {
		Object obj = unitConversion.get(unit);
		if (obj == null) return 0;
		return ((Long) obj).longValue();
	}
}
