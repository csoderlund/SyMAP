package number;

/**
 * Class <code>BPNumber</code> should be used for efficiency for GenomicsNumber's in which a CB value is not applicable.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see GenomicsNumber
 */
public class BPNumber extends GenomicsNumber {

	/**
	 * Creates a new <code>BPNumber</code> instance.
	 *
	 * @param parent a <code>GenomicsNumberHolder</code> value
	 * @param number a <code>long</code> value
	 */
	public BPNumber(GenomicsNumberHolder parent, long number) {
		super(parent,number);
	}

	/**
	 * Method <code>getValue</code> gets the value for this instance in units.
	 *
	 * @param units a <code>String</code> value
	 * @return a <code>double</code> value
	 */
	public double getValue(String units) {
		return GenomicsNumber.getValue(units,1,number,parent.getBpPerPixel());
	}

	/**
	 * Method <code>getPixelValue</code> returns the value in pixels
	 *
	 * @return a <code>double</code> value
	 */
	public double getPixelValue() {
		return number / parent.getBpPerPixel();
	}

	/**
	 * Method <code>setBPValue</code> sets the value based on the bp value
	 *
	 * @param bpNum a <code>long</code> value
	 */
	public void setBPValue(long bpNum) {
		this.number = bpNum;
	}

	/**
	 * Method <code>getBPValue</code> gets the base pair value
	 *
	 * @return a <code>long</code> value
	 */
	public long getBPValue() {
		return number;
	}

	/**
	 * Method <code>getFormatted</code> returns the BPNumber formatted
	 *
	 * @return a <code>String</code> value
	 */
	public String getFormatted() {
		return GenomicsNumber.getFormatted(1,number,parent.getBpPerPixel());
	}
}
