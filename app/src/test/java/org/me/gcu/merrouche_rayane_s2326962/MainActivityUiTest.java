package org.me.gcu.merrouche_rayane_s2326962;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.ui.TrafficViewModel;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI test of the real app on the JVM (Robolectric): the app's offline cache is filled with the
 * sample feeds supplied with the brief, so the app runs its "no connection / saved data" path.
 * Screenshots of each step are written to app/build/ui-screenshots/ (useful as test evidence).
 */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w393dp-h851dp-port-xhdpi")
public class MainActivityUiTest
{
    private static final File SHOTS = new File("build/ui-screenshots");

    @Before
    public void seedSavedData() throws Exception
    {
        Context ctx = ApplicationProvider.getApplicationContext();
        copyResource("unplannedContentExample1.xml", new File(ctx.getFilesDir(), "incident_latest.xml"));
        copyResource("roadworksContentExample1.xml", new File(ctx.getFilesDir(), "roadworks_latest.xml"));
        SHOTS.mkdirs();
    }

    @Test
    public void walkThroughTheApp() throws Exception
    {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class))
        {
            waitForData(scenario);

            // ---------- 1. list (portrait): 7 incidents + 452 roadworks from the saved data
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.situationList);
                assertEquals(459, list.getAdapter().getItemCount());
                String status = ((TextView) a.findViewById(R.id.statusText)).getText().toString();
                assertTrue(status, status.startsWith("Offline") || status.startsWith("Could not update"));
                assertFalse(a.findViewById(R.id.navigation) instanceof NavigationRailView);
                screenshot(a, "01_list_portrait");
            });

            // ---------- 2. select the first situation -> details over the list
            AtomicReference<String> selectedKey = new AtomicReference<>();
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.situationList);
                list.findViewHolderForAdapterPosition(0).itemView.performClick();
            });
            idle();
            scenario.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.detailContainer).getVisibility());
                TextView type = a.findViewById(R.id.detailType);
                assertFalse(type.getText().toString().isEmpty());
                selectedKey.set(vm(a).getSelectedKey());
                screenshot(a, "02_details_portrait");
                screenshotContent(a.findViewById(R.id.detailsScroll), "03_details_full_content");
                // Back closes the details
                a.getOnBackPressedDispatcher().onBackPressed();
            });
            idle();
            scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.detailContainer).getVisibility()));

            // ---------- 3. filter roadworks (colour-coded by duration)
            scenario.onActivity(a -> a.findViewById(R.id.filterRoadworks).performClick());
            idle();
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.situationList);
                assertEquals(452, list.getAdapter().getItemCount());
                screenshot(a, "04_roadworks_colour_coded");
                // select one roadworks to check that rotation keeps it
                list.findViewHolderForAdapterPosition(1).itemView.performClick();
            });
            idle();
            scenario.onActivity(a -> selectedKey.set(vm(a).getSelectedKey()));

            // ---------- 4. rotate to landscape: master-detail, same selection and filter
            RuntimeEnvironment.setQualifiers("w851dp-h393dp-land-xhdpi");
            scenario.recreate();
            idle();
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.navigation) instanceof NavigationRailView);
                assertEquals(selectedKey.get(), vm(a).getSelectedKey());
                RecyclerView list = a.findViewById(R.id.situationList);
                assertEquals(452, list.getAdapter().getItemCount()); // filter kept
                assertEquals(View.VISIBLE, a.findViewById(R.id.detailsScroll).getVisibility());
                screenshot(a, "05_landscape_master_detail");
                // "Show on map" from the details: map tab, details kept next to it (landscape)
                a.findViewById(R.id.detailMapButton).performClick();
            });
            idle();
            scenario.onActivity(a -> {
                assertEquals(TrafficViewModel.TAB_MAP, (int) vm(a).getCurrentTab().getValue());
                assertEquals(selectedKey.get(), vm(a).getSelectedKey());
                assertEquals(1, vm(a).getMapRequest().getValue().keys.size());
            });

            // ---------- 5. search by road (landscape)
            scenario.onActivity(a -> ((NavigationBarView) a.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_search));
            idle();
            scenario.onActivity(a -> {
                ((AutoCompleteTextView) a.findViewById(R.id.roadInput)).setText("m8");
                a.findViewById(R.id.roadSearchButton).performClick();
            });
            idle();
            AtomicReference<Integer> m8Count = new AtomicReference<>();
            scenario.onActivity(a -> {
                RecyclerView results = a.findViewById(R.id.resultsList);
                m8Count.set(results.getAdapter().getItemCount());
                assertTrue(m8Count.get() > 0);
                List<Situation> found = vm(a).getSearchResult().getValue().situations;
                for (Situation s : found)
                    assertTrue(s.matchesRoad("M8"));
                // changing tab closed the details of the previous screen
                assertEquals(null, vm(a).getSelectedKey());
                screenshot(a, "06_search_road_landscape");
            });

            // ---------- 6. back to portrait: search results kept
            RuntimeEnvironment.setQualifiers("w393dp-h851dp-port-xhdpi");
            scenario.recreate();
            idle();
            scenario.onActivity(a -> {
                assertFalse(a.findViewById(R.id.navigation) instanceof NavigationRailView);
                assertEquals(View.GONE, a.findViewById(R.id.detailContainer).getVisibility());
                RecyclerView results = a.findViewById(R.id.resultsList);
                assertEquals((int) m8Count.get(), results.getAdapter().getItemCount());
                screenshot(a, "07_search_road_portrait");
                // distance search from the fixed location (GCU), 5 km
                a.findViewById(R.id.modifySearchButton).performClick();
                a.findViewById(R.id.originFixed).performClick();
                ((android.widget.SeekBar) a.findViewById(R.id.radiusSeek)).setProgress(4);
                a.findViewById(R.id.nearbyButton).performClick();
            });
            idle();
            scenario.onActivity(a -> {
                TrafficViewModel.SearchResult r = vm(a).getSearchResult().getValue();
                assertNotNull(r.distancesKm);
                double previous = 0;
                for (Situation s : r.situations) // all within 5 km, nearest first
                {
                    double d = r.distancesKm.get(s.getKey());
                    assertTrue(d <= 5 && d >= previous);
                    previous = d;
                }
                assertTrue(r.situations.size() > 0);
                screenshot(a, "08_search_distance_portrait");
                // show those results on the map
                a.findViewById(R.id.resultsMapButton).performClick();
            });
            idle();

            // ---------- 7. map tab with the search results (Google map itself can't render here)
            scenario.onActivity(a -> {
                assertEquals(TrafficViewModel.TAB_MAP, (int) vm(a).getCurrentTab().getValue());
                TrafficViewModel.MapRequest req = vm(a).getMapRequest().getValue();
                assertNotNull(req.keys); // a user selection, not "all"
                // (under Robolectric the Google map never becomes "ready", so check the request)
                assertEquals(vm(a).getSearchResult().getValue().situations.size(), req.keys.size());
                screenshot(a, "09_map_tab");
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    private static TrafficViewModel vm(MainActivity a)
    {
        return new ViewModelProvider(a).get(TrafficViewModel.class);
    }

    private static void idle()
    {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** The data is loaded by background threads: let them work, then deliver LiveData updates */
    private static void waitForData(ActivityScenario<MainActivity> scenario) throws InterruptedException
    {
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end)
        {
            idle();
            AtomicReference<Integer> n = new AtomicReference<>(0);
            scenario.onActivity(a -> {
                List<Situation> all = vm(a).getAllSituations().getValue();
                n.set(all == null ? 0 : all.size());
            });
            if (n.get() > 0)
            {
                Thread.sleep(300); // let the status update arrive too
                idle();
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Data was not loaded");
    }

    private static void screenshot(MainActivity a, String name)
    {
        View root = a.getWindow().getDecorView();
        save(root, root.getWidth(), root.getHeight(), name);
    }

    /** Draws the whole content of a scroll view (not just the visible part) */
    private static void screenshotContent(View scroll, String name)
    {
        View content = ((android.view.ViewGroup) scroll).getChildAt(0);
        save(content, content.getWidth(), content.getHeight(), name);
    }

    private static void save(View v, int w, int h, String name)
    {
        try
        {
            Bitmap b = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(Color.WHITE);
            v.draw(c);
            try (OutputStream out = new FileOutputStream(new File(SHOTS, name + ".png")))
            {
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void copyResource(String resource, File target) throws Exception
    {
        try (InputStream in = MainActivityUiTest.class.getClassLoader().getResourceAsStream(resource);
             OutputStream out = new FileOutputStream(target))
        {
            assertNotNull(resource, in);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0)
                out.write(buf, 0, n);
        }
    }
}
