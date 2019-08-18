package symap.filter;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent; // mdb added 3/12/07 #104

import javax.swing.AbstractButton;
import javax.swing.JButton;

import symap.block.Block;
import symap.block.BlockFilter;
import symap.contig.Contig;
import symap.contig.ContigFilter;
import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpBar;
import symap.mapper.Mapper;
import symap.mapper.MapperFilter;
import symap.sequence.Sequence;
import symap.sequence.SequenceFilter;

public class FilterHandler implements Filtered, ActionListener {
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
// mdb removed 4/30/09 #162
//		if (SyMAP.isHelp()) {
//			helpButton = new JButton("Help");
//			SyMAP.enableHelpOnButton(helpButton,null);
//		}
		button.addActionListener(this);
	}

	public void set(Object obj) {
		if (filter != null) {
			filter.closeFilter();
		}
		filter = null;
		if (obj != null) {
			filter = createFilter(obj);
			//SyMAP.setHelpID(helpButton,filter.getHelpID()); // mdb removed 4/30/09 #162
			button.setText(filter.getTitle());
		}
	}

	public void show() {
		if (filter != null) {
			filter.show();
		}
	}

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
			if (filter != null && filter.canShow()) filter.show();
	}

	protected Filter createFilter(Object obj) {
		Filter filter = null;
		if      (obj instanceof Block)    filter = createBlockFilter((Block)obj);
		else if (obj instanceof Contig)   filter = createContigFilter((Contig)obj);
		else if (obj instanceof Sequence) filter = createSequenceFilter((Sequence)obj);
		else if (obj instanceof Mapper)   filter = createMapperFilter((Mapper)obj);
		if (filter != null && helpBar != null) filter.setHelpBar(helpBar);
		return filter;
	}

	protected Filter createBlockFilter(Block block) {
		return new BlockFilter(drawingPanel.getFrame(),drawingPanel,helpButton,block);
	}

	protected Filter createContigFilter(Contig contig) {
		return new ContigFilter(drawingPanel.getFrame(),drawingPanel,helpButton,contig);
	}

	protected Filter createSequenceFilter(Sequence sequence) {
		return new SequenceFilter(drawingPanel.getFrame(),drawingPanel,helpButton,sequence);
	}

	protected Filter createMapperFilter(Mapper mapper) {
		return new MapperFilter(drawingPanel.getFrame(),drawingPanel,helpButton,mapper);
	}
	
	public void showPopup(MouseEvent e) {
		filter.showPopup(e);
	}
}
