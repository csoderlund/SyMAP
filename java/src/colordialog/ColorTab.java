package colordialog;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.util.Vector;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
class ColorTab extends JPanel implements Comparable<ColorTab> {
	private Vector<ColorVariable> comps;
	private int nRows=3;
	private int order;

	protected ColorTab(String tabName,  int order) {
		super(new BorderLayout());
		
		setName(tabName);
		comps = new Vector<ColorVariable>();
		this.order = order;
	}
	
	public int compareTo(ColorTab t) {
		return order - t.order;
	}

	public void addVariable(ColorVariable cv) {
		comps.add(cv);
	}

	public void setup() {
		removeAll();

		int nCells = comps.size();
		int nCols = nCells / nRows;
		if (nCells % nRows != 0) nCols++;
		
		setLayout(new GridLayout(1, nRows));
		
		// CAS520 add order - this assumes the order# are correctly entered in colors.properties 1-size
		ColorVariable [] orderComps = new ColorVariable [nCells];
		for (int i=0; i<nCells; i++) {
			int j = comps.get(i).getOrder();
			orderComps[j-1] =  comps.get(i);
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
		for (ColorVariable cv : comps)
			cv.cancelColorChange();
	}

	public void commit() {
		for (ColorVariable cv : comps)
			cv.commitColorChange();
	}

	public boolean changeColor(ColorVariable cv) {
		int ind = comps.indexOf(cv);
		if (ind < 0) return false;
		((ColorVariable)comps.get(ind)).commitColorChange(cv);
		return true;
	}

	public Vector<ColorVariable> getChangedVariables() {
		Vector<ColorVariable> v = new Vector<ColorVariable>();
		for (ColorVariable cv : comps)
			if (!cv.isDefault()) v.add(cv);
		return v;
	}

	public void setDefault() {
		for (ColorVariable cv : comps)
			cv.setDefaultColor();
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
