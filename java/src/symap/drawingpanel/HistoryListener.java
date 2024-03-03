package symap.drawingpanel;

/**
 * Interface <code>HistoryListener</code> is used by the HistoryControl as the object
 * to update when the history needs to be changed.
 *
 */
public interface HistoryListener {
    
    /**
     * Method <code>setHistory</code> should be implemented to change the history based on obj.
     */
    public void setHistory(Object obj);
}
