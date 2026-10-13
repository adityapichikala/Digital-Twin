package com.twindigital.simulation.util;

import java.util.Locale;

public class KubeQuantityParser {

    /**
     * Parses Kubernetes CPU quantity string to millicores.
     * Examples: "500m" -> 500, "2" -> 2000, "1.5" -> 1500, "250000u" -> 250
     */
    public static long parseCpuMillis(String cpu) {
        if (cpu == null || cpu.isBlank()) {
            return 0L;
        }
        String clean = cpu.trim().toLowerCase(Locale.ROOT);
        try {
            if (clean.endsWith("m")) {
                return (long) Double.parseDouble(clean.substring(0, clean.length() - 1));
            } else if (clean.endsWith("u")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) / 1000.0);
            } else if (clean.endsWith("n")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) / 1_000_000.0);
            } else {
                return (long) (Double.parseDouble(clean) * 1000.0);
            }
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Parses Kubernetes Memory quantity string to bytes.
     * Supports binary SI (Ki, Mi, Gi, Ti) and decimal SI (k, M, G, T) or raw numbers.
     */
    public static long parseMemoryBytes(String mem) {
        if (mem == null || mem.isBlank()) {
            return 0L;
        }
        String clean = mem.trim();
        try {
            if (clean.endsWith("Ki")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L);
            } else if (clean.endsWith("Mi")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L);
            } else if (clean.endsWith("Gi")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L * 1024L);
            } else if (clean.endsWith("Ti")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L * 1024L * 1024L);
            } else if (clean.endsWith("k") || clean.endsWith("K")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1000L);
            } else if (clean.endsWith("M")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000L);
            } else if (clean.endsWith("G")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000_000L);
            } else if (clean.endsWith("T")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000_000_000L);
            } else {
                return (long) Double.parseDouble(clean);
            }
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String formatCpu(long millis) {
        if (millis >= 1000 && millis % 1000 == 0) {
            return (millis / 1000) + " cores";
        }
        return millis + "m";
    }

    public static String formatMemory(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            double gi = bytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.ROOT, "%.1fGi", gi);
        } else if (bytes >= 1024L * 1024L) {
            double mi = bytes / (1024.0 * 1024.0);
            return String.format(Locale.ROOT, "%.0fMi", mi);
        }
        return bytes + " bytes";
    }
}
