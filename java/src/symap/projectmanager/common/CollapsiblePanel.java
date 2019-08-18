package symap.projectmanager.common;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JSeparator;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class CollapsiblePanel extends JPanel {
	private JPanel panel; // for user-added components
	private JSeparator sep = null;
	private boolean collapsed;
	private JButton expandButton;
	private JLabel lblDesc = null;
	private Dimension collapsedSize;

	private static final ImageIcon expandIcon = createImageIcon("/images/plus.gif");
	private static final ImageIcon collapseIcon = createImageIcon("/images/minus.gif");
	
	public CollapsiblePanel(String strTitle, String strDescription) {
		this(strTitle, strDescription, true);
	}
	
	public CollapsiblePanel(String strTitle, String strDescription, boolean hasSeparator) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		expandButton = new JButton(strTitle);
		expandButton.setBorderPainted(false);
		expandButton.setFocusPainted(false);
		expandButton.setContentAreaFilled(false);
		expandButton.setMargin(new Insets(5, 0, 5, 0));
		expandButton.setFont(new Font(getFont().getName(), Font.PLAIN, 16));
		expandButton.addActionListener(
			new ActionListener( ) {
				public void actionPerformed(ActionEvent e) {
					if (collapsed) expand();
					else collapse();
		}});
		
		if (strDescription != null && strDescription.length() > 0) {
			lblDesc = new JLabel("     " + strDescription);
			lblDesc.setFont(new Font(lblDesc.getFont().getName(), Font.PLAIN, lblDesc.getFont().getSize()));
		}
		
		if (hasSeparator) 
			sep = new JSeparator();

		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
				
		collapse();
		
		super.add( expandButton );
		if (lblDesc != null) super.add( lblDesc );
		if (sep != null) super.add( sep );
		super.add( panel );
		
		collapsedSize = new Dimension(200, getPreferredSize().height);
	}
	
	public Component add(Component comp) {
		panel.add(comp);
		return comp;
	}
	
	private static ImageIcon createImageIcon(String path) {
	    java.net.URL imgURL = CollapsiblePanel.class.getResource(path);
	    if (imgURL != null)
	    	return new ImageIcon(imgURL);
	    else {
	    	System.err.println("Couldn't find icon: "+path);
	    	return null;
	    }
	}
	
	public void collapse() {
		collapsed = true;
		expandButton.setIcon(expandIcon);
		expandButton.setToolTipText("Show");
		if (sep != null) sep.setVisible(false);
		panel.setVisible(false);
		if (collapsedSize != null) setMaximumSize(collapsedSize); // prevent vertical stretching problem
	}
	
	public void expand() {
		collapsed = false;
		expandButton.setIcon(collapseIcon);
		expandButton.setToolTipText("Hide");
		if (sep != null) sep.setVisible(true);
		panel.setVisible(true);
		setMaximumSize(null);
	}
	
	// mdb added 8/20/09 - workaround for sizing problem when hasSeparator=false
	public void doClick() {
		expandButton.doClick();
	}
}
