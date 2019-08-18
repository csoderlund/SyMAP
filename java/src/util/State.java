package util;

public interface State {
    public static final int NONE     = -1;
    public static final int ACTIVE   = 0;
    public static final int PAUSING  = 1;
    public static final int STOPPING = 2;
    public static final int PAUSED   = 3;
    public static final int STOPPED  = 4;
    public static final int DEAD     = 5;

    public int state();
}
