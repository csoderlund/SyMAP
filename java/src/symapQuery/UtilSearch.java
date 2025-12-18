package symapQuery;

import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import symap.Globals;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.TreeSet;

import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/*****************************************************
 * Search button: select column to search and search for the entered gene
 */
public class UtilSearch  extends JDialog{
	private static final long serialVersionUID = 1L;

	private TableMainPanel tPanel;
	private String [] displayedCols;
	private AnnoData annoObj;
	private int rowIndex=-1, nextCnt=0, lastCol=0, lastSearch=0;
	private String lastValue="";
	private String [] skipCol = {Q.gEndCol, Q.gStartCol, Q.gLenCol, Q.gStrandCol, Q.hEndCol, Q.hStartCol, Q.hLenCol, Q.gOlap};// CAS578 add
	
	protected UtilSearch(TableMainPanel tpd, String [] cols, AnnoData spAnno) {
		this.tPanel = tpd;
		this.displayedCols = cols;
		this.annoObj = spAnno;
		
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE); // uses upper left icons
		setTitle("Search");
		
		createPanel();
		
		pack();
		this.setResizable(false);
		setLocationRelativeTo(null); // setVisible in calling routine so can reuse this panel with defaults
	}
	protected int getRowIndex() { return rowIndex;};

	/*****************************************************/
	private void createPanel() {
	try {
		JPanel selectPanel = Jcomp.createPagePanel();
		
		// Search columns
		TreeSet <String> speciesSet = annoObj.getSpeciesAbbrList();
		ArrayList <String> searchCols = new ArrayList <String> ();
		for (String col : displayedCols) {
			
			if (col.equals(Q.cosetCol) || col.equals(Q.hitCol) || col.equals(Q.blockCol) || col.equals(Q.grpCol)) {
				searchCols.add(col);
			}
			else if (col.endsWith(Q.gNCol)) { // gene
				searchCols.add(col.replace("\n"," "));
			}
			else if (col.contains("\n")) { // annotation only
				String [] tok = col.split("\n");
				if (!speciesSet.contains(tok[0])) continue; // would only fail on "Hit" columns; Hitx could be spAbbr
				
				if (tok.length<2) continue;
				
				String n = tok[1]; // only want any annotation fields
				for (int i=0; i<skipCol.length; i++) {
					if (skipCol[i].equals(n)) {
						n=null; break;
					}
				}
				if (n!=null) searchCols.add(col.replace("\n"," "));
			}
		}
		numCols = searchCols.size(); // Display even if numCols=0 so can close with Cancel
		
		JPanel colPanel = Jcomp.createPagePanel();
		if (numCols==0) colPanel.add(Jcomp.createHtmlLabel("No searchable columns"));
		else {
			colPanel.add(Jcomp.createHtmlLabel("Searchable columns")); colPanel.add(Box.createVerticalStrut(3));
			radColArr = new JRadioButton [searchCols.size()];
			ButtonGroup bg = new ButtonGroup();
			for (int i=0; i<numCols; i++) {
				String col = searchCols.get(i);
				radColArr[i] = Jcomp.createRadio(col, "Search on this column");
				colPanel.add(radColArr[i]); colPanel.add(Box.createVerticalStrut(3));
				bg.add(radColArr[i]);
			}
			radColArr[0].setSelected(true);
		}
		selectPanel.add(colPanel); 
		
		selectPanel.add(new JSeparator()); selectPanel.add(Box.createVerticalStrut(5));
			
		// Search string
		JPanel row0 = Jcomp.createRowPanel();
		txtSearch = Jcomp.createTextField("", "Enter search string",  15);
		row0.add(Jcomp.createLabel("  Search: ", "Enter search string"));row0.add(Box.createHorizontalStrut(1));
		row0.add(txtSearch);
		selectPanel.add(row0);  selectPanel.add(Box.createVerticalStrut(8));
		
		JPanel row1 = Jcomp.createRowPanel();
		ButtonGroup sg = new ButtonGroup();
		radLast = 		Jcomp.createRadio("Last#", "Search for last number after '.' or '-'");
		radExact = 		Jcomp.createRadio("Exact", "Search for exact string");
		radSubstring = 	Jcomp.createRadio("Substring", "Search for substring");
		
		row1.add(radLast); 		row1.add(Box.createHorizontalStrut(3));
		row1.add(radExact); 	row1.add(Box.createHorizontalStrut(3));
		row1.add(radSubstring); row1.add(Box.createHorizontalStrut(3));
		sg.add(radExact); sg.add(radLast); sg.add(radSubstring); radLast.setSelected(true);
		selectPanel.add(row1);
		
		selectPanel.add(new JSeparator()); selectPanel.add(Box.createVerticalStrut(5));
			
		// Buttons
		JPanel row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(5));
		JButton btnOK = Jcomp.createButton("Next", "Search for next string in table"); 	
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {	
				doSearch(); 
				setVisible(false); // have to do this so TableMain gets value
			}
		});
    	row.add(Box.createHorizontalStrut(5));
    	row.add(btnOK); 				row.add(Box.createHorizontalStrut(5));
    	btnOK.setEnabled(numCols>0);
    	
    	JButton btnCancel = Jcomp.createButton(Jcomp.close, "Close window"); 
    	btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
    	row.add(btnCancel); 			row.add(Box.createHorizontalStrut(5));
    	
    	JButton btnClear = Jcomp.createButton("Restart", "Restarts the search from the first row"); 
    	btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clear();
			}
		});
    	row.add(btnClear); 			row.add(Box.createHorizontalStrut(5));
    	
    	JButton btnInfo = Jcomp.createBorderIconButton("/images/info.png", "Quick Help Popup");
		btnInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				popupHelp();
			}
		});
		row.add(btnInfo); 			row.add(Box.createHorizontalStrut(5));
			
		selectPanel.add(row);
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		add(selectPanel);
	}
	catch (Exception e) {ErrorReport.print(e, "Creating search panel");}
	}
	private void clear() {
		lastValue = "";
		rowIndex = lastCol = lastSearch = -1;
		nextCnt = 0;
	}
	private void checkChange() {
		String val = txtSearch.getText().trim();
		
		int radVal=3; 			//radSubstring
		if (radExact.isSelected()) radVal=1; 
		else if (radLast.isSelected()) radVal=2; 
		
		int colVal = -1;
		for (int i=0; i<radColArr.length; i++) {
			if (radColArr[i].isSelected()) {
				colVal = i;
				break;
			}
		}
		if (!val.equals(lastValue) || radVal!=lastSearch || colVal!=lastCol) clear();
		
		lastValue = val;
		lastCol = colVal;
		lastSearch = radVal;
	}	
	/***************************************************
	 **************************************************/
	private int doSearch() { 
	try {
		checkChange();
		
		String findStr = txtSearch.getText().trim();
		if (findStr.equals("")) {setVisible(false); return -1;}
		
		int nrows = tPanel.theTable.getRowCount();
		
		// find the column name selected in the dialog
		String selColName="";
		for (int i=0; i<radColArr.length; i++) {
			if (radColArr[i].isSelected()) {
				selColName = radColArr[i].getText();
				break;
			}
		}
		
		// find the column index in the table headers
		int colIndex=-1;
		for (int x=0; x<tPanel.theTable.getColumnCount(); x++) {
			String name = tPanel.theTable.getColumnName(x);
			if (name.contains("\n")) name = name.replace("\n", " ");
			if (name.equals(selColName)) {
				colIndex = x;
				break;
			}
		}
		if (colIndex==-1) {Globals.eprt("No column index"); setVisible(false); return -1;}
		
		// Some adjustments of input string based on column
		if (selColName.equals(Q.hitCol) && findStr.contains(",")) 
			findStr = findStr.replace(",", ""); // for Hit#
		else if (selColName.endsWith(Q.gNCol) && !radSubstring.isSelected() && !findStr.contains(".")) // for last# or exact
			findStr += ".";
		
		// search each row
		int index= -1;
		for (int x=rowIndex+1; x<nrows; x++) {// start with last row index; CAS579
			Object colValObj = tPanel.theTable.getValueAt(x, colIndex);
			
			String colVal;
			if (colValObj instanceof String) colVal = (String) colValObj;
			else if (colValObj instanceof Integer) 	{ // Hit# only
				colVal = String.valueOf(colValObj);
				if (colVal.contains(",")) colVal = colVal.replace(",","");
			}
			else continue;
			
			if (colVal.equals(Q.empty)) continue;
			
			if (radLast.isSelected()) { // remove up to '-' or '.' from column value
				String modColVal = colVal;  			// this works as exact for non-Gene#
				
				if (!selColName.endsWith(Q.gNCol)) {
					String sepLast = (selColName.equals(Q.grpCol)) ? Q.GROUP : Q.SDOT; 
					String [] tok = colVal.split(sepLast);
					modColVal = (tok.length>0) ? tok[tok.length-1] : colVal;
				}
				else { 		// special processing in case of character at end
					modColVal = colVal.substring(colVal.indexOf(".")+1); // chr.N.{suf} - remove chr
				}
				if (modColVal.equals(findStr)) {
					index=x;
					break;
				}
			}
			else if (radSubstring.isSelected()) {
				if (colVal.contains(findStr)) {
					index=x;
					break;
				}
			}
			else { // exact
				if (colVal.equals(findStr)) {
					index=x;
					break;
				}
			}
 		}
		if (index == -1 && rowIndex == -1) {
			String op = "last#";
			if (radSubstring.isSelected()) op = "substring";
			else if (radExact.isSelected()) op = "exact";
			String msg = String.format("No %s value in column  %s  of '%s'", op, selColName,  txtSearch.getText().trim());
			JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
		}
		else if (index == -1) {
			String msg = String.format("Found all %,d values of '%s'", nextCnt, txtSearch.getText().trim());
			JOptionPane.showMessageDialog(null, msg, "Done", JOptionPane.PLAIN_MESSAGE);
		}
		else {
			rowIndex = index;
			nextCnt++;
		}
		return rowIndex;
	}
	catch (Exception e) {ErrorReport.print(e, "Performing search"); return -1;}
	}
	/****************************************/
	private void popupHelp() {
		String msg = "";
		msg += "Select column to search. Enter search string. Select search type:";
		msg += "\n  Last#: Enter the last number of a '.' or '-' delimited string, e.g. for 10-33, enter '33'."
			+  "\n         For Gene#, if the gene has a suffix, it must be in the search string, e.g. '1.a'."
			+ "\n  Exact: Enter the exact string. "
			+  "\n  Substring: The substring can be anywhere in the column value."
			+  "\n\nNext: If found, the table will redisplay the next row containing the value. ";
		msg += "\n\nSee ? for details.\n";
		util.Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	private int numCols=0;
	private JRadioButton radExact, radLast, radSubstring;
	private JTextField txtSearch = null;
	private JRadioButton [] radColArr;
}
