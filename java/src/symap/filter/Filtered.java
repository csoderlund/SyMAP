package symap.filter;

import javax.swing.AbstractButton;

/**
 * Interface for objects holding the filter button
 */
public interface Filtered {
    
    /**
     * @return the button to use to access the filter dialog
     */
    public AbstractButton getFilterButton();
    
    public void closeFilter();
}
