package symap.filter;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent; 

import javax.swing.AbstractButton;
import javax.swing.JButton;

import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpBar;
import symap.mapper.Mapper;
import symap.mapper.Hfilter;
import symap.sequence.Sequence;
import symap.sequence.Sfilter;

public class FilterHandler implements ActionListener { // CAS521 remove Filtered interface
	protected JButton helpButton;
	protected DrawingPanel drawingPanel;
	protected HelpBar helpBar;
	protected Filter filter;
	protected JButton button;

	public FilterHandler(DrawingPanel dp, HelpBar bar) {
		button = new JButton();
		button.setMargin(new Insets(2,4,2,4));
		this.drawingPanel = dp;
		this.helpBar = bar;

		button.addActionListener(this);
	}

	public void set(Object obj) {
		if (filter != null) {
			filter.closeFilter();
		}
		filter = null;
		if (obj != null) {
			filter = createFilter(obj);
			button.setText(filter.getTitle());
		}
	}

	/** CAS512 not called
	public void show() {
		if (filter != null) {
			filter.showX(); 
		}
	}
	**/
	public void hide() {
		if (filter != null) {
			filter.closeFilter();
		}
	}

	public void closeFilter() {
		hide();
	}

	public AbstractButton getFilterButton() {
		return button;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == button)
			if (filter != null && filter.canShow()) filter.showX(); // CAS512 show()
	}

	protected Filter createFilter(Object obj) {
		Filter filter = null;
		if (obj instanceof Sequence) filter = createSequenceFilter((Sequence)obj);
		else if (obj instanceof Mapper)   filter = createMapperFilter((Mapper)obj);
		if (filter != null && helpBar != null) filter.setHelpBar(helpBar);
		return filter;
	}

	protected Filter createSequenceFilter(Sequence sequence) {
		return new Sfilter(drawingPanel.getFrame(),drawingPanel,helpButton,sequence);
	}

	protected Filter createMapperFilter(Mapper mapper) {
		return new Hfilter(drawingPanel.getFrame(),drawingPanel,helpButton,mapper);
	}
	
	public void showPopup(MouseEvent e) {
		filter.showPopup(e);
	}
}
