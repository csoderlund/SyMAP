package symapQuery;

public class TableDataHeader {
    public TableDataHeader() {}

    public TableDataHeader(String label, Class<?> type) {
        strName = label;
        columnType = type;
    }

    public TableDataHeader(String label) {
        strName = label;
        columnType = Object.class;
    }

    public String getColumnName() { return strName; }

    public Class<?> getColumnClass() { return columnType; }
    
    public boolean isAscending() { return bAscending; }
    public void flipAscending() { bAscending = !bAscending; }
    public void setAscending(boolean ascending) { bAscending = ascending; } 

    public String toString() { return strName; }

    private String strName = null;
    private Class<?> columnType = null;
    private boolean bAscending = false;
}