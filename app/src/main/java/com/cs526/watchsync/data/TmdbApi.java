package com.cs526.watchsync.data;

import android.content.Context;
import android.net.Uri;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.cs526.watchsync.BuildConfig;
import com.cs526.watchsync.model.Show;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The Movie Database (TMDB): search, episode counts, episode names and posters. */
public class TmdbApi {
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_URL = "https://image.tmdb.org/t/p/w342";
    private static final int GENRE_ANIMATION = 16;

    private static RequestQueue queue;
    private final String apiKey = BuildConfig.TMDB_API_KEY;

    public interface Callback<T> {
        void onResult(T result);
        void onError(String message);
    }

    public TmdbApi(Context context) {
        if (queue == null) queue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public static String posterUrl(String posterPath) {
        if (posterPath == null || posterPath.isEmpty()) return null;
        return IMAGE_URL + posterPath;
    }

    /** Movies + TV shows + anime by title. */
    public void search(String query, Callback<List<Show>> callback) {
        String url = BASE_URL + "/search/multi?include_adult=false&api_key=" + apiKey
                + "&query=" + Uri.encode(query);
        queue.add(new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            List<Show> list = new ArrayList<>();
            JSONArray results = response.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.optJSONObject(i);
                    if (item == null) continue;
                    String type = item.optString("media_type");
                    if (!type.equals("tv") && !type.equals("movie")) continue; // skip people
                    list.add(parse(item, type));
                }
            }
            callback.onResult(list);
        }, error -> callback.onError(errorText(error))));
    }

    /** Fills seasonCounts + totalEpisodes (+ overview) for a TV show. Movies = 1 episode. */
    public void loadDetails(Show show, Callback<Show> callback) {
        if (show.movie() || show.tmdbId <= 0) {
            if (show.movie()) show.totalEpisodes = 1;
            callback.onResult(show);
            return;
        }
        String url = BASE_URL + "/tv/" + show.tmdbId + "?api_key=" + apiKey;
        queue.add(new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            StringBuilder counts = new StringBuilder();
            int total = 0;
            JSONArray seasons = response.optJSONArray("seasons");
            if (seasons != null) {
                for (int i = 0; i < seasons.length(); i++) {
                    JSONObject season = seasons.optJSONObject(i);
                    if (season == null) continue;
                    int number = season.optInt("season_number", 0);
                    int eps = season.optInt("episode_count", 0);
                    if (number <= 0 || eps <= 0) continue; // skip "Specials" (season 0)
                    if (counts.length() > 0) counts.append(",");
                    counts.append(eps);
                    total += eps;
                }
            }
            if (total == 0) total = response.optInt("number_of_episodes", 0);
            show.seasonCounts = counts.toString();
            show.totalEpisodes = total;
            if (show.overview.isEmpty()) show.overview = text(response, "overview");
            callback.onResult(show);
        }, error -> callback.onError(errorText(error))));
    }

    /** Episode names of one season (season is 1-based). */
    public void loadEpisodeNames(int tmdbId, int season, Callback<List<String>> callback) {
        String url = BASE_URL + "/tv/" + tmdbId + "/season/" + season + "?api_key=" + apiKey;
        queue.add(new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            List<String> names = new ArrayList<>();
            JSONArray eps = response.optJSONArray("episodes");
            if (eps != null) {
                for (int i = 0; i < eps.length(); i++) {
                    JSONObject e = eps.optJSONObject(i);
                    names.add(e == null ? "" : text(e, "name"));
                }
            }
            callback.onResult(names);
        }, error -> callback.onError(errorText(error))));
    }

    private Show parse(JSONObject item, String type) {
        Show s = new Show();
        boolean movie = type.equals("movie");
        s.tmdbId = item.optInt("id");
        s.mediaType = type;
        s.title = text(item, movie ? "title" : "name");
        String date = text(item, movie ? "release_date" : "first_air_date");
        s.year = date.length() >= 4 ? date.substring(0, 4) : "";
        s.posterPath = text(item, "poster_path");
        s.overview = text(item, "overview");
        s.anime = !movie && hasGenre(item, GENRE_ANIMATION) && text(item, "original_language").equals("ja");
        if (movie) s.totalEpisodes = 1;
        return s;
    }

    /** optString() returns the word "null" for JSON nulls, so check first. */
    private static String text(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private static boolean hasGenre(JSONObject item, int id) {
        JSONArray g = item.optJSONArray("genre_ids");
        if (g == null) return false;
        for (int i = 0; i < g.length(); i++) if (g.optInt(i) == id) return true;
        return false;
    }

    private static String errorText(Exception e) {
        if (e instanceof com.android.volley.VolleyError) {
            com.android.volley.NetworkResponse r = ((com.android.volley.VolleyError) e).networkResponse;
            if (r != null && r.statusCode == 401) return "TMDB key is missing or wrong (check secrets.properties)";
        }
        return "Couldn't reach TMDB. Check your internet.";
    }
}
