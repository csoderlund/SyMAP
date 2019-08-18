package dotplot;

import java.awt.Shape;

/**
 * Interface <code>InnerBlock</code> for an inner block (synteny block).
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Shape
 */
public interface InnerBlock extends Shape {
    public String getName();
    public int getNumber();
    public Tile getParent();
    public Group getGroup(int axis);
    public int[] getContigNumbers(int axis);

    public int getStart(int axis);
    public int getEnd(int axis);

    public int getX();
    public int getY();
    public int getWidth();
    public int getHeight();

    public Hit[] getHits(boolean includeRepetitive, boolean onlyBlock);
}
