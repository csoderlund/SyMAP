package arranger.algo;

public class ListElem {
	int m_value;
	ListElem m_next;
	
	public ListElem(int value, ListElem next) {
		m_value = value;
		m_next = next;
	}
	
	public ListElem getNext() { return m_next; }
	public int getValue() { return m_value; }
	
	public boolean ordered() {
		if(m_next == null)
			return true;
		if(m_value > m_next.m_value)
			return false;
		return m_next.ordered();
	}
}
