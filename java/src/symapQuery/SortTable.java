package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/*******************************************************
 * Called from buildTable and setTable (change to columns)
 * Referred to as 'theTable' in TableDataPanel
 */
public class SortTable extends JTable implements ListSelectionListener {
	private static final long serialVersionUID = 5088980428070407729L;

    protected SortTable(TableData tData) {
    	theModel = new SortTableModel(tData);
    	
    	setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        setAutoCreateColumnsFromModel( true );
       	setColumnSelectionAllowed( false );
       	setCellSelectionEnabled( false );
       	setRowSelectionAllowed( true );
       	setShowHorizontalLines( false );
       	setShowVerticalLines( true );	
       	setIntercellSpacing ( new Dimension ( 1, 0 ) );
       	setOpaque(true);

       	setModel(theModel);
       	// CAS560 removed listeners, they were inactive
    }  
    
    protected void sortAtColumn(int column) {
    	theModel.sortAtColumn(column);
    }
    protected void autofitColumns() { 
        TableModel model = getModel();
        TableColumn column;
        Component comp;
       
        int cellWidth, maxDef;
        TableCellRenderer headerRenderer = getTableHeader().getDefaultRenderer();
      
        // CAS504 use different max widths, less padding, more iterations
        for (int i = 0;  i < getModel().getColumnCount();  i++) { // for each column; default order
            column = getColumnModel().getColumn(i);
          
            comp = headerRenderer.getTableCellRendererComponent(this, column.getHeaderValue(), false, false, 0, i); 
            cellWidth = comp.getPreferredSize().width; // header width 
            
            for (int j = 0;  j < getModel().getRowCount();  j++) { // for each row
	            comp = getDefaultRenderer(model.getColumnClass(i)).
	               getTableCellRendererComponent(this, model.getValueAt(j, i), false, false, j, i);

	            cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
	            
	            if (j > 1001) break; // only check beginning rows, for performance reasons
            }
      
            String head = (String) column.getHeaderValue();
            if (head.contains(Q.chrCol)) maxDef = MAX_AUTOFIT_COLUMN_CHR_WIDTH;
            else if (theModel.getColumnClass(i) == String.class)  
            							maxDef = MAX_AUTOFIT_COLUMN_STR_WIDTH;
            else 						maxDef = MAX_AUTOFIT_COLUMN_INT_WIDTH;       
            column.setPreferredWidth(Math.min(cellWidth, maxDef)+5);
        }
    }
    /* required */
    public Component prepareRenderer(TableCellRenderer renderer,int Index_row, int Index_col) {
    	Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
    	if (comp instanceof JLabel) {
        	JLabel compLbl = (JLabel)comp;
        	Class<?> cl = getColumnClass(Index_col);

        	//even index, selected or not selected
        	if (isRowSelected(Index_row)) {
        		compLbl.setBackground(bgColorHighlight);
        		compLbl.setForeground(bgColor);
        	}
        	else if (Index_row % 2 == 0) {
        		compLbl.setBackground(bgColorAlt);
        		compLbl.setForeground(txtColor);
        	} 
        	else {
            	compLbl.setBackground(bgColor);
            	compLbl.setForeground(txtColor);
        	}
        	if (cl == Integer.class) {
        		compLbl.setText(addCommas(compLbl.getText()));
        	}
        	else if (cl == Long.class) {
        		compLbl.setText(addCommas(compLbl.getText()));
        	}
        	compLbl.setHorizontalAlignment(SwingConstants.LEFT);
        	if (compLbl.getText().length() == 0)
        		compLbl.setText("-");
	        return compLbl;    		
	    }
	    return comp;
    }
    
    private static String addCommas(String val) {
    	return val.replaceAll("(\\d)(?=(\\d{3})+$)", "$1,");
    }

    /**********************************************************/
    private SortTableModel theModel = null;

    private final Color bgColor = Color.WHITE;
    private final Color bgColorAlt = new Color(240,240,255);
    private final Color bgColorHighlight = Color.GRAY;
    private final Color txtColor = Color.BLACK;
    
	private final int MAX_AUTOFIT_COLUMN_STR_WIDTH = 120; // in pixels
	private final int MAX_AUTOFIT_COLUMN_INT_WIDTH = 90; // in pixels
	private final int MAX_AUTOFIT_COLUMN_CHR_WIDTH = 60; // in pixels
	
	/*******************************************************************************/
	protected class SortTableModel extends AbstractTableModel {
		private static final long serialVersionUID = -2360668369025795459L;

		protected SortTableModel(TableData values) {
			theData = values;
		}

		protected void sortAtColumn(final int columnIndex) {
			Thread t = new Thread(new Runnable() {
    		public void run() {
            	setCursor(new Cursor(Cursor.WAIT_CURSOR));
        		theData.sortByColumn(columnIndex);
        		fireTableDataChanged();
        		theData.sortMasterList(theModel.getColumnName(columnIndex));
             	setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    		}
	    	});
	    	t.setPriority(Thread.MIN_PRIORITY);
	    	t.start();
		}
		/* required */
		public boolean isCellEditable(int row, int column) { return false; }
		public Class<?> getColumnClass(int columnIndex) { return theData.getColumnType(columnIndex); }
		public String getColumnName(int columnIndex)    { return theData.getColumnName(columnIndex); }
		public int getColumnCount() { return theData.getNumColumns(); }
		public int getRowCount()    { return theData.getNumRows(); }
		public Object getValueAt(int rowIndex, int columnIndex) { return theData.getValueAt(rowIndex, columnIndex); }
		 
		/* data */
		private TableData theData = null;
    }
}