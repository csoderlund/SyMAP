package symapQuery;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class GlobalQueryPanel extends JPanel {
	private static final long serialVersionUID = 5785170488400209684L;

	public GlobalQueryPanel() {
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		add(new JLabel("Global Search"));
	}
}
