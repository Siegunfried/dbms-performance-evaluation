import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 环境验证:连上 PostgreSQL 与 openGauss,打印两张表的行数。
 * 用途:确认两边数据都导入成功、JDBC 能通。
 *
 * 用法: java -cp ".;lib/postgresql-42.7.4.jar" Verify
 */
public class Verify {

    /** 连接并统计 movies / people 行数。 */
    static void check(String url, String user, String pass, String label) {
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement s = c.createStatement()) {
            long movies = count(s, "movies");
            long people = count(s, "people");
            System.out.printf("%-12s movies=%,d  people=%,d  (url=%s)%n",
                    label, movies, people, url);
        } catch (Exception e) {
            System.out.printf("%-12s 连接失败: %s%n", label, e.getMessage());
        }
    }

    static long count(Statement s, String table) throws Exception {
        try (ResultSet r = s.executeQuery("SELECT count(*) FROM " + table)) {
            r.next();
            return r.getLong(1);
        }
    }

    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        // PostgreSQL(本机 5432,库 dbperf,用户 postgres/postgres)
        check("jdbc:postgresql://localhost:5432/dbperf", "postgres", "postgres", "PostgreSQL");
        // openGauss(Docker 映射到本机 5433,库 postgres,用户 gaussdb)
        check("jdbc:postgresql://localhost:5433/postgres", "gaussdb", "Gaussdb@2026", "openGauss");
    }
}
