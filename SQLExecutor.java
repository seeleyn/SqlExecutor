
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/*
 * This is a development tool used to allow one to run JDBC queries.
 * 
 * @author seeleyn
 *
 */
class SQLExecutor {
	
	private static final int MAX_FIELD_LENGTH = 100;

	private static String getFormatterString(int width) {
		return "%-" + width + "." + width + "s ";
	}

	private static String truncateString(String str, int maxChars) {
		if (str == null) {
			return null;
		}
		if (str.length() > maxChars) {
			return str.substring(0, maxChars);
		} else {
			return str;
		}
	}

	private static String createOutputString(Object obj, int maxChars) {
		String str = String.valueOf(obj);
		String truncatedStr = truncateString(str, maxChars);
		if (truncatedStr.length() < str.length()) {
			truncatedStr += "...";
		}
		return "'" + truncatedStr + "'";
	}

	private static String toOutputString(ResultSet resultSet) throws SQLException {
		return toOutputString(resultSet, null);
	}

	private static String toOutputString(ResultSet resultSet, RowFilter rowFilter) throws SQLException {
		if (resultSet == null) {
			System.err.println("null input parameter passed to printResults()");
			return "";
		}
		ResultSetMetaData metaData = resultSet.getMetaData();
		int numOfCols = metaData.getColumnCount();
		// numOfCols = Math.min(numOfCols, maxColumns);

		List<String> colHeaders = new ArrayList<String>();
		for (int i = 1; i <= numOfCols; i++) {
			colHeaders.add(metaData.getColumnName(i));
		}

		ResultTable resultTable = new ResultTable(colHeaders, rowFilter);

		while (resultSet.next()) {
			ArrayList<String> row = new ArrayList<String>();
			for (int i = 1; i <= numOfCols; i++) {
				Object val = resultSet.getObject(i);
				String valueString;
				if (val instanceof Clob) {
					valueString = ((Clob)val).getSubString(1,MAX_FIELD_LENGTH);
				} else {
					valueString = String.valueOf(val);
				}
				row.add(valueString);
			}
			resultTable.addRow(row);
		}
		return resultTable.toString();
	}

	private static void executeQuery(Connection conn, BufferedReader stdin, String commandLineQuery)
			throws SQLException, IOException, InterruptedException {
		if (conn == null || stdin == null) {
			throw new IllegalArgumentException("null input param : conn-" + (conn == null) + ", stdin-"
					+ (stdin == null));
		}

		String sqlQuery;
		if (commandLineQuery != null && commandLineQuery.trim().length() > 1) {
			sqlQuery = commandLineQuery.substring(1).trim();
		} else {
			System.out.println("Enter your SQL query");
			sqlQuery = stdin.readLine();
		}

		Statement stmt = conn.createStatement();
		ResultSet resultSet = stmt.executeQuery(sqlQuery);
		System.out.println("\nResults of '" + sqlQuery + "'\n");
		System.out.println(toOutputString(resultSet));
	}

	private static void printMenu() {
		System.out.println("\nEnter your choice");
		System.out.println("'q' - for an SQL query");
		System.out.println("'u' - to add or update data via SQL");
		System.out.println("'t' - [optional schema name] - to show tables");
		System.out.println("'r' - repeat the last query");
		System.out.println("'x' - to exit this program");
	}

	private static void executeUpdate(Connection conn, BufferedReader stdin) throws SQLException, IOException {
		if (conn == null || stdin == null) {
			throw new IllegalArgumentException("null input param : conn-" + (conn == null) + ", stdin-"
					+ (stdin == null));
		}
		System.out.println("Enter the SQL for your update");
		String sql = stdin.readLine();
		Statement stmt = conn.createStatement();
		int rowsAffected = stmt.executeUpdate(sql);
		System.out.println("\nResults of '" + sql + "'\n");
		System.out.println(rowsAffected + " rows affected");
	}

	private static void showTables(Connection conn, BufferedReader stdin, String cmdLineInput) throws SQLException,
			IOException {
		if (conn == null || stdin == null) {
			throw new IllegalArgumentException("null input param : conn-" + (conn == null) + ", stdin-"
					+ (stdin == null));
		}

		String schema = null;
		RowFilter rowFilter = null;
		if (cmdLineInput != null) {
			String[] choiceTokens = cmdLineInput.split("\\s");

			if (choiceTokens.length >= 2) {
				schema = choiceTokens[1];
			}
			if (choiceTokens.length >= 3) {
				if ("oracleFilter".equals(choiceTokens[2])) {
					/*
					 * This is a temporary hack to filter out the dozens of
					 * Oracle generated tables that make the table listing
					 * impossible to dig through. These tables have names like
					 * "BIN$g24S543ssdf...".
					 */
					rowFilter = new RowFilter("TABLE_NAME", "^BIN\\$.*");
				}
			}
		}

		DatabaseMetaData dbMetaData = conn.getMetaData();
		ResultSet results = dbMetaData.getTables(null, schema, null, new String[] { "TABLE" });

		System.out.println(toOutputString(results, rowFilter));
	}

	private static class RowFilter {
		private final String columnName;
		private final String regExToFilterOut;

		public RowFilter(String colName_, String regExToFilterOut_) {
			if (colName_ == null || regExToFilterOut_ == null) {
				throw new IllegalArgumentException("null input param : colName_-" + (colName_ == null)
						+ ", regExToFilterOut_-" + (regExToFilterOut_ == null));
			}
			this.columnName = colName_;
			this.regExToFilterOut = regExToFilterOut_;
		}

		public String getColumnName() {
			return columnName;
		}

		public String getRegExToFilterOut() {
			return regExToFilterOut;
		}
	}

	private static class ResultTable {

		private List<String> columnHeaders = new ArrayList<String>();

		private List<List<String>> rows = new ArrayList<List<String>>();

		private RowFilter rowFilter = null;

		public ResultTable(List<String> columnHeaders_, RowFilter rowFilter_) {
			if (columnHeaders_ == null) {
				throw new IllegalArgumentException("null input param : columnHeaders_-" + (columnHeaders_ == null));
			} else if (columnHeaders_.size() <= 0) {
				throw new IllegalArgumentException("columnHeaders_ is empty (size = " + columnHeaders_.size() + ")");
			}
			this.columnHeaders.addAll(columnHeaders_);
			this.rowFilter = rowFilter_;
		}

		/*
		 * Commented out because the newer versions of eclipse show a warning
		 * compliaining this method is unused public ResultTable(List<String>
		 * columnHeaders_) { this(columnHeaders_, null); }
		 */

		private boolean passesRowFilter(List<String> row) {
			if (row == null) {
				return false;
			}
			if (rowFilter == null) {
				return true;
			}
			int colIndex = getColumnIndex(rowFilter.getColumnName());
			String field = row.get(colIndex);
			return !String.valueOf(field).matches(rowFilter.getRegExToFilterOut());
		}

		private int getColumnIndex(String columnHeader) {
			for (int i = 0; i < columnHeaders.size(); i++) {
				if (String.valueOf(columnHeaders.get(i)).equals(columnHeader)) {
					return i;
				}
			}
			throw new IllegalStateException(columnHeader + " is not a legal column header");
		}

		public void addRow(List<String> row) {
			if (row == null) {
				System.err.println("null input param : row-" + (row == null));
			} else if (row.size() != this.columnHeaders.size()) {
				System.err.println("# of columns in row (" + row.size() + ") != to # of columns in column headers ("
						+ columnHeaders.size() + ")");
			} else {
				if (passesRowFilter(row)) {
					rows.add(row);
				}
			}
		}

		public int getMaxFieldLength4Column(int columnIndex) {
			if (columnIndex < 0 || columnIndex >= columnHeaders.size()) {
				throw new IllegalArgumentException("Column index " + columnIndex + " not between 0 and "
						+ (columnHeaders.size() - 1));
			}
			int maxFieldLength = columnHeaders.get(columnIndex).length();

			for (List<String> row : rows) {
				String field = row.get(columnIndex);
				maxFieldLength = Math.max(maxFieldLength, field.length());
			}
			return maxFieldLength;
		}

		private String rowToString(List<String> row) {
			if (row == null) {
				return "";
			}
			StringBuilder output = new StringBuilder();
			for (int i = 0; i < columnHeaders.size(); i++) {
				int fieldLength = getMaxFieldLength4Column(i);
				fieldLength = Math.min(fieldLength, MAX_FIELD_LENGTH);
				final int PADDING = 5;
				String field = createOutputString(row.get(i), fieldLength);
				final String formatStr = getFormatterString(fieldLength + PADDING);
				output.append(String.format(formatStr, field));
			}
			output.append("\n");
			return output.toString();
		}

		public String toString() {
			StringBuilder output = new StringBuilder();
			String columnHeaderStr = rowToString(columnHeaders);
			output.append(columnHeaderStr);

			for (int i = 0; i < columnHeaderStr.length(); i++) {
				output.append("-");
			}
			output.append("\n");

			for (List<String> row : rows) {
				output.append(rowToString(row));
			}

			return output.toString();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("Usage   : jdbcDriverClass jdbcURL user [password]");
			return;
		}
		String driver = args[0];
		String jdbcURL = args[1];
		String user = args[2];
		String password = "";
		if (args.length == 4) {
			password = args[3];
		}

		System.out.println("Driver   : " + driver);
		System.out.println("JDBC URL : " + jdbcURL);
		System.out.println("Username : " + user);

		Class.forName(driver);
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(jdbcURL, user, password);

			printMenu();
			BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
			String choice = stdin.readLine();
			String lastQuery = null;
			while (!choice.startsWith("x")) {
				try {
					if (choice.startsWith("q")) {
						lastQuery = choice;
						executeQuery(conn, stdin, choice);
					} else if (choice.startsWith("r")) {
						executeQuery(conn, stdin, lastQuery);
					} else if (choice.startsWith("u")) {
						executeUpdate(conn, stdin);
					} else if (choice.startsWith("t")) {
						showTables(conn, stdin, choice);
					} else if (choice.startsWith("x")) {
						// do nothing, this means exit
					} else {
						System.out.println("Error unrecognized option : " + choice);
					}
				} catch (Throwable th) {
					th.printStackTrace();
				}
				printMenu();
				choice = stdin.readLine();
			}

		} finally {
			if (conn != null) {
				conn.close();
			}
		}
		System.out.println("Done");
	}

}
