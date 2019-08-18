package symap.closeup;

import colordialog.ColorListener;

interface HitDialogInterface extends ColorListener {
    public int getNumberOfHits();
    public int showIfHits();
}
