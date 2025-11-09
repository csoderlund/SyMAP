package colordialog;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.util.Vector;

class ColorTab extends JPanel implements Comparable<ColorTab> {
	private static final long serialVersionUID = 1L;
	private Vector<ColorVariable> cvVec;
	private int nRows=3;
	private int order;

	protected ColorTab(String tabName,  int order) {
		super(new BorderLayout());
		
		setName(tabName);
		cvVec = new Vector<ColorVariable>();
		this.order = order;
	}
	
	public int compareTo(ColorTab t) {
		return order - t.order;
	}

	public void addVariable(ColorVariable cv) {
		cvVec.add(cv);
	}

	public void setup() {
		removeAll();

		int nCells = cvVec.size();
		int nCols = nCells / nRows;
		if (nCells % nRows != 0) nCols++;
		
		setLayout(new GridLayout(1, nRows));
		
		// this assumes the order# are correctly entered in colors.properties 1-size
		ColorVariable [] orderComps = new ColorVariable [nCells];
		for (int i=0; i<nCells; i++) {
			int j = cvVec.get(i).getOrder();
			orderComps[j-1] =  cvVec.get(i);
		}
		
		ColorVariable cv;
		JPanel opanel, panel, apanel;
		opanel = new JPanel();
		int i, j, k=0;
		
		// Adjacent columns are created with nCols each
		for (i = 0; i < nCols; i++) {
			panel = new JPanel(null);
			panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
			if (nCells-k==4) nRows=2;
			
			for (j = 0; j < nRows && k < nCells; j++, k++) {
				apanel = new JPanel(new BorderLayout());
				cv = orderComps[k];
			
				apanel.add(cv.button,BorderLayout.WEST);
				apanel.add(cv.label, BorderLayout.CENTER);
				panel.add(apanel);
			}
			opanel.add(panel);
		}
		add(opanel,BorderLayout.NORTH);
	}

	public void cancel() {
		for (ColorVariable cv : cvVec)
			cv.cancelColorChange();
	}

	public void commit() { // ok
		for (ColorVariable cv : cvVec)
			cv.commitColorChange();
	}

	public void setDefault() {
		for (ColorVariable cv : cvVec) 
			cv.setDefaultColor();
	
	}
	// ColorDialog.changeCookieColor
	public boolean changeColor(ColorVariable cv) {
		int ind = cvVec.indexOf(cv);
		if (ind < 0) return false;

		((ColorVariable)cvVec.get(ind)).commitColorChange(cv);
		return true;
	}
	// setCookie - find cv's whose color diffs from default
	public Vector<ColorVariable> getChangedVariables() {
		Vector<ColorVariable> v = new Vector<ColorVariable>();
		for (ColorVariable cv : cvVec)
			if (!cv.isDefault()) v.add(cv);
		return v;
	}

	public boolean equals(Object obj) {
		if (obj instanceof ColorTab) {
			return getName().equals(((ColorTab)obj).getName());
		}
		else if (obj instanceof String) {
			return getName().equals(obj);
		}
		return false;
	}
}
