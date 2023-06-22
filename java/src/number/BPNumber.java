package number;

/**
 * Class BPNumber should be used for efficiency for GenomicsNumber's in which a CB value is not applicable.
 */
public class BPNumber extends GenomicsNumber {

	public BPNumber(GenomicsNumberHolder parent, long number) {
		super(parent,number);
	}

	public double getValue(String units) {
		return GenomicsNumber.getValue(units,1,number,parent.getBpPerPixel());
	}

	public double getPixelValue() {
		return number / parent.getBpPerPixel();
	}

	public void setBPValue(long bpNum) {
		this.number = bpNum;
	}

	public long getBPValue() {
		return number;
	}

	public String getFormatted() {
		return GenomicsNumber.getFormatted(1,number,parent.getBpPerPixel());
	}
}
