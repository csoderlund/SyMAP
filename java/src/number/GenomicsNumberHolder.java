package number;

/**
 * Interface <code>GenomicsNumberHolder</code> should be an object that contains a genomics number
 * and has changing bp/cb and/or bp/pixel.
 *
 * A GenomicsNumberHolder is used by a GenomicsNumber to return the proper values from it's methods
 * based of the return values of these methods.
 *
 * @see GenomicsNumber
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public interface GenomicsNumberHolder {

    /**
     * Method <code>getBpPerCb</code> should return the current value of the bp/cb of this
     * holder.
     *
     * @return an <code>int</code> value
     */
    public int getBpPerCb();


    /**
     * Method <code>getBpPerPixel</code> should return the current value of the bp/pixel of this holder.
     *
     * @return a <code>double</code> value
     */
    public double getBpPerPixel();
}
