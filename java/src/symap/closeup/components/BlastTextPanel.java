package symap.closeup.components;

import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class BlastTextPanel extends JPanel {
	private JTextArea text;

	public BlastTextPanel() {
		super(new BorderLayout());
		text = new JTextArea();
		text.setFont(BlastComponent.sequenceFont);
		text.setLineWrap(false);
		text.setEditable(false);	

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton button = new JButton("Select All");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.selectAll();
			}
		});
		buttonPanel.add(button);
		button = new JButton("Copy");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.copy();
			}
		});
		buttonPanel.add(button);	

		add(text,BorderLayout.CENTER);
		add(buttonPanel,BorderLayout.SOUTH);
	}

	public BlastTextPanel(BlastComponent bc) {
		this();
		setBlastComponent(bc);
	}

	public BlastTextPanel(BlastComponent[] bc) {
		this();
		setBlastComponents(bc);
	}

	public void setBlastComponent(BlastComponent bc) {
		text.setText(bc.toString());
		text.setCaretPosition(0);
	}

	public void setBlastComponents(BlastComponent[] bc) {
		text.setText("");
		for (int i = 0; i < bc.length; ++i)
			text.append(bc[i].toString());
		text.setCaretPosition(0);
	}

	public void addBlastComponent(BlastComponent bc) {
		text.append(bc.toString());
	}

	public void clear() {
		text.setText("");
	}
}
