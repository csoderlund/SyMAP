package symap.closeup;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextArea;

/***************************************************
 * Draws the text area at bottom of display with alignment
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TextComponent extends JTextArea {

	public static final Color backgroundColor = Color.white;
	public static final Color sequenceColor = Color.black;
	public static Font sequenceFont = null;
	
	private String proj1, proj2;
	private HitAlignment alignment;
	
	public TextComponent(String proj1, String proj2) {
		super();
		this.proj1=proj1;
		this.proj2=proj2;
		
		setEditable(false);	
		if (sequenceFont == null) sequenceFont = new Font(Font.MONOSPACED,0,16);
		
		setFont(sequenceFont);
		setBackground(backgroundColor);
		this.alignment = null;
	}
	
	public String toString() {
		if (alignment == null) return super.toString();
		return alignment.toText(true, proj1, proj2); // CAS531 changed from toString
	}
	
	public void setAlignment(HitAlignment ha) {
		alignment = ha;
		setVisible(false);
		selectAll();
		replaceRange(alignment.toString(), getSelectionStart(), getSelectionEnd());
		setVisible(true);
	}
}
