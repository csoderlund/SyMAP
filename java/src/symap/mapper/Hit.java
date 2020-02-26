package symap.mapper;

import java.awt.event.MouseEvent;

/**
 * Interface <code>Hit</code> is the interface for holding a hit (both sides).
 *
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

    public String getType();

    public void clear();

    public void mouseMoved(MouseEvent e);
}
