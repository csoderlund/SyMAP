package symap.block;

import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.AbstractButton;

import symap.drawingpanel.DrawingPanel;
import symap.marker.MarkerFilter;
import symap.marker.MarkerTrack;

/**
 * The filter window for the block track
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class BlockFilter extends MarkerFilter {
	private JTextField contigSetText;

	private Block block;
	private String contigSet;

	//private boolean noChange = false;  // mdb removed 6/29/07 #118

	public BlockFilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Block block) {
		super(owner,dp,"Block Filter",helpButton,(MarkerTrack)block);
		this.block = block;

		Container contentPane = getContentPane();
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();

		JLabel contigSetLabel = new JLabel("Contig Set:");
		JLabel contigSetExampleLabel = new JLabel("(Ex. 4,5-20,37)");
		contigSetText = new JTextField("", 10);

		contentPane.setLayout(gridbag);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;

		addMarkerNameFilterToGrid(contentPane,gridbag,constraints);

		addToGrid(contentPane, gridbag, constraints, contigSetLabel, 1);
		addToGrid(contentPane, gridbag, constraints, contigSetText, 1);
		addToGrid(contentPane, gridbag, constraints, contigSetExampleLabel, GridBagConstraints.REMAINDER);

		addToGrid(contentPane, gridbag, constraints, getFlippedBox(), GridBagConstraints.REMAINDER);

		addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);

		constraints.anchor = GridBagConstraints.CENTER;
		addMarkerFilterToGrid(contentPane,gridbag,constraints);

		addToGrid(contentPane, gridbag, constraints, buttonPanel, GridBagConstraints.REMAINDER);

		pack();
		setResizable(false);
		
		//popupTitle.setLabel("Block Options:"); // mdb added 3/21/07 #104 // mdb removed 7/2/07 #118
		popupTitle.setText("Block Options:"); // mdb added 7/2/07 #118
		
		// mdb removed 4/30/09 #162
		//SyMAP.enableHelpOnButton(showNavigationHelp,"blockcontrols"); // mdb added 3/21/07 #104
		//SyMAP.enableHelpOnButton(showTrackHelp,"blocktrack"); // mdb added 3/28/07 #104
	}

	public String getHelpID() {
		return "blockfilter";//Filter.BLOCK_FILTER_ID;
	}

	protected void setDefault() {
		if (block != null) contigSetText.setText(block.getDefaultContigs());
		super.setDefault();
	}

	protected void didFlip() { }

	public void show() {
		if (block != null) {
			if (!isShowing()) {
				//noChange = true; // mdb removed 6/29/07 #118
				contigSetText.setText(block.contigSetText);
				contigSet = contigSetText.getText();
				//noChange = false; // mdb removed 6/29/07 #118
			}
			super.show();
		}
	}


	protected boolean okAction() throws Exception {
		if (block == null) return false;

		boolean changed = super.okAction();

		if (contigSet == null || !contigSet.equals(contigSetText.getText())) {
			contigSet = contigSetText.getText().trim();
			if (contigSet.length() == 0) {
				contigSetText.setText(block.getDefaultContigs());
				block.resetContigList();
			} 
			else {
				if (!Block.validContigSet(contigSet))
					throw new Exception("Contig set is not valid.");
				if (!block.isContigs(contigSet))
					throw new Exception("None of the contigs in the set exist.");
				block.setContigList(contigSet);
			}
		}

		return (changed || !block.hasBuilt());
	}

	protected void cancelAction() {
		contigSetText.setText(contigSet);
		super.cancelAction();
	}

	/**
	 * Method <code>canShow</code> returns true if the filter window can be shown (i.e. The Block has been initialized).
	 *
	 * @return a <code>boolean</code> value
	 */
	public boolean canShow() {
		if (block == null) return false;
		return block.hasInit();
	}
}

