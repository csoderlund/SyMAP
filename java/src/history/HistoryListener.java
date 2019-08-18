package history;

/**
 * Interface <code>HistoryListener</code> is used by the HistoryControl as the object
 * to update when the history needs to be changed.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public interface HistoryListener {
    
    /**
     * Method <code>setHistory</code> should be implemented to change the history based on
     * obj.
     *
     * @param obj an <code>Object</code> value
     */
    public void setHistory(Object obj);
}
