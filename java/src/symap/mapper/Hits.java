package symap.mapper;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import util.ClearList;

public interface Hits extends ClearList.Clearer {
    public void paintComponent(Graphics2D g2);
    public void setMinMax(HitFilter hf);
    public void mouseMoved(MouseEvent e);
    public void mouseExited(MouseEvent e); 
}
