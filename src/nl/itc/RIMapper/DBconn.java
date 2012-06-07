package nl.itc.RIMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class for opening, closing and querying connections to databases that support
 * the OGC SFS (Simple Features for SQL) specification. <br/>This version supports
 * PostgreSQL/PostGIS and MySQL. <br/>&copy;2004-12 International Institute for
 * Geo-information Science and Earth Observation (ITC) <br/>Licensed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 2.5 License. see <a
 * href="http://creativecommons.org/licenses/by-nc-sa/2.5/" target="_blank">
 * http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 1.1 [Feb 2012]
 */

// Major changes:
// 1.0 [Dec 2006] - first released version
// 1.1 [Feb 2012] - added HOST entry
//                - updated JDBC driver lib (postgresql-9.1-901.jdbc4.jar) for 
//                  compatibility with Postgres 9+. 

public class DBconn {

	private static final String POSTGIS_JDBC_DRIVER = "org.postgresql.Driver";

	private static final String POSTGIS_SFS_PROTOCOL = "jdbc:postgresql://";
	//NOTE that the default port [5432] is hardcoded (for now)

	private static final String MYSQL_JDBC_DRIVER = "com.mysql.jdbc.Driver";

	private static final String MYSQL_SFS_PROTOCOL = "jdbc:mysql://";

	private Connection JDBCconn;

	private ResultSet rs;

	private Statement st;

	public DBconn() {
		// constructor method;
	}

	/**
	 * @param SFSserver
	 *          name of the DB server to connect to. 
	 *          Only "MySQL" or "PostGIS" accepted.
   * @param HOST
   *          address of the host to connect to (localhost or someserver.domain.com)
   * @param DB
   *          name of the database to connect to.
	 * @param UN
	 *          username for DB connection.
	 * @param PW
	 *          password for DB connection.
	 * @return true if successful, false if not.
	 * @throws RIMapperException
	 */
	public boolean Open(String SFSserver, String HOST, String DB, String UN, String PW)
			throws RIMapperException {

		String ConnStr = null;
		boolean Opened = false;

		try {
			if (SFSserver.equals("MySQL")) { //$NON-NLS-1$
				Class.forName(MYSQL_JDBC_DRIVER).newInstance();
				ConnStr = MYSQL_SFS_PROTOCOL + HOST + "/" + DB + "?user=" + UN + "&password=" + PW;
			} else if (SFSserver.equals("PostGIS")) { //$NON-NLS-1$
				Class.forName(POSTGIS_JDBC_DRIVER).newInstance();
				ConnStr = POSTGIS_SFS_PROTOCOL + HOST + "/" + DB + "?user=" + UN + "&password=" + PW;
			} else {
				throw new RIMapperException(
						Messages.getString("DBconn.InvalidServer") + SFSserver //$NON-NLS-1$
								+ Messages.getString("DBconn.onlyMySQLPostGIS")); //$NON-NLS-1$
			}
			JDBCconn = DriverManager.getConnection(ConnStr);
			if (JDBCconn != null) {
				Opened = true;
			}
		} catch (InstantiationException e) {
			throw new RIMapperException(Messages.getString("DBconn.Instantiation")); //$NON-NLS-1$
		} catch (IllegalAccessException e) {
			throw new RIMapperException(Messages.getString("DBconn.IllegalAccess")); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			throw new RIMapperException(Messages.getString("DBconn.ClassNotFound")); //$NON-NLS-1$
		} catch (SQLException e) {
			throw new RIMapperException(
					Messages.getString("DBconn.SQLException") + e.getMessage() + "" +
					"[SFSserver=" + SFSserver + "; ConnStr = " + ConnStr + "]"); //$NON-NLS-1$
		}
		return Opened;
	} // Open

	/**
	 * @param SQL
	 *          SQL query to run against open DB connection.
	 * @return ResultSet object.
	 * @throws SQLException
	 */
	public ResultSet Query(String SQL) throws SQLException {

		if (JDBCconn != null) {
			st = JDBCconn.createStatement();
			rs = st.executeQuery(SQL);
		}
		return rs;
	}// Query

	/**
	 * Closes DB connection.
	 * @throws RIMapperException 
	 */
	public void Close(String SFSserver, String HOST) throws RIMapperException {
		try {
			if (rs != null)
				rs.close();
			if (st != null)
				st.close();
			if (JDBCconn != null)
				JDBCconn.close();
			//unregister the JDBC driver:
//      java.sql.Driver myDBDriver = null;
//			if (SFSserver.equals("MySQL")) {
//	      myDBDriver = DriverManager.getDriver(MYSQL_SFS_PROTOCOL + HOST + "/");
//      } else if (SFSserver.equals("PostGIS")) { 
//        myDBDriver = DriverManager.getDriver(POSTGIS_SFS_PROTOCOL + HOST + "/");
//      } else {
//        throw new RIMapperException(
//            Messages.getString("DBconn.InvalidServer") + SFSserver //$NON-NLS-1$
//                + Messages.getString("DBconn.onlyMySQLPostGIS")); //$NON-NLS-1$
//      }
//      DriverManager.deregisterDriver(myDBDriver);
			
		} catch (SQLException e) {
      throw new RIMapperException(
          Messages.getString("DBconn.SQLException") + " [Close()] " + e.getMessage());
    }
	} // Close

}
