package symap.drawingpanel;

import java.awt.Frame;

/**
 * Inteface <code>DrawingPanelListener</code> can be used to receive
 * communication from a DrawingPanel instance.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see DrawingPanel#setListener(DrawingPanelListener)
 */
public interface DrawingPanelListener {

    /**
     * Method <code>displayWarning</code> is called by the DrawingPanel
     * when there is a warning message that should be displayed.
     *
     * @param message a <code>String</code> value
     */
    public void displayWarning(String message);

    /**
     * Method <code>displayError</code> is called by the DrawingPanel
     * when there is an error message that should be displayed.
     *
     * @param message a <code>String</code> value
     */
    public void displayError(String message);

    /**
     * Method <code>setFrameEnabled</code> is called by the drawing panel
     * when it's in a disabled or transition state (i.e. it's making the tracks).
     *
     * @param enabled a <code>boolean</code> value
     */
    public void setFrameEnabled(boolean enabled);

    /**
     * Method <code>getFrame</code> should return the owning frame if applicable.
     *
     * @return a <code>Frame</code> value
     */
    public Frame getFrame();
}
