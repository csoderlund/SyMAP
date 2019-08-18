package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

public class SortTable extends JTable implements ListSelectionListener {
	private static final long serialVersionUID = 5088980428070407729L;

    public SortTable(TableData tData) {
        theClickListeners = new Vector<ActionListener> ();
        theDoubleClickListeners = new Vector<ActionListener> ();
        
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
       	
        addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				if(me.getClickCount() > 1) {
				    ActionEvent e = new ActionEvent ( this, ActionEvent.ACTION_PERFORMED, "DoubleClickSingleRow" );
				    
					Iterator<ActionListener> iter = theDoubleClickListeners.iterator();
					while(iter.hasNext()) {
						iter.next().actionPerformed(e);
					}
				}
				else {
				    ActionEvent e = new ActionEvent ( this, ActionEvent.ACTION_PERFORMED, "SinlgeClickRow" );
					Iterator<ActionListener> iter = theClickListeners.iterator();
					while(iter.hasNext()) {
						ActionListener l = iter.next();
						l.actionPerformed(e);
					}					
				}
			}
			
        });

//        setAutoCreateColumnsFromModel( false );
        
//        getColumn("Select").setCellRenderer(new CheckBoxTableCellRenderer());
//        getColumn("Select").setCellEditor(new CheckBoxTableCellEditor());
    }  
    
    public void removeListeners() {
    	theClickListeners.clear();
    	theDoubleClickListeners.clear();
    }
    
    public void addSingleClickListener(ActionListener l) {
    	theClickListeners.add(l);
    }
    
    public void addDoubleClickListener(ActionListener l) {
    	theDoubleClickListeners.add(l);
    }
    
    public void sortAtColumn(int column) {
    	theModel.sortAtColumn(column);
    }
        
    public Component prepareRenderer(TableCellRenderer renderer,int Index_row, int Index_col) {
//     	if(renderer != null) {
    	Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
    	if(comp instanceof JLabel) {
        	JLabel compLbl = (JLabel)comp;
        	Class<?> cl = getColumnClass(Index_col);

        	//even index, selected or not selected
        	if(isRowSelected(Index_row)) {
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
        	if(cl == Integer.class) {
        		compLbl.setText(addCommas(compLbl.getText()));
        	}
        	else if(cl == Long.class) {
        		compLbl.setText(addCommas(compLbl.getText()));
        	}
        	compLbl.setHorizontalAlignment(SwingConstants.LEFT);
        	if(compLbl.getText().length() == 0)
        		compLbl.setText("-");
           	return compLbl;    		
    	}
    	return comp;
  //  	}
//    	return null;
    }
    
    private static String addCommas(String val) {
    	return val.replaceAll("(\\d)(?=(\\d{3})+$)", "$1,");
    }

    private Color bgColor = Color.WHITE;
    private Color bgColorAlt = new Color(240,240,255);
    private Color bgColorHighlight = Color.GRAY;
    private Color txtColor = Color.BLACK;
        
    private SortTableModel theModel = null;
    private Vector<ActionListener> theClickListeners = null;
    private Vector<ActionListener> theDoubleClickListeners = null;

	private static final int MAX_AUTOFIT_COLUMN_WIDTH = 120; // in pixels
    public void autofitColumns() {
        TableModel model = getModel();
        TableColumn column;
        Component comp;
        int headerWidth;
        int cellWidth;
        TableCellRenderer headerRenderer = getTableHeader().getDefaultRenderer();
        
        for (int i = 0;  i < getModel().getColumnCount();  i++) { // for each column
            column = getColumnModel().getColumn(i);
            
            comp = headerRenderer.getTableCellRendererComponent(
                                 this, column.getHeaderValue(),
                                 false, false, 0, i);
            
            headerWidth = comp.getPreferredSize().width + 10;
            
            cellWidth = 0;
            for (int j = 0;  j < getModel().getRowCount();  j++) { // for each row
	            comp = getDefaultRenderer(model.getColumnClass(i)).
	                             getTableCellRendererComponent(
	                                 this, model.getValueAt(j, i),
	                                 false, false, j, i);

	            cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
	            //Strings need to be adjusted
	            if(theModel.getColumnClass(i) == String.class)
	            	cellWidth += 5;
	            if (j > 100) break; // only check beginning rows, for performance reasons
            }

            column.setPreferredWidth(Math.min(Math.max(headerWidth, cellWidth), MAX_AUTOFIT_COLUMN_WIDTH));
        }
    }
    
    public class SortTableModel extends AbstractTableModel {

    	private static final long serialVersionUID = -2360668369025795459L;

    	public SortTableModel(TableData values) {
    		theData = values;
    	}

    	public boolean isCellEditable(int row, int column) { return false; }
    	public Class<?> getColumnClass(int columnIndex) { return theData.getColumnType(columnIndex); }
    	public String getColumnName(int columnIndex) { return theData.getColumnName(columnIndex); }
    	public int getColumnCount() { return theData.getNumColumns(); }
    	public int getRowCount() { return theData.getNumRows(); }
    	public Object getValueAt(int rowIndex, int columnIndex) { return theData.getValueAt(rowIndex, columnIndex); }
    	public void sortAtColumn(final int columnIndex) {
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

    	private TableData theData = null;
    }
}