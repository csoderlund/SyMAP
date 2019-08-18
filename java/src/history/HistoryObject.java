package history;

/**
 * Class <code>HistoryObject</code> is only used by the History class
 * as a way to store the reset points with the objects.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
class HistoryObject {

	private Object obj;
	private boolean reset;

	public HistoryObject(Object obj, boolean resetPoint) {
		this.obj = obj;
		this.reset = resetPoint;
	}

	public Object getObject() {
		return obj;
	}

	public boolean isResetPoint() {
		return reset;
	}
}
