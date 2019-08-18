package symap.mapper;

import java.awt.event.MouseEvent;

/**
 * Interface <code>Hit</code> is the interface for holding a hit (both sides).
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public interface Hit {

    /**
     * Method <code>isFiltered</code> should return true if the hit
     * is currently filtered.
     *
     * @return a <code>boolean</code> value
     */
    public boolean isFiltered();

    public boolean isBlockHit();
   
    public boolean isVisible();
    
    //public boolean isShowing(Object obj);

    public String getType();

    public void clear();

    public void mouseMoved(MouseEvent e);
}
