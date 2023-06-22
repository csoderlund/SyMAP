package number;

/**
 * Interface GenomicsNumberHolder should be an object that contains a genomics number
 * and has changing bp/cb and/or bp/pixel.
 * Track implements GenomicsNumberHolder
 */
public interface GenomicsNumberHolder {

    public int getBpPerCb();

    public double getBpPerPixel();
}
