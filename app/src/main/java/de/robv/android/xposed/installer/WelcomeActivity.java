package de.robv.android.xposed.installer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import de.robv.android.xposed.installer.installation.AdvancedInstallerFragment;
import de.robv.android.xposed.installer.util.BottomNavigationViewHelper;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;
import de.robv.android.xposed.installer.util.ThemeUtil;

import static de.robv.android.xposed.installer.XposedApp.darkenColor;

public class WelcomeActivity extends XposedBaseActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener,
        ModuleListener, RepoListener {

    private static final String SELECTED_ITEM_ID = "SELECTED_ITEM_ID";
    private final Handler mDrawerHandler = new Handler();
    private RepoLoader mRepoLoader;
    private BottomNavigationView mNavigationView;
    private int mSelectedId;
    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setTheme(this);
        setContentView(R.layout.activity_welcome);

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mNavigationView = findViewById(R.id.bottom_nav);
        BottomNavigationViewHelper.removeShiftMode(mNavigationView);
        assert mNavigationView != null;
        mNavigationView.setOnNavigationItemSelectedListener(this);
        mNavigationView.setOnNavigationItemReselectedListener(new BottomNavigationView.OnNavigationItemReselectedListener() {
            @Override
            public void onNavigationItemReselected(@NonNull MenuItem item) {
                // Do nothing
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSelectedId = mNavigationView.getMenu().getItem(prefs.getInt("default_view", 0)).getItemId();
        mSelectedId = savedInstanceState == null ? mSelectedId : savedInstanceState.getInt(SELECTED_ITEM_ID);
        mNavigationView.getMenu().findItem(mSelectedId).setChecked(true);

        if (savedInstanceState == null) {
            postNavigate(mSelectedId);
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            int value = extras.getInt("fragment", prefs.getInt("default_view", 0));
            switchFragment(value);
        }

        mRepoLoader = RepoLoader.getInstance();
        ModuleUtil.getInstance().addListener(this);
        mRepoLoader.addListener(this, false);

        notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= 21)
            getWindow().setStatusBarColor(darkenColor(XposedApp.getColor(this), 0.85f));
    }

    public void switchFragment(int itemId) {
        mSelectedId = mNavigationView.getMenu().getItem(itemId).getItemId();
        mNavigationView.getMenu().findItem(mSelectedId).setChecked(true);
        postNavigate(mSelectedId);
    }

    private void postNavigate(final int itemId) {
        mDrawerHandler.post(new Runnable() {
            @Override
            public void run() {
                navigate(itemId);
            }
        });
    }

    private void navigate(int itemId) {
        Fragment navFragment = null;
        switch (itemId) {
            case R.id.drawer_item_1:
                navFragment = new AdvancedInstallerFragment();
                break;
            case R.id.drawer_item_2:
                navFragment = new ModulesFragment();
                break;
            case R.id.drawer_item_3:
                navFragment = new DownloadFragment();
                break;
            case R.id.drawer_item_4:
                navFragment = new LogsFragment();
                break;
            case R.id.drawer_item_5:
//                navFragment = new SettingsActivity.SettingsFragment();
//                startActivity(new Intent(this, SettingsActivity.class));
//                mNavigationView.getMenu().findItem(mPrevSelectedId).setChecked(true);
                break;
            case R.id.drawer_item_6:
                startActivity(new Intent(this, SupportActivity.class));
                return;
            case R.id.drawer_item_7:
//                startActivity(new Intent(this, AboutActivity.class));
                navFragment = new AboutActivity.AboutFragment();
                break;
        }

        if (navFragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            try {
                transaction.replace(R.id.content_frame, navFragment).commit();
                findViewById(R.id.content_frame).setAlpha(1f);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if (Build.VERSION.SDK_INT >= 21 && mToolbar != null) {
            mToolbar.setElevation(fragment instanceof AdvancedInstallerFragment ? 0 : dp(4));
        }
    }

    public int dp(float value) {
        float density = getApplicationContext().getResources().getDisplayMetrics().density;

        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(density * value);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        menuItem.setChecked(true);
        mSelectedId = menuItem.getItemId();
        postNavigate(mSelectedId);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_ID, mSelectedId);
    }

    private void notifyDataSetChanged() {
        View parentLayout = findViewById(R.id.content_frame);
        String frameworkUpdateVersion = mRepoLoader.getFrameworkUpdateVersion();
        boolean moduleUpdateAvailable = mRepoLoader.hasModuleUpdates();

        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.content_frame);
        if (currentFragment instanceof DownloadDetailsFragment) {
            if (frameworkUpdateVersion != null) {
                Snackbar.make(parentLayout, R.string.welcome_framework_update_available + " " + String.valueOf(frameworkUpdateVersion), Snackbar.LENGTH_LONG).show();
            }
        }

        boolean snackBar = getSharedPreferences(
                getPackageName() + "_preferences", MODE_PRIVATE).getBoolean("snack_bar", true);

        if (moduleUpdateAvailable && snackBar) {
            Snackbar.make(parentLayout, R.string.modules_updates_available, Snackbar.LENGTH_LONG).setAction(getString(R.string.view), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    switchFragment(2);
                }
            }).show();
        }
    }

    @Override
    public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
        notifyDataSetChanged();
    }

    @Override
    public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
        notifyDataSetChanged();
    }

    @Override
    public void onRepoReloaded(RepoLoader loader) {
        notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ModuleUtil.getInstance().removeListener(this);
        mRepoLoader.removeListener(this);
    }

    public void openLink(View view) {
        NavUtil.startURL(this, view.getTag().toString());
    }
}