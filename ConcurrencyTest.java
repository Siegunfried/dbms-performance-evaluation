import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConcurrencyTest {
    static final int[] THREADS = {1, 8, 32, 64, 128};
    static final int PER_THREAD = 5000;

    static double sweep(String url, String user, String pass, String label) throws Exception {
        Class.forName("org.postgresql.Driver");
        // 预热连接
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement p = c.prepareStatement("SELECT title FROM movies WHERE movieid = ?"))
        { p.setInt(1, 1); p.executeQuery().close(); }

        System.out.println("[" + label + "]");
        for (int n : THREADS) {
            AtomicLong done = new AtomicLong();
            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(n);
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
                        System.out.println("    err " + e.getMessage());
                    } finally { finish.countDown(); }
                });
            }
            ready.await();
            long t0 = System.nanoTime();
            go.countDown();
            finish.await();
            double sec = (System.nanoTime() - t0) / 1e9;
            pool.shutdown();
            System.out.printf("  threads=%3d  qps=%8.1f  (avg latency %.3f ms)%n",
                    n, done.get() / sec, sec / done.get() * 1000.0);
        }
        return 0;
    }

    public static void main(String[] a) throws Exception {
        if (a.length == 1 && a[0].equals("both")) {
            sweep("jdbc:postgresql://127.0.0.1:5432/dbperf", "postgres", "postgres", "PostgreSQL");
            sweep("jdbc:postgresql://127.0.0.1:5433/postgres", "gaussdb", "Gaussdb@2026", "openGauss");
            return;
        }
        sweep(a[0], a[1], a[2], a[3]);
    }
}