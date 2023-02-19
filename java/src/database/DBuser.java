package database;

/********************************************
 * CAS534 used with an existing dbc, necessary since DBuser is abstract
 */
public class DBuser  extends DBAbsUser {	
	public DBuser(DBconn dbc) {
		super(dbc);
	}
	public void close() {
		super.close();
	}
}
