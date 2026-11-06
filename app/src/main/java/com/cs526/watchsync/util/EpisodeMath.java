package com.cs526.watchsync.util;

/**
 * Episodes are counted as ONE running number across all seasons (1, 2, 3 ... total).
 * seasonCounts keeps how many episodes each season has, e.g. "24,23" = S1 has 24, S2 has 23.
 * That makes spoiler math simple: "behind" = one number minus another.
 */
public final class EpisodeMath {
    private EpisodeMath() {}

    public static int[] counts(String seasonCounts) {
        if (seasonCounts == null || seasonCounts.trim().isEmpty()) return new int[0];
        String[] parts = seasonCounts.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i].trim()); } catch (Exception e) { result[i] = 0; }
        }
        return result;
    }

    /** 14 with "12,12" -> "S2 E2". */
    public static String label(int episode, String seasonCounts, boolean movie) {
        if (movie) return "Movie";
        if (episode <= 0) return "Not started";
        int[] c = counts(seasonCounts);
        int left = episode;
        for (int i = 0; i < c.length; i++) {
            if (left <= c[i]) return "S" + (i + 1) + " E" + left;
            left -= c[i];
        }
        return "Episode " + episode;
    }

    /** Season 2 episode 3 with "12,12" -> 15. season is 1-based. */
    public static int toNumber(int season, int episodeInSeason, String seasonCounts) {
        int[] c = counts(seasonCounts);
        int sum = 0;
        for (int i = 0; i < season - 1 && i < c.length; i++) sum += c[i];
        return sum + episodeInSeason;
    }

    /** Which season (1-based) an episode number falls in. */
    public static int seasonOf(int episode, String seasonCounts) {
        int[] c = counts(seasonCounts);
        int left = episode;
        for (int i = 0; i < c.length; i++) {
            if (left <= c[i]) return i + 1;
            left -= c[i];
        }
        return Math.max(1, c.length);
    }

    /** "2 episodes" / "1 episode". */
    public static String count(int n) {
        return n + (n == 1 ? " episode" : " episodes");
    }
}
