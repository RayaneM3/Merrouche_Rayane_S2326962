package org.me.gcu.merrouche_rayane_s2326962.data;

import java.util.Locale;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Model layer: downloads, caches and parses the two Traffic Scotland feeds.
 *
 * Threading (replacing the starter code's Thread + runOnUiThread):
 *  - a ScheduledExecutorService runs the update at start-up and then at a fixed interval;
 *  - each update downloads and parses both feeds IN PARALLEL on a 2-thread ExecutorService pool;
 *  - results reach the UI through LiveData.postValue(), which delivers them on the main thread
 *    to whatever is observing (the ViewModel/Views), so no worker thread touches a widget.
 *
 * Offline behaviour: every successful download is saved in app-specific internal storage;
 * if the phone is offline (or the download fails) the most recent saved data is shown instead,
 * and a new update is triggered automatically as soon as the connection comes back.
 */
public class TrafficRepository
{
    private static final String TAG = "TrafficRepository";
    private static final int TIMEOUT_MS = 20000;

    private final Context appContext;
    private final Credentials credentials;
    private final ConnectivityManager connectivity;

    private final ExecutorService downloadPool = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> autoUpdate;
    private long intervalMinutes = 60;
    private final AtomicBoolean updating = new AtomicBoolean(false);

    private final MutableLiveData<List<Situation>> situations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<LoadState> state = new MutableLiveData<>(LoadState.initial());
    private volatile LoadState lastState = LoadState.initial();

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback()
    {
        @Override
        public void onAvailable(@NonNull Network network)
        {
            // Connection (re)established: if we were showing saved data, update now
            LoadState.Status s = lastState.status;
            if (s == LoadState.Status.OFFLINE_CACHED || s == LoadState.Status.NO_DATA)
            {
                Log.d(TAG, "Connection restored: updating");
                refreshNow();
            }
        }

        @Override
        public void onLost(@NonNull Network network)
        {
            Log.d(TAG, "Connection lost");
            LoadState.Status s = lastState.status;
            if (s == LoadState.Status.UP_TO_DATE || s == LoadState.Status.ERROR_CACHED)
                publish(lastState.withStatus(LoadState.Status.OFFLINE_CACHED, "Connection lost"));
        }
    };

    public TrafficRepository(Context context, Credentials credentials)
    {
        this.appContext = context.getApplicationContext();
        this.credentials = credentials;
        this.connectivity = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        try
        {
            connectivity.registerDefaultNetworkCallback(networkCallback);
        }
        catch (RuntimeException e) // i.e. missing ACCESS_NETWORK_STATE permission
        {
            Log.e(TAG, "Cannot listen to connectivity changes", e);
        }
    }

    public LiveData<List<Situation>> getSituations()
    {
        return situations;
    }

    public LiveData<LoadState> getState()
    {
        return state;
    }

    /** Starts (or restarts with a new interval) the automatic updates; the first one runs now */
    public synchronized void startAutoUpdate(long minutes)
    {
        intervalMinutes = Math.max(1, minutes);
        if (autoUpdate != null)
            autoUpdate.cancel(false);
        autoUpdate = scheduler.scheduleWithFixedDelay(this::update, 0, intervalMinutes, TimeUnit.MINUTES);
        Log.d(TAG, "Auto-update every " + intervalMinutes + " min");
    }

    /** Manual refresh (menu option / connection restored): runs on the scheduler thread */
    public void refreshNow()
    {
        scheduler.execute(this::update);
    }

    /** Releases the threads and the connectivity listener (called from ViewModel.onCleared) */
    public void shutdown()
    {
        try
        {
            connectivity.unregisterNetworkCallback(networkCallback);
        }
        catch (RuntimeException ignored) { }
        scheduler.shutdownNow();
        downloadPool.shutdownNow();
    }

    // ------------------------------------------------------------------ background work

    /** Most recently started first (situations without a start time go last) */
    private static final java.util.Comparator<Situation> NEWEST_FIRST = (a, b) -> {
        if (a.getStartTime() == null) return b.getStartTime() == null ? 0 : 1;
        if (b.getStartTime() == null) return -1;
        return b.getStartTime().compareTo(a.getStartTime());
    };

    /** One update cycle. Runs on a background thread (never on the UI thread) */
    private void update()
    {
        if (!updating.compareAndSet(false, true))
            return; // an update is already running
        try
        {
            publish(lastState.withStatus(LoadState.Status.LOADING, ""));
            boolean online = isOnline();

            // Both feeds are downloaded and parsed at the same time, in the thread pool
            Future<FeedOutcome> incidents = downloadPool.submit(() -> loadFeed(FeedType.INCIDENT, online));
            Future<FeedOutcome> roadworks = downloadPool.submit(() -> loadFeed(FeedType.ROADWORKS, online));
            FeedOutcome inc = incidents.get();
            FeedOutcome rw = roadworks.get();

            // Unplanned events first, then roadworks; each group newest first
            List<Situation> all = new ArrayList<>(inc.situations);
            Collections.sort(all, NEWEST_FIRST);
            List<Situation> works = new ArrayList<>(rw.situations);
            Collections.sort(works, NEWEST_FIRST);
            all.addAll(works);
            situations.postValue(Collections.unmodifiableList(all)); // delivered on the main thread

            publish(buildState(inc, rw, online));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Update failed", e);
            publish(lastState.withStatus(LoadState.Status.ERROR_CACHED, e.getMessage()));
        }
        finally
        {
            updating.set(false);
        }
    }

    private LoadState buildState(FeedOutcome inc, FeedOutcome rw, boolean online)
    {
        ZonedDateTime next = ZonedDateTime.now().plusMinutes(intervalMinutes);
        ZonedDateTime dataTime = oldest(inc.dataTime, rw.dataTime);
        String error = !inc.error.isEmpty() ? inc.error : rw.error;
        LoadState.Status status;
        boolean bothFresh = inc.dataTime != null && rw.dataTime != null && !inc.fromCache && !rw.fromCache;
        if (inc.dataTime == null && rw.dataTime == null)
            status = LoadState.Status.NO_DATA;
        else if (bothFresh)
            status = LoadState.Status.UP_TO_DATE;
        else if (!online)
            status = LoadState.Status.OFFLINE_CACHED;
        else
            status = LoadState.Status.ERROR_CACHED; // one/both feeds failed: saved or partial data
        return new LoadState(status, error, dataTime, next, inc.situations.size(), rw.situations.size());
    }

    private static ZonedDateTime oldest(ZonedDateTime a, ZonedDateTime b)
    {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private void publish(LoadState s)
    {
        lastState = s;
        state.postValue(s);
    }

    /** Result of loading one feed */
    private static class FeedOutcome
    {
        List<Situation> situations = new ArrayList<>();
        ZonedDateTime dataTime;  // download time of the data used (null = no data)
        boolean fromCache = false;
        String error = "";
    }

    /** Download (or read the saved copy of) one feed and parse it. Runs in the thread pool */
    private FeedOutcome loadFeed(FeedType feed, boolean online)
    {
        FeedOutcome out = new FeedOutcome();
        String xml = null;
        if (!online)
            out.error = "No internet connection";
        else if (!credentials.isConfigured())
            out.error = "Traffic Scotland client ID/key not set in local.properties";
        else
        {
            try
            {
                xml = download(feed);
                out.dataTime = ZonedDateTime.now();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Download failed: " + feed, e);
                out.error = e.getMessage() == null ? "Download failed" : e.getMessage();
            }
        }

        if (xml != null)
        {
            try
            {
                out.situations = new SituationParser().parse(xml, feed).situations;
                saveCache(feed, xml); // only keep data that parsed correctly
                return out;
            }
            catch (Exception e)
            {
                Log.e(TAG, "Parsing failed: " + feed, e);
                out.error = "Data received could not be read";
            }
        }

        // Fall back to the most recent saved data
        File cache = cacheFile(feed);
        if (cache.exists())
        {
            try
            {
                out.situations = new SituationParser().parse(readFile(cache), feed).situations;
                out.dataTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(cache.lastModified()), ZoneId.systemDefault());
                out.fromCache = true;
            }
            catch (Exception e)
            {
                Log.e(TAG, "Saved data unreadable: " + feed, e);
                out.dataTime = null;
            }
        }
        else
        {
            out.dataTime = null;
        }
        return out;
    }

    /** HTTP GET with the client ID/key in the Authorization header (as the starter code) */
    private String download(FeedType feed) throws IOException
    {
        Log.d(TAG, "Downloading " + feed.getUrl());
        HttpURLConnection connection = (HttpURLConnection) new URL(feed.getUrl()).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Authorization", credentials.authorizationHeader());
        // With a wrong client ID/key the server redirects (HTTP 302) to its sign-in web page instead
        // of answering 401, so redirects are not followed: they mean the credentials were refused
        connection.setInstanceFollowRedirects(false);
        try
        {
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400)
                throw new IOException("Traffic Scotland refused the client ID/key: HTTP " + code);
            if (code != HttpURLConnection.HTTP_OK)
            {
                String hint = (code == 401 || code == 403) ? " (check your client ID/key)" : "";
                throw new IOException("Server answered HTTP " + code + hint);
            }
            // StringBuilder instead of String concatenation: the roadworks feed is >1 MB
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = in.readLine()) != null)
                    sb.append(line).append('\n');
            }
            Log.d(TAG, feed + ": downloaded " + sb.length() + " characters");
            return sb.toString();
        }
        finally
        {
            connection.disconnect();
        }
    }

    private boolean isOnline()
    {
        try
        {
            Network network = connectivity.getActiveNetwork();
            if (network == null)
                return false;
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        catch (RuntimeException e)
        {
            return true; // cannot tell: just try the download
        }
    }

    // ------------------------------------------------------------------ app-specific storage (cache)

    private File cacheFile(FeedType feed)
    {
        return new File(appContext.getFilesDir(), feed.name().toLowerCase(Locale.UK) + "_latest.xml");
    }

    private void saveCache(FeedType feed, String xml)
    {
        try (FileOutputStream out = new FileOutputStream(cacheFile(feed)))
        {
            out.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            Log.e(TAG, "Could not save " + feed, e);
        }
    }

    private static String readFile(File f) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = in.readLine()) != null)
                sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
