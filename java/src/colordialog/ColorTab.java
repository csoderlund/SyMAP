package colordialog;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.util.Vector;
import java.util.Iterator;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
class ColorTab extends JPanel implements Comparable<ColorTab> {
	private Vector<ColorVariable> comps;
	private final int COLOR_COLUMNS;
	private int order;

	protected ColorTab(String tabName, int columns, int order) {
		super(new BorderLayout());
		COLOR_COLUMNS = columns;
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

		int size = comps.size();
		int height = size / COLOR_COLUMNS;
		if (size % COLOR_COLUMNS != 0) height++;
		setLayout(new GridLayout(1,COLOR_COLUMNS));

		ColorVariable cv;
		JPanel opanel, panel, apanel;
		Iterator<ColorVariable> iter = comps.iterator();
		int i, j;
		opanel = new JPanel();
		for (i = 0; i < COLOR_COLUMNS; i++) {
			panel = new JPanel(null);
			panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
			for (j = 0; j < height && iter.hasNext(); j++) {
				apanel = new JPanel(new BorderLayout());
				cv = iter.next();
				apanel.add(cv.button,BorderLayout.WEST);
				apanel.add(cv.label,BorderLayout.CENTER);
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
