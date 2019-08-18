package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

public class MenuPanel extends JPanel {
	private static final long serialVersionUID = 7029184547060500871L;
	
	public MenuPanel(String [] menuItems, int selection, ActionListener l) {
		theSelection = selection;
		theListener = l;
		
		theResults = new Vector<JButton> ();
		theOptions = new JButton[menuItems.length];
		for(int x=0; x<theOptions.length; x++) {
			theOptions[x] = new JButton(menuItems[x]);
			theOptions[x].setBorderPainted(false);
			theOptions[x].setFocusPainted(false);
			theOptions[x].setContentAreaFilled(false);
			theOptions[x].setMargin(new Insets(0, 0, 0, 0));
			theOptions[x].setVerticalAlignment(AbstractButton.TOP);
			theOptions[x].setHorizontalAlignment(AbstractButton.LEFT);
			theOptions[x].addActionListener(l);
		}
		buildPanel();
		showSelectedMenu();
	}
	
	public void handleClick(ActionEvent ae) {
		theSelection = -1;
		int x;
		for(x=0; theSelection < 0 && x < theOptions.length; x++) {
			if(ae.getSource().equals(theOptions[x]))
				theSelection = x;
		}
		Iterator<JButton> iter = theResults.iterator();
		while(iter.hasNext() && theSelection < 0) {
			if(ae.getSource().equals(iter.next()))
				theSelection = x;
			x++;
		}
		showSelectedMenu();
	}
	
	public void addResult(String label) {
		JButton newResult = new JButton(label);

		newResult.setBorderPainted(false);
		newResult.setFocusPainted(false);
		newResult.setContentAreaFilled(false);
		newResult.setMargin(new Insets(0, 0, 0, 0));
		newResult.setVerticalAlignment(AbstractButton.TOP);
		newResult.setHorizontalAlignment(AbstractButton.LEFT);
		newResult.addActionListener(theListener);
		newResult.setBackground(Color.WHITE);
		theResults.add(newResult);
		buildPanel();
		setSelection(theOptions.length + theResults.size() - 1);
	}
	
	public void removeResult(int pos) {
		theResults.get(pos).removeActionListener(theListener);
		theResults.remove(pos);
		buildPanel();
		setSelection(theOptions.length - 1);
	}
	
	public int getCurrentSelection() { return theSelection; }
	public String getCurrentSelectionLabel() { return theOptions[theSelection].getText(); }
	
	public boolean updateResultLabel(String oldLabel, String newLabel) {
		Iterator<JButton> iter = theResults.iterator();
		while(iter.hasNext()) {
			JButton temp = iter.next();
			if(temp.getText().startsWith(oldLabel)) {
				temp.setText(newLabel);
				buildPanel();
				return true;
			}
		}
		return false;
	}
	
	public void setSelection(int selection) { 
		theSelection = selection;
		showSelectedMenu();
	}
	
	public void setToolTipForMenu(String label, String text) {
		boolean found = false;
		
		for(int x=0; x<theOptions.length && !found; x++) {
			if(theOptions[x].getText().equals(label)) {
				theOptions[x].setToolTipText(text);
				found = true;
			}
		}
		
		if(!found) {
			Iterator<JButton> iter = theResults.iterator();
			while(iter.hasNext() && !found) {
				JButton temp = iter.next();
				if(temp.getText().equals(label)) {
					found = true;
					temp.setToolTipText(text);
				}
			}
		}		
	}
	
	private void buildPanel() {
		removeAll();
		repaint();
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		for(int x=0; x<theOptions.length; x++) {
			add(theOptions[x]);
			add(Box.createVerticalStrut(5));
		}
		
		Iterator<JButton> iter = theResults.iterator();
		while(iter.hasNext()) {
			JPanel temp = new JPanel();
			temp.setLayout(new BoxLayout(temp, BoxLayout.LINE_AXIS));
			temp.setBackground(Color.WHITE);
			temp.add(Box.createHorizontalStrut(10));
			temp.add(iter.next());
			Dimension d = temp.getPreferredSize();
			//Buffer for bold/plain text
			d.width += 20;
			temp.setMaximumSize(d);
			temp.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			add(temp);
		}
		setBackground(Color.WHITE);
		showSelectedMenu();
	}
	
	private void showSelectedMenu() {
		Font f = null;
		int x;
		for(x=0; x<theOptions.length; x++) {
			if(x==theSelection)
				f = new Font(theOptions[x].getFont().getName(), Font.BOLD, theOptions[x].getFont().getSize());
			else
				f = new Font(theOptions[x].getFont().getName(), Font.PLAIN, theOptions[x].getFont().getSize());
			theOptions[x].setFont(f);
		}
		Iterator<JButton> iter = theResults.iterator();
		while(iter.hasNext()) {
			JButton temp = iter.next();
			if(x==theSelection)
				f = new Font(temp.getFont().getName(), Font.BOLD, temp.getFont().getSize());
			else
				f = new Font(temp.getFont().getName(), Font.PLAIN, temp.getFont().getSize());
			temp.setFont(f);
			x++;
		}

	}
	private JButton [] theOptions = null;
	private Vector<JButton> theResults = null;
	private ActionListener theListener = null;
	private int theSelection = -1;
}
