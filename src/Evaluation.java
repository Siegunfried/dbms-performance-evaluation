import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class Evaluation {

    static final String PG_URL  = "jdbc:postgresql://127.0.0.1:5432/dbperf";
    static final String PG_USER = "postgres";
    static final String PG_PASS = "postgres";
    static final String OG_URL  = "jdbc:postgresql://127.0.0.1:5433/postgres";
    static final String OG_USER = "gaussdb";
    static final String OG_PASS = "Gaussdb@2026";

    // 数据文件
    static final String MOVIES_TSV = "data/movies.tsv";
    static final String PEOPLE_TSV = "data/people.tsv";

    // 实验参数
    static final String[] WORDS  = {"love", "war", "the", "zzzqqq"}; // 选择性:1.06% / 0.75% / 22.5% / 0%
    static final int RUNS    = 5;   // 计时运行次数
    static final int WARMUPS = 1;   // 预热次数
    static final int[] SIZES = {100_000, 250_000, 500_000, 1_000_000}; // 规模缩放

    // 计时统计
    static class Stats {
        final double median, mean, std;
        Stats(double median, double mean, double std) {
            this.median = median; this.mean = mean; this.std = std;
        }
        @Override public String toString() {
            return String.format("%8.2f ms  (median, ±%.2f)", median, std);
        }
    }

    // nanos 数组转毫秒,统计中位数/均值/标准差
    static Stats stats(long[] nanos) {
        double[] ms = new double[nanos.length];
        for (int i = 0; i < nanos.length; i++) ms[i] = nanos[i] / 1e6;
        Arrays.sort(ms);
        double median = ms[ms.length / 2];
        double sum = 0;
        for (double v : ms) sum += v;
        double mean = sum / ms.length;
        double var = 0;
        for (double v : ms) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / ms.length);
        return new Stats(median, mean, std);
    }

    // 取中位数并格式化为两位小数
    static String ms(long[] a) { return String.format("%.2f", stats(a).median); }

    // 工具函数
    static void hr(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    static Connection connect(String url, String user, String pass) throws SQLException {
        Connection c = DriverManager.getConnection(url, user, pass);
        return c;
    }

    // 取一行里的 title(第 2 列)
    static String titleOf(String line) {
        int t1 = line.indexOf('\t');
        int t2 = line.indexOf('\t', t1 + 1);
        return line.substring(t1 + 1, t2);
    }

    static BufferedReader openTsv(String path) throws IOException {
        return new BufferedReader(
            new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8), 1 << 20);
    }

    // 实验一:检索(文件扫描 vs 数据库)
    // 文件侧:逐行扫描,统计 title 含关键词的行数,maxLines 用于截断
    static long[] timeFileScan(String word, int maxLines, int runs) throws IOException {
        long[] nanos = new long[runs];
        for (int r = -WARMUPS; r < runs; r++) {
            int hits = 0, lines = 0;
            long t0 = System.nanoTime();
            try (BufferedReader br = openTsv(MOVIES_TSV)) {
                String line;
                while ((line = br.readLine()) != null && lines < maxLines) {
                    if (titleOf(line).toLowerCase().contains(word)) hits++;
                    lines++;
                }
            }
            long t1 = System.nanoTime();
            if (r >= 0) nanos[r] = t1 - t0;
        }
        return nanos;
    }

    // 数据库侧:跑 count(*),ILIKE 忽略大小写
    static long[] timeDbQuery(Connection c, String sql, int runs) throws SQLException {
        long[] nanos = new long[runs];
        for (int r = -WARMUPS; r < runs; r++) {
            long t0 = System.nanoTime();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) { /* 只消费结果,不打印 */ }
            }
            long t1 = System.nanoTime();
            if (r >= 0) nanos[r] = t1 - t0;
        }
        return nanos;
    }

    static long countQuery(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static void e1Retrieval(Connection pg, Connection og) throws Exception {
        hr("E1 检索对比:文件线性扫描 vs DB(PostgreSQL / openGauss)");
        System.out.printf("%-8s | %-14s | %-14s | %-14s | 命中数%n",
                "关键词", "文件扫描", "PostgreSQL", "openGauss", "");
        System.out.println("--------------------------------------------------------------------------------");
        for (String word : WORDS) {
            long[] f = timeFileScan(word, Integer.MAX_VALUE, RUNS);
            String sql = "SELECT count(*) FROM movies WHERE title ILIKE '%" + word + "%'";
            long n = countQuery(pg, sql);
            long[] p = timeDbQuery(pg, sql, RUNS);
            long[] o = timeDbQuery(og, sql, RUNS);
            System.out.printf("%-8s | %9s | %10s | %9s | %d%n",
                    word, ms(f), ms(p), ms(o), n);
        }
    }

    // 实验二:更新(文件改写 vs 数据库)
    // 文件侧:读 people.tsv,把姓名里的 T 换成 Ttt 另写一份
    static long[] timeFileUpdate(int runs) throws IOException {
        long[] nanos = new long[runs];
        for (int r = -WARMUPS; r < runs; r++) {
            long t0 = System.nanoTime();
            long transformed = 0;
            try (BufferedReader br = openTsv(PEOPLE_TSV);
                 BufferedWriter bw = new BufferedWriter(
                     new OutputStreamWriter(new FileOutputStream("results/people_updated.tsv"),
                         StandardCharsets.UTF_8), 1 << 20)) {
                String line;
                while ((line = br.readLine()) != null) {
                    int t1 = line.indexOf('\t');
                    int t2 = line.indexOf('\t', t1 + 1);
                    int t3 = line.indexOf('\t', t2 + 1);
                    // 只改 first_name(t1..t2) 和 surname(t2..t3),其余字段不含 T
                    String fn = line.substring(t1 + 1, t2).replace("T", "Ttt");
                    String sn = line.substring(t2 + 1, t3).replace("T", "Ttt");
                    bw.write(line.substring(0, t1 + 1));
                    bw.write(fn); bw.write('\t');
                    bw.write(sn);
                    bw.write(line.substring(t3));
                    bw.write('\n');
                    transformed++;
                }
            }
            long t1 = System.nanoTime();
            if (r >= 0) nanos[r] = t1 - t0;
        }
        return nanos;
    }

    // 数据库侧:UPDATE ... REPLACE,跑完 rollback,保证可重复测量
    static long[] timeDbUpdate(Connection c, String sql, int runs) throws SQLException {
        long[] nanos = new long[runs];
        for (int r = -WARMUPS; r < runs; r++) {
            c.setAutoCommit(false);
            long t0 = System.nanoTime();
            try (Statement s = c.createStatement()) {
                s.executeUpdate(sql);
            }
            long t1 = System.nanoTime();
            c.rollback();
            c.setAutoCommit(true);
            if (r >= 0) nanos[r] = t1 - t0;
        }
        return nanos;
    }

    static void e2Update(Connection pg, Connection og) throws Exception {
        hr("E2 更新对比:文件改写 vs DB(PostgreSQL / openGauss)");
        String upd = "UPDATE people SET first_name = REPLACE(first_name,'T','Ttt'), "
                   + "surname = REPLACE(surname,'T','Ttt')";
        long[] f = timeFileUpdate(RUNS);
        long[] p = timeDbUpdate(pg, upd, RUNS);
        long[] o = timeDbUpdate(og, upd, RUNS);
        System.out.println("文件改写    " + ms(f) + " ms");
        System.out.println("PostgreSQL  " + ms(p) + " ms");
        System.out.println("openGauss   " + ms(o) + " ms");
    }

    // 实验三:索引对比
    static void explain(Connection c, String sql, String label) throws SQLException {
        System.out.println("  -- " + label + " 的查询计划 --");
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("EXPLAIN " + sql)) {
            while (rs.next()) System.out.println("     " + rs.getString(1));
        }
    }

    static void e3Index(Connection pg, Connection og, boolean verbose) throws Exception {
        hr("E3 索引对比:为什么 LIKE '%word%' 普通 B-tree 索引失效");
        String like = "SELECT count(*) FROM movies WHERE title ILIKE '%love%'";
        String title;

        // 取一个真实存在的标题,用于精确匹配(B-tree 能加速)
        try (Statement s = pg.createStatement();
             ResultSet rs = s.executeQuery("SELECT title FROM movies WHERE movieid = 500000")) {
            rs.next(); title = rs.getString(1);
        }
        String exact = "SELECT count(*) FROM movies WHERE title = '" + title.replace("'", "''") + "'";

        System.out.println("[PostgreSQL]");
        try (Statement s = pg.createStatement()) {
            s.execute("DROP INDEX IF EXISTS idx_movies_title_btree");
            s.execute("DROP INDEX IF EXISTS idx_movies_title_gin");
        }
        System.out.println("无索引     " + ms(timeDbQuery(pg, like, RUNS)) + " ms");
        if (verbose) explain(pg, like, "无索引");
        try (Statement s = pg.createStatement()) {
            s.execute("CREATE INDEX idx_movies_title_btree ON movies(title)");
        }
        System.out.println("B-tree     " + ms(timeDbQuery(pg, like, RUNS)) + " ms");
        if (verbose) explain(pg, like, "B-tree");
        System.out.println("B-tree 精确 " + ms(timeDbQuery(pg, exact, RUNS)) + " ms");
        if (verbose) explain(pg, exact, "B-tree 精确匹配");

        // GIN 三元组索引(PG 专有,pg_trgm)
        try (Statement s = pg.createStatement()) {
            s.execute("DROP INDEX IF EXISTS idx_movies_title_btree");
            try { s.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm"); }
            catch (SQLException e) { System.out.println("  (pg_trgm 扩展不可用:" + e.getMessage() + ")"); }
            s.execute("CREATE INDEX idx_movies_title_gin ON movies USING gin (title gin_trgm_ops)");
        }
        System.out.println("GIN 三元组 " + ms(timeDbQuery(pg, like, RUNS)) + " ms");
        if (verbose) explain(pg, like, "GIN 三元组");
        try (Statement s = pg.createStatement()) { s.execute("DROP INDEX IF EXISTS idx_movies_title_gin"); }

        System.out.println();
        System.out.println("[openGauss]");
        try (Statement s = og.createStatement()) { s.execute("DROP INDEX IF EXISTS idx_movies_title_btree"); }
        System.out.println("无索引     " + ms(timeDbQuery(og, like, RUNS)) + " ms");
        try (Statement s = og.createStatement()) { s.execute("CREATE INDEX idx_movies_title_btree ON movies(title)"); }
        System.out.println("B-tree     " + ms(timeDbQuery(og, like, RUNS)) + " ms");
        if (verbose) explain(og, like, "openGauss B-tree");
        System.out.println("B-tree 精确 " + ms(timeDbQuery(og, exact, RUNS)) + " ms");
        try (Statement s = og.createStatement()) { s.execute("DROP INDEX IF EXISTS idx_movies_title_btree"); }
    }

    // 实验三b:文件侧手工倒排索引
    // 建「词 -> movieid列表」倒排表,同一部电影同一个词只记一次
    static Map<String, List<Integer>> buildInvertedIndex(int maxLines) throws IOException {
        Map<String, List<Integer>> idx = new HashMap<>(1_500_000);
        int id = 0;
        try (BufferedReader br = openTsv(MOVIES_TSV)) {
            String line;
            while ((line = br.readLine()) != null && id < maxLines) {
                String title = titleOf(line).toLowerCase();
                for (String w : title.split("[^a-z0-9]+")) {
                    if (w.isEmpty()) continue;
                    List<Integer> post = idx.get(w);
                    if (post == null) {
                        idx.put(w, new ArrayList<>(Collections.singletonList(id)));
                    } else if (post.get(post.size() - 1) != id) {
                        post.add(id);   // 同一电影内同一词去重
                    }
                }
                id++;
            }
        }
        return idx;
    }

    // 查倒排表:命中则返回 posting list 长度
    static long[] timeInvertedLookup(Map<String, List<Integer>> idx, String word, int runs) {
        long[] nanos = new long[runs];
        for (int r = -WARMUPS; r < runs; r++) {
            long t0 = System.nanoTime();
            List<Integer> post = idx.get(word);
            int hits = post == null ? 0 : post.size();
            long t1 = System.nanoTime();
            if (r >= 0) nanos[r] = t1 - t0;
        }
        return nanos;
    }

    static void e3bSmartFile(Connection pg) throws Exception {
        hr("E3b 聪明文件对照:手工倒排索引(文件侧) vs 数据库索引");
        String word = "love";
        String like = "SELECT count(*) FROM movies WHERE title ILIKE '%love%'";

        // 1. 构建手工倒排索引(一次性)
        long t0 = System.nanoTime();
        Map<String, List<Integer>> idx = buildInvertedIndex(Integer.MAX_VALUE);
        long buildMs = (System.nanoTime() - t0) / 1_000_000;

        // 2. 手工倒排查询(整词)
        long[] inv = timeInvertedLookup(idx, word, RUNS);
        int invHits = idx.get(word) == null ? 0 : idx.get(word).size();

        // 3. 文件线性扫描(子串)
        long[] f = timeFileScan(word, Integer.MAX_VALUE, RUNS);

        // 4. DB 全表扫描(先确保无索引)
        try (Statement s = pg.createStatement()) { s.execute("DROP INDEX IF EXISTS idx_movies_title_gin"); }
        long[] seq = timeDbQuery(pg, like, RUNS);
        long dbHits = countQuery(pg, like);

        // 5. DB GIN 三元组索引(子串)
        try (Statement s = pg.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            s.execute("CREATE INDEX idx_movies_title_gin ON movies USING gin (title gin_trgm_ops)");
        }
        long[] gin = timeDbQuery(pg, like, RUNS);

        double invMs = stats(inv).median;
        String invStr = invMs >= 0.5 ? String.format("%.2f ms", invMs)
                                     : String.format("%.2f 微秒", invMs * 1000);

        System.out.println("构建倒排索引   " + buildMs + " ms");
        System.out.println("文件线性扫描   " + ms(f) + " ms  命中 " + dbHits);
        System.out.println("DB 全表扫描    " + ms(seq) + " ms  命中 " + dbHits);
        System.out.println("DB GIN 三元组  " + ms(gin) + " ms  命中 " + dbHits);
        System.out.println("手工倒排(整词) " + invStr + "  命中 " + invHits);
        try (Statement s = pg.createStatement()) { s.execute("DROP INDEX IF EXISTS idx_movies_title_gin"); }
    }

    // 实验四:规模缩放
    static void e4Scale(Connection pg) throws Exception {
        hr("E4 规模缩放:时间 vs 数据量 N(文件扫描 / DB 全表扫描 / DB GIN 索引)");
        String like = "SELECT count(*) FROM %s WHERE title ILIKE '%%love%%'";
        System.out.printf("%-9s | %-14s | %-14s | %-14s%n", "数据量 N", "文件扫描", "DB 全表扫描", "DB GIN 索引");
        System.out.println("-------------------------------------------------------------------");
        for (int n : SIZES) {
            long[] f = timeFileScan("love", n, RUNS);
            String tbl = "movies_" + n;
            try (Statement s = pg.createStatement()) {
                s.execute("DROP TABLE IF EXISTS " + tbl);
                s.execute("CREATE TABLE " + tbl + " AS SELECT * FROM movies WHERE movieid <= " + n);
            }
            long[] seq = timeDbQuery(pg, String.format(like, tbl), RUNS);
            try (Statement s = pg.createStatement()) {
                s.execute("CREATE INDEX " + tbl + "_gin ON " + tbl + " USING gin (title gin_trgm_ops)");
            }
            long[] idx = timeDbQuery(pg, String.format(like, tbl), RUNS);
            System.out.printf("%,-9d | %9s | %10s | %9s%n",
                    n, ms(f), ms(seq), ms(idx));
        }
    }

    // 实验五:事务/完整性演示
    static void e5Acid(Connection pg) throws Exception {
        hr("E5 ACID / 完整性演示(文件做不到的能力)");
        System.out.println("[原子性] 事务内 UPDATE 后 ROLLBACK,数据不变:");
        long before = countQuery(pg, "SELECT runtime FROM movies WHERE movieid = 1");
        pg.setAutoCommit(false);
        try (Statement s = pg.createStatement()) { s.executeUpdate("UPDATE movies SET runtime = 0 WHERE movieid = 1"); }
        pg.rollback();
        pg.setAutoCommit(true);
        long after = countQuery(pg, "SELECT runtime FROM movies WHERE movieid = 1");
        System.out.println("  UPDATE 前 runtime=" + before + " ,ROLLBACK 后 runtime=" + after + " (未丢失)");

        System.out.println("[完整性] 违反主键约束的 INSERT 被拒绝:");
        try (Statement s = pg.createStatement()) {
            s.executeUpdate("INSERT INTO movies(movieid,title,country,year_released) VALUES(1,'dup','us',2000)");
            System.out.println("  (未触发约束)");
        } catch (SQLException e) {
            System.out.println("  拒绝原因: " + e.getMessage().split("\\n")[0]);
        }

        System.out.println("[MVCC 快照隔离] 事务未提交的修改对其他连接不可见:");
        try (Connection c2 = connect(PG_URL, PG_USER, PG_PASS)) {
            // 先复位为已知值,保证实验可重复、对比明显
            try (Statement s = pg.createStatement()) { s.executeUpdate("UPDATE movies SET runtime = 500 WHERE movieid = 2"); }
            pg.setAutoCommit(false);
            try (Statement s = pg.createStatement()) { s.executeUpdate("UPDATE movies SET runtime = 999 WHERE movieid = 2"); }
            long seen = countQuery(c2, "SELECT runtime FROM movies WHERE movieid = 2");
            System.out.println("  连接 A 已 UPDATE(runtime=999)但未提交,连接 B 读到 runtime=" + seen + " (旧值,隔离)");
            pg.commit();
            pg.setAutoCommit(true);
            long seen2 = countQuery(c2, "SELECT runtime FROM movies WHERE movieid = 2");
            System.out.println("  连接 A 提交后,连接 B 读到 runtime=" + seen2 + " (新值)");
            // 复位为原始值,避免污染数据
            try (Statement s = pg.createStatement()) { s.executeUpdate("UPDATE movies SET runtime = 102 WHERE movieid = 2"); }
        }
    }

    // 实验六:两库汇总对比
    static void e6Summary(Connection pg, Connection og) throws Exception {
        hr("E6 PostgreSQL vs openGauss 汇总(同负载、同数据、同客户端)");
        System.out.printf("%-28s | %-14s | %-14s%n", "负载", "PostgreSQL", "openGauss");
        System.out.println("-------------------------------------------------------------------");
        String[] queries = {
            "检索 全表扫描(LIKE '%love%')",  "SELECT count(*) FROM movies WHERE title ILIKE '%love%'",
            "检索 全表扫描(LIKE '%the%')",   "SELECT count(*) FROM movies WHERE title ILIKE '%the%'",
        };
        for (int i = 0; i < queries.length; i += 2) {
            long[] p = timeDbQuery(pg, queries[i + 1], RUNS);
            long[] o = timeDbQuery(og, queries[i + 1], RUNS);
            System.out.printf("%-28s | %9s | %9s%n", queries[i], ms(p), ms(o));
        }
        String upd = "UPDATE people SET first_name = REPLACE(first_name,'T','Ttt'), surname = REPLACE(surname,'T','Ttt')";
        long[] pu = timeDbUpdate(pg, upd, RUNS);
        long[] ou = timeDbUpdate(og, upd, RUNS);
        System.out.printf("%-28s | %9s | %9s%n", "更新(50万行 REPLACE)", ms(pu), ms(ou));
    }

    // 实验六b:高并发吞吐
    static final int[] CONCURRENCY = {1, 8, 32, 64, 128};
    static final int PER_THREAD  = 5000;

    // n 个线程并发点查,返回 qps
    static double sweepQps(String url, String user, String pass, int n) throws Exception {
        AtomicLong done = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n), go = new CountDownLatch(1), finish = new CountDownLatch(n);
        for (int t = 0; t < n; t++) {
            final int tIdx = t;
            pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(url, user, pass);
                     PreparedStatement ps = c.prepareStatement("SELECT title FROM movies WHERE movieid = ?")) {
                    ready.countDown();
                    go.await();
                    for (int k = 0; k < PER_THREAD; k++) {
                        ps.setInt(1, 1 + ((tIdx * 7919 + k * 104729) % 1000000));
                        try (ResultSet r = ps.executeQuery()) { done.incrementAndGet(); }
                    }
                } catch (Exception e) {
                    System.out.println("  (err " + e.getMessage() + ")");
                } finally { finish.countDown(); }
            });
        }
        ready.await();
        long t0 = System.nanoTime();
        go.countDown();
        finish.await();
        double sec = (System.nanoTime() - t0) / 1e9;
        pool.shutdown();
        return done.get() / sec;
    }

    static void e6bThroughput(Connection pg, Connection og) throws Exception {
        hr("E6b 高并发吞吐:openGauss 线程池 vs PostgreSQL 进程模型");
        System.out.println("负载:SELECT title FROM movies WHERE movieid = ?(走主键索引),每线程 " + PER_THREAD + " 次;");
        System.out.println("openGauss 已 enable_thread_pool = on;两库 max_connections = 300。");
        System.out.printf("%-10s | %-16s | %-16s%n", "并发数", "PostgreSQL(qps)", "openGauss(qps)");
        System.out.println("----------------------------------------------------------");
        for (int n : CONCURRENCY) {
            double p = sweepQps(PG_URL, PG_USER, PG_PASS, n);
            double o = sweepQps(OG_URL, OG_USER, OG_PASS, n);
            System.out.printf("%-10d | %-16.0f | %-16.0f%n", n, p, o);
        }
    }

    // ---- 补充:openGauss 动态数据脱敏(企业级安全,非性能,仅供参考) ----
    static void e6bMasking(Connection og) throws Exception {
        hr("E6b openGauss 动态数据脱敏(PostgreSQL 不具备)");
        String[] setup = {
            "CREATE USER masked_user PASSWORD 'Mask@123'",
            "GRANT CONNECT ON DATABASE postgres TO masked_user",
            "GRANT USAGE ON SCHEMA public TO masked_user",
            "GRANT SELECT ON movies TO masked_user",
            "CREATE RESOURCE LABEL rl_title ADD COLUMN(public.movies.title)",
            "CREATE MASKING POLICY mask_title_all maskall ON LABEL(rl_title) FILTER ON ROLES(masked_user)",
        };
        for (String s : setup) {
            try (Statement st = og.createStatement()) { st.execute(s); }
            catch (SQLException e) { System.out.println("  跳过(已存在): " + e.getMessage().split("\\n")[0]); }
        }
        System.out.println("[超级用户 gaussdb] 看到真实 title:");
        try (Statement st = og.createStatement();
             ResultSet r = st.executeQuery("SELECT movieid,title FROM movies WHERE movieid<=3")) {
            while (r.next()) System.out.println("  " + r.getInt(1) + " | " + r.getString(2));
        }
        System.out.println("[受限用户 masked_user] 同一查询看到脱敏 title:");
        try (Connection cu = DriverManager.getConnection(OG_URL, "masked_user", "Mask@123");
             Statement st = cu.createStatement();
             ResultSet r = st.executeQuery("SELECT movieid,title FROM movies WHERE movieid<=3")) {
            while (r.next()) System.out.println("  " + r.getInt(1) + " | " + r.getString(2));
        }
        System.out.println("  => 数据库在返回结果前按策略把敏感列自动替换为 x...(PostgreSQL 无 MASKING POLICY 语法)");
    }

    // 入口
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        new File("results").mkdirs();
        Class.forName("org.postgresql.Driver");

        if (args.length > 0) {
            switch (args[0]) {
                case "--env":
                    try (Connection pg = connect(PG_URL, PG_USER, PG_PASS);
                         Connection og = connect(OG_URL, OG_USER, OG_PASS)) {
                        System.out.println("DBMS 性能评测 —— 实验开始");
                        System.out.println("数据:movies 100 万行,people 50 万行(电影数据,来自 filmdb.sql 放大)");
                        System.out.println("计时方法:预热 " + WARMUPS + " 次 + 运行 " + RUNS + " 次,报中位数 ± 标准差(热缓存)");
                        try (Statement s = pg.createStatement(); ResultSet r = s.executeQuery("SELECT version()")) {
                            r.next(); System.out.println("PostgreSQL: " + r.getString(1));
                        }
                        try (Statement s = og.createStatement(); ResultSet r = s.executeQuery("SELECT version()")) {
                            r.next(); System.out.println("openGauss : " + r.getString(1));
                        }
                    }
                    return;
                case "--e3":
                    try (Connection pg = connect(PG_URL, PG_USER, PG_PASS);
                         Connection og = connect(OG_URL, OG_USER, OG_PASS)) { e3Index(pg, og, true); }
                    return;
                case "--e5":
                    try (Connection pg = connect(PG_URL, PG_USER, PG_PASS)) { e5Acid(pg); }
                    return;
                case "--masking":
                    try (Connection og = connect(OG_URL, OG_USER, OG_PASS)) { e6bMasking(og); }
                    return;
                case "--throughput":
                    try (Connection pg = connect(PG_URL, PG_USER, PG_PASS);
                         Connection og = connect(OG_URL, OG_USER, OG_PASS)) { e6bThroughput(pg, og); }
                    return;
            }
        }

        System.out.println("DBMS 性能评测 —— 实验开始");
        System.out.println("数据:movies 100 万行,people 50 万行(电影数据,来自 filmdb.sql 放大)");
        System.out.println("计时方法:预热 " + WARMUPS + " 次 + 运行 " + RUNS + " 次,报中位数 ± 标准差(热缓存)");

        try (Connection pg = connect(PG_URL, PG_USER, PG_PASS);
             Connection og = connect(OG_URL, OG_USER, OG_PASS)) {

            try (Statement s = pg.createStatement(); ResultSet r = s.executeQuery("SELECT version()")) {
                r.next(); System.out.println("PostgreSQL: " + r.getString(1));
            }
            try (Statement s = og.createStatement(); ResultSet r = s.executeQuery("SELECT version()")) {
                r.next(); System.out.println("openGauss : " + r.getString(1));
            }

            try { e1Retrieval(pg, og); } catch (Exception e) { System.out.println("E1 失败:" + e); }
            try { e2Update(pg, og); }      catch (Exception e) { System.out.println("E2 失败:" + e); }
            try { e3Index(pg, og, false); }      catch (Exception e) { System.out.println("E3 失败:" + e); }
            try { e3bSmartFile(pg); }      catch (Exception e) { System.out.println("E3b 失败:" + e); }
            try { e4Scale(pg); }           catch (Exception e) { System.out.println("E4 失败:" + e); }
            try { e5Acid(pg); }            catch (Exception e) { System.out.println("E5 失败:" + e); }
            try { e6Summary(pg, og); }     catch (Exception e) { System.out.println("E6 失败:" + e); }
            try { e6bThroughput(pg, og); } catch (Exception e) { System.out.println("E6b 失败:" + e); }
        }
        System.out.println();
        System.out.println("实验结束。");
    }
}
