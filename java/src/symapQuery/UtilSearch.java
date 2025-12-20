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
import javax.swing.JPanel;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.TreeSet;

import util.ErrorReport;
import util.Jcomp;

/*****************************************************
 * Search button: select column to search and search for the entered gene
 */
public class UtilSearch  extends JDialog{
	private static final long serialVersionUID = 1L;

	private TableMainPanel tPanel;
	private String [] displayedCols;
	private AnnoData annoObj;
	private int rowIndex=-1, colIndex=-1, nextCnt=0, totalCnt=0;
	private int curType=0; // 1 last 2 exact 3 substring - only used to see if  value has changed
	private String curVal="", curCol;
	private boolean isLast=false, isExact=false, isSubstring=false, isGene=false, isHit=false;
	
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
				radColArr[i].addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {restart();}});
			}
			radColArr[0].setSelected(true);
		}
		selectPanel.add(colPanel); 
		
		selectPanel.add(new JSeparator()); selectPanel.add(Box.createVerticalStrut(5));
			
	// Search string
		JPanel row0 = Jcomp.createRowPanel();
		
		txtSearch = Jcomp.createTextField("", "Enter search string",  15);
		txtSearch.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {restart();}});
		row0.add(Jcomp.createLabel("  Search: ", "Enter search string"));row0.add(Box.createHorizontalStrut(1));
		row0.add(txtSearch);
		selectPanel.add(row0);  selectPanel.add(Box.createVerticalStrut(8));
		
		JPanel row1 = Jcomp.createRowPanel();
		ButtonGroup sg = new ButtonGroup();
		radLast = 		Jcomp.createRadio("Last#", "Search for last number after '.' or '-'");
		radExact = 		Jcomp.createRadio("Exact", "Search for exact string");
		radSubstring = 	Jcomp.createRadio("Substring", "Search for substring");
		radLast.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {restart();}});
		radExact.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {restart();}});
		radSubstring.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {restart();}});
		
		row1.add(radLast); 		row1.add(Box.createHorizontalStrut(3));
		row1.add(radExact); 	row1.add(Box.createHorizontalStrut(3));
		row1.add(radSubstring); row1.add(Box.createHorizontalStrut(3));
		sg.add(radExact); sg.add(radLast); sg.add(radSubstring); radLast.setSelected(true);
		selectPanel.add(row1);
		
		selectPanel.add(new JSeparator()); selectPanel.add(Box.createVerticalStrut(5));
			
	// Text status - above buttons CAS579b add
		JPanel rows = Jcomp.createRowPanel(); rows.add(Box.createHorizontalStrut(6));
		txtStatus = new JTextField("New search", 25); txtStatus.setBackground(Color.WHITE);
		txtStatus.setBorder(BorderFactory.createEmptyBorder());
		rows.add(txtStatus);
		selectPanel.add(rows); selectPanel.add(Box.createVerticalStrut(5));
		
	// Buttons
		JPanel row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(5));
		JButton btnOK = Jcomp.createButton("Next", "Search for next string in table"); 	
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {	
				doNext(); // CAS579b do not close windows after search
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
				restart();
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
		selectPanel.setMinimumSize(selectPanel.getPreferredSize());
		selectPanel.setMaximumSize(selectPanel.getPreferredSize());
		
		add(selectPanel);
	}
	catch (Exception e) {ErrorReport.print(e, "Creating search panel");}
	}
	/***********************************************************/
	private void restart() {
		curVal = curCol = "";	
		curType = -1;
		rowIndex =  -1;
		nextCnt = totalCnt = 0;
		setStatus("New search");
	}
	/** See if anything has changed **/
	private void setChgVal() {
		String val = txtSearch.getText().trim();
		
		isLast=isExact=isSubstring=false;
		int valType; 				  
		if (radLast.isSelected()) 		{valType=1; isLast=true;}
		else if (radExact.isSelected())	{valType=2; isExact=true;} 
		else 							{valType=3; isSubstring=true;} 
		
		String valCol = "";
		for (int i=0; i<radColArr.length; i++) {
			if (radColArr[i].isSelected()) {
				valCol = radColArr[i].getText();
				break;
			}
		}
		
		if (val.equals(curVal) && valType==curType && valCol.equals(curCol)) return; // continue search
		
		restart();

		curVal  = val;
		curCol  = valCol;
		curType = valType;
		
		isGene = curCol.endsWith(Q.gNCol);
		isHit  = curCol.equals(Q.hitCol);
		
		// find the column index in the table headers
		colIndex=-1;
		for (int x=0; x<tPanel.theTable.getColumnCount(); x++) {
			String name = tPanel.theTable.getColumnName(x);
			if (name.contains("\n")) name = name.replace("\n", " ");
			if (name.equals(curCol)) {
				colIndex = x;
				break;
			}
		}
		if (colIndex==-1) {curVal=""; Globals.eprt("No column index"); return;}
	}	
	/***************************************************
	 **************************************************/
	private void doNext() { 
	try {
		setChgVal();
	
		if (curVal.equals("")) {setStatus("No search value"); return; }
		
		// Some adjustments of input string based on column; leave curCol alone for display
		String findStr=curVal;
		if (isHit && findStr.contains(",")) findStr = findStr.replace(",", ""); 
		else if (isGene && !isSubstring && !findStr.contains(".")) findStr += "."; // for last# or exact, needs '.' at end
			
	// Loop: count number of instances if starting new search
		int nrows = tPanel.theTable.getRowCount();
		if (totalCnt==0) {
			while (true) {
				rowIndex = getRowIndex(nrows, findStr);		// use rowIndex so it starts the next search at it
				if (rowIndex>=0) totalCnt++;
				else {
					rowIndex = -1;  // starts over in search using same values
					break;
				}
			}
		}
		if (totalCnt==0) {
			String op = "Last#";
			if (isExact) op = "Exact";
			else if (isSubstring) op = "Substring";
			String msg = String.format("No %s value in column %s of '%s'", op, curCol,  txtSearch.getText().trim());
			setStatus(msg);
			return;
		}
	// get the next
		int index = getRowIndex(nrows, findStr);

		if (index == -1 && rowIndex == -1) {
			setStatus("Should not happen");
		}
		else if (index == -1) {
			String msg = String.format("Found all %,d values", nextCnt);
			setStatus(msg);
		}
		else {
			rowIndex = index;
			nextCnt++;
			setStatus("Found " + nextCnt + " of " + totalCnt);
			tPanel.showSearch(rowIndex);	// set results from here; CAS579b 
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Performing search"); return;}
	}
	/**************************************************************
	 * Find next curVal
	 */
	private int getRowIndex(int nrows,  String findStr) {
		int index= -1;
		
		for (int x=rowIndex+1; x<nrows; x++) {// start with last row index; CAS579
			// get column value
			Object colValObj = tPanel.theTable.getValueAt(x, colIndex);
			String colVal;
			
			if (colValObj instanceof String) colVal = (String) colValObj;
			else if (colValObj instanceof Integer) 	{ // Hit# only
				colVal = String.valueOf(colValObj);
				if (colVal.contains(",")) colVal = colVal.replace(",","");
			}
			else {Globals.tprt("found non-integer or string value"); continue;} 
			
			if (colVal.equals(Q.empty)) continue;
			
			if (isLast) { // remove up to '-' or '.' from column value
				String modColVal = colVal;  			// this works as exact for non-Gene#
				
				if (!isGene) {
					String sepLast = (curCol.equals(Q.grpCol)) ? Q.GROUP : Q.SDOT; 
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
			else if (isExact) {
				if (colVal.equals(findStr)) {
					index=x;
					break;
				}
			}
			else {
				if (colVal.contains(findStr)) {
					index=x;
					break;
				}
			}
 		}
		return index;
	}
	private void setStatus(String msg) {
		txtStatus.setText(msg);
	}
	/****************************************/
	private void popupHelp() {
		String msg = "";
		msg += "Select column to search. Enter search string. Select search type:";
		msg += "\n  Last#: Enter the last number of a '.' or '-' delimited string, e.g. for 10-33, enter '33'."
			+  "\n         For Gene#, if the gene has a suffix, it must be in the search string, e.g. '1.a'."
			+ "\n  Exact: Enter the exact string. "
			+  "\n  Substring: The substring can be anywhere in the column value."
			+  "\n\nNext: If found, the table will redisplay the next row containing the value. "
			+  "\n\nRestart: Will restart the search. "
			+  "\n         Changing any value on the panel will also restart the search. ";
		msg += "\n\nSee ? for details.\n";
		util.Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	private int numCols=0;
	private JRadioButton radExact, radLast, radSubstring;
	private JTextField txtSearch = null;
	private JRadioButton [] radColArr;
	 private JTextField txtStatus = null;
}
