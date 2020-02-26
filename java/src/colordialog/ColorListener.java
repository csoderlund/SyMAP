package colordialog;

/**
 * Interface <code>ColorListener</code> can be used by the color dialog to nofify objects when a change occurs.
 */
public interface ColorListener {

    /**
     * Method <code>resetColors</code> is called when the colors have changed so the implementor may
     * redraw anything that may need redraw.
     */
    public void resetColors();

}
