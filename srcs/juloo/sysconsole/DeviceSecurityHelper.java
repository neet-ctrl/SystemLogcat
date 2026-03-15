package juloo.sysconsole;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects elevated-privilege apps on the device:
 *
 *  - Device administrator apps (can wipe/lock device remotely)
 *  - VPN apps (can intercept all network traffic)
 *  - Notification listener apps (can read all notifications)
 *  - Accessibility service apps (can read/control the screen)
 *  - Apps with BIND_DEVICE_ADMIN or similar high-privilege permissions
 *
 * Uses Shizuku where available for deeper access; falls back gracefully.
 */
public class DeviceSecurityHelper {

    public static class ElevatedApp {
        public String packageName;
        public String appName;
        public String privilege;
        public String description;
        public int    riskColor;

        public ElevatedApp(String pkg, String name, String priv, String desc, int color) {
            this.packageName = pkg;
            this.appName     = name;
            this.privilege   = priv;
            this.description = desc;
            this.riskColor   = color;
        }
    }

    /**
     * Get all elevated-privilege apps using available methods.
     */
    public static List<ElevatedApp> getElevatedApps(Context ctx) {
        List<ElevatedApp> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        getDeviceAdmins(ctx, result, seen);
        getAccessibilityApps(ctx, result, seen);
        getNotificationListeners(ctx, result, seen);
        getVpnApps(ctx, result, seen);

        // Augment with Shizuku if available
        if (ShizukuCommandHelper.isAvailable()) {
            augmentWithShizuku(ctx, result, seen);
        }

        return result;
    }

    private static void getDeviceAdmins(Context ctx, List<ElevatedApp> out, Set<String> seen) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) return;

            List<ComponentName> admins = dpm.getActiveAdmins();
            if (admins == null) return;

            PackageManager pm = ctx.getPackageManager();
            for (ComponentName cn : admins) {
                String pkg  = cn.getPackageName();
                String name = getAppName(pm, pkg);
                if (seen.add(pkg + ":admin")) {
                    out.add(new ElevatedApp(pkg, name,
                            "Device Administrator",
                            "Can remotely wipe, lock, or control this device",
                            0xFFE63946));
                }
            }
        } catch (Exception ignored) {}
    }

    private static void getAccessibilityApps(Context ctx, List<ElevatedApp> out,
                                              Set<String> seen) {
        try {
            String enabled = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.isEmpty()) return;

            PackageManager pm = ctx.getPackageManager();
            for (String entry : enabled.split(":")) {
                String[] parts = entry.split("/");
                if (parts.length < 1) continue;
                String pkg  = parts[0].trim();
                if (pkg.isEmpty() || pkg.startsWith("com.android") || pkg.startsWith("android")) {
                    continue; // skip built-in services
                }
                String name = getAppName(pm, pkg);
                if (seen.add(pkg + ":accessibility")) {
                    out.add(new ElevatedApp(pkg, name,
                            "Accessibility Service",
                            "Can read screen content, simulate taps, and intercept input",
                            0xFFFF9F1C));
                }
            }
        } catch (Exception ignored) {}
    }

    private static void getNotificationListeners(Context ctx, List<ElevatedApp> out,
                                                   Set<String> seen) {
        try {
            String flat = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    "enabled_notification_listeners");
            if (flat == null || flat.isEmpty()) return;

            PackageManager pm = ctx.getPackageManager();
            for (String entry : flat.split(":")) {
                String[] parts = entry.split("/");
                if (parts.length < 1) continue;
                String pkg = parts[0].trim();
                if (pkg.isEmpty() || pkg.startsWith("com.android") || pkg.startsWith("android")) {
                    continue;
                }
                String name = getAppName(pm, pkg);
                if (seen.add(pkg + ":notif")) {
                    out.add(new ElevatedApp(pkg, name,
                            "Notification Listener",
                            "Can read all notifications from every app on this device",
                            0xFFFF9F1C));
                }
            }
        } catch (Exception ignored) {}
    }

    private static void getVpnApps(Context ctx, List<ElevatedApp> out, Set<String> seen) {
        try {
            PackageManager pm = ctx.getPackageManager();
            List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            for (PackageInfo pi : pkgs) {
                if (pi.requestedPermissions == null) continue;
                for (String perm : pi.requestedPermissions) {
                    if ("android.permission.BIND_VPN_SERVICE".equals(perm)) {
                        String pkg  = pi.packageName;
                        String name = getAppName(pm, pkg);
                        if (seen.add(pkg + ":vpn")) {
                            out.add(new ElevatedApp(pkg, name,
                                    "VPN Service",
                                    "Can intercept and inspect all network traffic on this device",
                                    0xFFFF9F1C));
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void augmentWithShizuku(Context ctx, List<ElevatedApp> out,
                                            Set<String> seen) {
        PackageManager pm = ctx.getPackageManager();
        try {
            // Device admins via device_policy dump
            String policyDump = ShizukuCommandHelper.dumpDevicePolicy();
            List<String> adminPkgs = ShizukuCommandHelper.parseDeviceAdminPackages(policyDump);
            for (String pkg : adminPkgs) {
                String name = getAppName(pm, pkg);
                if (seen.add(pkg + ":admin")) {
                    out.add(new ElevatedApp(pkg, name,
                            "Device Administrator (Shizuku)",
                            "Can remotely wipe, lock, or control this device",
                            0xFFE63946));
                }
            }
        } catch (Exception ignored) {}

        try {
            // Notification listeners via notification dump
            String notifDump = ShizukuCommandHelper.dumpNotificationPolicy();
            List<String> notifPkgs = ShizukuCommandHelper.parseNotificationListeners(notifDump);
            for (String pkg : notifPkgs) {
                String name = getAppName(pm, pkg);
                if (seen.add(pkg + ":notif")) {
                    out.add(new ElevatedApp(pkg, name,
                            "Notification Listener (Shizuku)",
                            "Can read all your notifications",
                            0xFFFF9F1C));
                }
            }
        } catch (Exception ignored) {}

        try {
            // Accessibility via accessibility dump
            String accessDump = ShizukuCommandHelper.dumpAccessibility();
            List<String> accessPkgs = ShizukuCommandHelper.parseAccessibilityPackages(accessDump);
            for (String pkg : accessPkgs) {
                if (pkg.startsWith("com.android") || pkg.startsWith("android")) continue;
                String name = getAppName(pm, pkg);
                if (seen.add(pkg + ":accessibility")) {
                    out.add(new ElevatedApp(pkg, name,
                            "Accessibility Service (Shizuku)",
                            "Can read screen content and simulate input",
                            0xFFFF9F1C));
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getAppName(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return (String) pm.getApplicationLabel(ai);
        } catch (Exception e) {
            return pkg;
        }
    }
}
