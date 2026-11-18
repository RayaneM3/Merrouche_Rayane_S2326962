//
// Name                 Rayane Merrouche
// Student ID           S2326962
// Programme of Study   Software Development
//
package org.me.gcu.merrouche_rayane_s2326962;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;

import org.me.gcu.merrouche_rayane_s2326962.data.LoadState;
import org.me.gcu.merrouche_rayane_s2326962.ui.DetailsFragment;
import org.me.gcu.merrouche_rayane_s2326962.ui.SearchFragment;
import org.me.gcu.merrouche_rayane_s2326962.ui.SituationListFragment;
import org.me.gcu.merrouche_rayane_s2326962.ui.SituationMapFragment;
import org.me.gcu.merrouche_rayane_s2326962.ui.TrafficViewModel;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Single Activity of the app (single-activity + fragments + MVVM architecture).
 * It hosts the three tabs (list / search / map) in mainContainer and the details of the selected
 * situation in detailContainer. All state lives in the TrafficViewModel, so rotating the device
 * (which switches between res/layout and res/layout-land) keeps everything the user was viewing.
 */
public class MainActivity extends AppCompatActivity
{
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK);

    private TrafficViewModel viewModel;
    private NavigationBarView navigation;
    private View detailContainer, statusBar;
    private TextView statusText;
    private ProgressBar statusProgress;
    private MaterialToolbar toolbar;
    private boolean landscape;
    private int shownTab = -1;

    // In portrait the Back button first closes the details pane
    private final OnBackPressedCallback closeDetails = new OnBackPressedCallback(false)
    {
        @Override
        public void handleOnBackPressed()
        {
            viewModel.clearSelection();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Keep the content clear of the status/navigation bars, display cut-outs and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        navigation = findViewById(R.id.navigation);
        detailContainer = findViewById(R.id.detailContainer);
        statusBar = findViewById(R.id.statusBar);
        statusText = findViewById(R.id.statusText);
        statusProgress = findViewById(R.id.statusProgress);
        // The landscape layout uses a navigation rail and shows list and details side by side
        landscape = navigation instanceof NavigationRailView;

        viewModel = new ViewModelProvider(this).get(TrafficViewModel.class);

        // The details fragment is always there; its container is shown or hidden as needed
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentById(R.id.detailContainer) == null)
            fm.beginTransaction().replace(R.id.detailContainer, new DetailsFragment()).commit();

        navigation.setOnItemSelectedListener(item -> {
            viewModel.setCurrentTab(tabFor(item.getItemId()));
            return true;
        });
        statusBar.setOnClickListener(v -> viewModel.refreshNow());

        getOnBackPressedDispatcher().addCallback(this, closeDetails);

        viewModel.getCurrentTab().observe(this, this::showTab);
        viewModel.getSelected().observe(this, s -> {
            boolean open = s != null;
            if (!landscape)
            {
                detailContainer.setVisibility(open ? View.VISIBLE : View.GONE);
                closeDetails.setEnabled(open);
            }
        });
        viewModel.getLoadState().observe(this, this::showLoadState);
        viewModel.getUpdateInterval().observe(this, m -> invalidateOptionsMenu());
    }

    // ------------------------------------------------------------------ tabs

    private static int tabFor(int itemId)
    {
        if (itemId == R.id.nav_search) return TrafficViewModel.TAB_SEARCH;
        if (itemId == R.id.nav_map) return TrafficViewModel.TAB_MAP;
        return TrafficViewModel.TAB_LIST;
    }

    private static int itemFor(int tab)
    {
        if (tab == TrafficViewModel.TAB_SEARCH) return R.id.nav_search;
        if (tab == TrafficViewModel.TAB_MAP) return R.id.nav_map;
        return R.id.nav_list;
    }

    /** Puts the fragment of the selected tab in mainContainer (fragment transaction) */
    private void showTab(int tab)
    {
        if (navigation.getSelectedItemId() != itemFor(tab))
            navigation.setSelectedItemId(itemFor(tab));

        // Changing tab closes the details (they belong to the previous screen), except when the
        // user asked to see the selected situation on the map: in landscape the details then stay
        // next to the map (in portrait they would cover it, so they close anyway)
        boolean keep = viewModel.consumeKeepSelection() && landscape;
        if (shownTab != -1 && shownTab != tab && !keep)
            viewModel.clearSelection();
        shownTab = tab;

        String tag = "tab" + tab;
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.mainContainer);
        if (current != null && tag.equals(current.getTag()))
            return; // already showing (i.e. restored after rotation)

        Fragment f;
        if (tab == TrafficViewModel.TAB_SEARCH)
            f = new SearchFragment();
        else if (tab == TrafficViewModel.TAB_MAP)
            f = new SituationMapFragment();
        else
            f = new SituationListFragment();
        fm.beginTransaction().replace(R.id.mainContainer, f, tag).commit();
    }

    // ------------------------------------------------------------------ status banner

    private void showLoadState(LoadState s)
    {
        int bg, fg;
        String text;
        String counts = s.incidentCount + " incidents, " + s.roadworksCount + " roadworks";
        String next = s.nextUpdate == null ? "" : " · next update " + TIME.format(s.nextUpdate);
        String dataTime = s.dataTime == null ? "" : DATE_TIME.format(s.dataTime);
        switch (s.status)
        {
            case LOADING:
                bg = R.color.status_ok; fg = R.color.status_ok_text;
                text = "Updating Traffic Scotland data…";
                break;
            case UP_TO_DATE:
                bg = R.color.status_ok; fg = R.color.status_ok_text;
                text = "Updated " + TIME.format(s.dataTime) + " (" + counts + ")" + next;
                break;
            case OFFLINE_CACHED:
                bg = R.color.status_warning; fg = R.color.status_warning_text;
                text = "Offline: showing data saved " + dataTime + ". It will update when the connection returns.";
                break;
            case ERROR_CACHED:
                bg = R.color.status_warning; fg = R.color.status_warning_text;
                text = "Could not update (" + s.message + "). Showing data saved " + dataTime + ". Tap to retry.";
                break;
            default: // NO_DATA
                bg = R.color.status_error; fg = R.color.status_error_text;
                text = "No data available" + (s.message.isEmpty() ? "" : ": " + s.message)
                        + ". Connect to the internet and tap here to retry.";
                break;
        }
        statusBar.setBackgroundColor(ContextCompat.getColor(this, bg));
        statusText.setTextColor(ContextCompat.getColor(this, fg));
        statusText.setText(text);
        statusProgress.setVisibility(s.status == LoadState.Status.LOADING ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ options menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        Long minutes = viewModel.getUpdateInterval().getValue();
        long m = minutes == null ? 60 : minutes;
        int id = m == 1 ? R.id.interval_1 : m == 5 ? R.id.interval_5 : m == 15 ? R.id.interval_15 : R.id.interval_60;
        MenuItem item = menu.findItem(id);
        if (item != null)
            item.setChecked(true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();
        if (id == R.id.action_refresh)
            viewModel.refreshNow();
        else if (id == R.id.interval_60)
            viewModel.setUpdateInterval(60);
        else if (id == R.id.interval_15)
            viewModel.setUpdateInterval(15);
        else if (id == R.id.interval_5)
            viewModel.setUpdateInterval(5);
        else if (id == R.id.interval_1)
            viewModel.setUpdateInterval(1);
        else if (id == R.id.action_about)
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.about_text)
                    .setPositiveButton("OK", null)
                    .show();
        else
            return super.onOptionsItemSelected(item);
        return true;
    }
}
