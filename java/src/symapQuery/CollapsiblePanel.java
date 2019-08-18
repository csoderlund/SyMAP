package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

public class CollapsiblePanel extends JPanel {
	private static final long serialVersionUID = 7114124162647988756L;

	public CollapsiblePanel(String title, String description) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Color.WHITE);
		showIcon = createImageIcon("/images/plus.gif");
		hideIcon = createImageIcon("/images/minus.gif");
		
		showHideButton = new JButton(title);
		showHideButton.setBorderPainted(false);
		showHideButton.setFocusPainted(false);
		showHideButton.setContentAreaFilled(false);
		showHideButton.setMargin(new Insets(5, 0, 5, 0));
		showHideButton.setAlignmentX(LEFT_ALIGNMENT);
		showHideButton.addActionListener(
			new ActionListener( ) {
				public void actionPerformed(ActionEvent e) {
					if (!visible) expand();
					else collapse();
		}});
		
		if (!description.equals("")) {
			labelDescription = new JLabel("     " + description);
			labelDescription.setFont(new Font(labelDescription.getFont().getName(), Font.PLAIN, labelDescription.getFont().getSize()));
			labelDescription.setAlignmentX(LEFT_ALIGNMENT);
			labelDescription.setBackground(Color.WHITE);
		}

		thePanel = new JPanel();
		thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.Y_AXIS));
		thePanel.setBorder ( BorderFactory.createEmptyBorder(5, 20, 10, 20) );
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		thePanel.setBackground(Color.WHITE);
		thePanel.add(new JSeparator());
		super.add( showHideButton );
		if (labelDescription != null) super.add( labelDescription );
		super.add( thePanel );

		theSizeExpanded = getPreferredSize();
		collapse();
		theSizeCollapsed = getPreferredSize();

		setAlignmentX(LEFT_ALIGNMENT);
	}
		
	public void collapse() {
		visible = false;
		showHideButton.setIcon(showIcon);
		showHideButton.setToolTipText("Expand");
		
		setMaximumSize(theSizeCollapsed);
		thePanel.setVisible(false);
	}
	
	public void expand() {
		visible = true;
		showHideButton.setIcon(hideIcon);
		showHideButton.setToolTipText("Collapse");
		
		setMaximumSize(theSizeExpanded);
		thePanel.setVisible(true);
	}
	
	public Component add(Component comp) {
		thePanel.add(comp);
		if(!visible) {
			expand();
			theSizeExpanded = getPreferredSize();
			collapse();
		}
		else {
			theSizeExpanded = getPreferredSize();
			expand();
		}
		
		return comp;
	}

	private static ImageIcon createImageIcon(String path) {
	    java.net.URL imgURL = CollapsiblePanel.class.getResource(path);
	    if (imgURL != null)
	    	return new ImageIcon(imgURL);
	    else
	    	return null;
	}

	private boolean visible = false;
	private ImageIcon showIcon = null, hideIcon = null;
	private JButton showHideButton = null;
	private JLabel labelDescription = null;
	private JPanel thePanel = null;
	private Dimension theSizeExpanded = null, theSizeCollapsed = null;
}
