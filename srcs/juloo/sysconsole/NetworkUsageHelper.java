package juloo.sysconsole;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.RemoteException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gets per-app network usage via NetworkStatsManager (API 23+).
 * Falls back to TrafficStats for per-UID data when quota stats
 * aren't available or permission is lacking.
 *
 * Requires android.permission.PACKAGE_USAGE_STATS to be granted
 * (user grants via Settings > Digital Wellbeing, or via Shizuku).
 */
public class NetworkUsageHelper {

    /**
     * Returns a map of packageName → total bytes (sent + received) in the last 7 days.
     * Returns an empty map on failure.
     */
    public static Map<String, Long> getWeeklyNetworkUsage(Context ctx) {
        Map<String, Long> map = new HashMap<>();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return getFallbackUsage(ctx);
        }

        try {
            NetworkStatsManager nsm = (NetworkStatsManager)
                    ctx.getSystemService(Context.NETWORK_STATS_SERVICE);
            if (nsm == null) return getFallbackUsage(ctx);

            long now   = System.currentTimeMillis();
            long start = now - 7L * 24 * 3600 * 1000;

            PackageManager pm = ctx.getPackageManager();

            // Query mobile network
            queryNetworkType(nsm, pm, ConnectivityManager.TYPE_MOBILE, start, now, map);
            // Query WiFi
            queryNetworkType(nsm, pm, ConnectivityManager.TYPE_WIFI, start, now, map);

        } catch (Exception e) {
            return getFallbackUsage(ctx);
        }

        return map;
    }

    private static void queryNetworkType(NetworkStatsManager nsm,
                                          PackageManager pm,
                                          int networkType,
                                          long start, long end,
                                          Map<String, Long> map) {
        try {
            NetworkStats stats = nsm.querySummary(networkType, null, start, end);
            if (stats == null) return;

            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                int uid = bucket.getUid();
                if (uid < 1000) continue; // skip kernel/system UIDs

                long bytes = bucket.getRxBytes() + bucket.getTxBytes();
                if (bytes <= 0) continue;

                // Resolve UID to package name
                String[] pkgs = pm.getPackagesForUid(uid);
                if (pkgs == null) continue;

                for (String pkg : pkgs) {
                    long current = map.containsKey(pkg) ? map.get(pkg) : 0L;
                    map.put(pkg, current + bytes);
                }
            }
            stats.close();
        } catch (Exception ignored) {}
    }

    /**
     * TrafficStats fallback — less accurate but always available.
     * Maps UID → bytes, which we then resolve to package names.
     */
    private static Map<String, Long> getFallbackUsage(Context ctx) {
        Map<String, Long> map = new HashMap<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                long rx = TrafficStats.getUidRxBytes(app.uid);
                long tx = TrafficStats.getUidTxBytes(app.uid);
                if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) continue;
                long total = rx + tx;
                if (total > 0) {
                    String[] pkgs = pm.getPackagesForUid(app.uid);
                    if (pkgs != null) {
                        for (String pkg : pkgs) {
                            long cur = map.containsKey(pkg) ? map.get(pkg) : 0L;
                            map.put(pkg, cur + total);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    /**
     * Format bytes into a human-readable string.
     */
    public static String formatBytes(long bytes) {
        if (bytes <= 0)              return "0 B";
        if (bytes < 1024)            return bytes + " B";
        if (bytes < 1024 * 1024)     return String.format("%.1f KB", bytes / 1024f);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024f * 1024f));
        return String.format("%.2f GB", bytes / (1024f * 1024f * 1024f));
    }
}
