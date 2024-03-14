package symap.drawingpanel;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent; 
import javax.swing.JButton;

import symap.mapper.Mapper;
import symap.mapper.Hfilter;
import symap.sequence.Sequence;
import symap.sequence.Sfilter;

/********************************************************************
 * Used to open Sfilter and Hfilter
 * CAS542 removed the abstract Filter.java and moved this to symap.frame; CAS544 moved to drawingpanel
 */
public class FilterHandler implements ActionListener { // CAS521 remove Filtered interface
	protected DrawingPanel drawingPanel;
	protected Hfilter hFilter=null;
	protected Sfilter sFilter=null;
	protected JButton button;

	public FilterHandler(DrawingPanel dp) {
		button = new JButton();
		button.setMargin(new Insets(2,4,2,4));
		this.drawingPanel = dp;
		
		button.addActionListener(this);
	}
	public void setHfilter(Mapper mapperObj) { // called in Mapper
		if (hFilter != null) hFilter.closeFilter();
		hFilter = null;
		if (mapperObj != null) {
			hFilter = new Hfilter(drawingPanel.getFrame(),drawingPanel, mapperObj);
			button.setText(hFilter.getTitle());
		}
	}
	public void setSfilter(Object seqObj) { // the object is a sequence Track
		if (sFilter != null) sFilter.closeFilter();
		sFilter = null;
		if (seqObj != null) {
			sFilter = new Sfilter(drawingPanel.getFrame(),drawingPanel, (Sequence) seqObj);
			button.setText("Sequence Filter"); // CAS551 sFilter.getTitle() - added #N, which do not need here
		}
	}
	public void hide() {
		if (hFilter != null) hFilter.closeFilter();
		else if (sFilter != null) sFilter.closeFilter();
	}
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == button) {
			if (hFilter != null && hFilter.canShow()) hFilter.showX(); // CAS512 show()
			else if (sFilter != null && sFilter.canShow()) sFilter.showX();
		}
	}
	public void showPopup(MouseEvent e) {
		if (hFilter!=null) hFilter.showPopup(e);
		else if (sFilter!=null) sFilter.showPopup(e);
	}
	public void closeFilter() {hide();}
	public JButton getFilterButton() {return button;}
}
