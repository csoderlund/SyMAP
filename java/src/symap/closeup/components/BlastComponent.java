package symap.closeup.components;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextArea;

import symap.closeup.alignment.HitAlignment;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class BlastComponent extends JTextArea {
// mdb removed 1/29/09 #159 simplify properties	
//	public static Color backgroundColor;
//	public static Color sequenceColor;
//	public static Font sequenceFont;
//	static {
//		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/blast.properties"));
//		backgroundColor   = props.getColor("backgroundColor");
//		sequenceColor     = props.getColor("sequenceColor");
//		sequenceFont      = props.getFont("sequenceFont");
//	}
	
	// mdb added 1/29/09 #159 simplify properties
	public static final Color backgroundColor = Color.white;
	public static final Color sequenceColor = Color.black;
	public static Font sequenceFont = null;
	
	private HitAlignment alignment;
	
	public BlastComponent() {
		super();
		setEditable(false);	
		if (sequenceFont == null)
		{
			sequenceFont = new Font(Font.MONOSPACED,0,16);
			//System.err.println("Font: " + sequenceFont.getFontName());
		}
		setFont(sequenceFont);
		setBackground(backgroundColor);
		this.alignment = null;
	}
	
	public BlastComponent(HitAlignment hitAlignment) {
		this();
		setAlignment(hitAlignment);
	}
	
	public String toString() {
		if (alignment == null) return super.toString();
		return alignment.toString();
	}
	
	public void setAlignment(HitAlignment ha) {
		alignment = ha;
		setVisible(false);
		selectAll();
		replaceRange(alignment.toString(), getSelectionStart(), getSelectionEnd());
		setVisible(true);
	}
	
	public static BlastComponent[] getBlastComponents(HitAlignment[] alignments) {
		BlastComponent[] bc = new BlastComponent[alignments.length];
		for (int i = 0; i < bc.length; ++i)
			bc[i] = new BlastComponent(alignments[i]);
		return bc;
	}
}
