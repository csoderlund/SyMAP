package symapQuery;

public class TableDataHeader {
    protected TableDataHeader() {}

    protected TableDataHeader(String label, Class<?> type) {
        strName = label;
        columnType = type;
    }

    protected TableDataHeader(String label) {
        strName = label;
        columnType = Object.class;
    }

    protected String getColumnName() { return strName; }

    protected Class<?> getColumnClass() { return columnType; }
    
    protected boolean isAscending() { return bAscending; }
    protected void flipAscending() { bAscending = !bAscending; }
    protected void setAscending(boolean ascending) { bAscending = ascending; } 

    public String toString() { return strName; }

    private String strName = null;
    private Class<?> columnType = null;
    private boolean bAscending = false;
}