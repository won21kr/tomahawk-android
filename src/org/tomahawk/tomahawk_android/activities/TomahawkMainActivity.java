/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2012, Christopher Reichert <creichert07@gmail.com>
 *   Copyright 2012, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.activities;

import org.tomahawk.libtomahawk.authentication.AuthenticatorManager;
import org.tomahawk.libtomahawk.authentication.AuthenticatorUtils;
import org.tomahawk.libtomahawk.collection.CollectionLoader;
import org.tomahawk.libtomahawk.collection.Image;
import org.tomahawk.libtomahawk.collection.UserCollection;
import org.tomahawk.libtomahawk.database.DatabaseHelper;
import org.tomahawk.libtomahawk.database.TomahawkSQLiteHelper;
import org.tomahawk.libtomahawk.infosystem.InfoRequestData;
import org.tomahawk.libtomahawk.infosystem.InfoSystem;
import org.tomahawk.libtomahawk.infosystem.User;
import org.tomahawk.libtomahawk.infosystem.hatchet.HatchetInfoPlugin;
import org.tomahawk.libtomahawk.resolver.DataBaseResolver;
import org.tomahawk.libtomahawk.resolver.PipeLine;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.libtomahawk.resolver.ScriptResolver;
import org.tomahawk.libtomahawk.resolver.spotify.SpotifyResolver;
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.adapters.SuggestionSimpleCursorAdapter;
import org.tomahawk.tomahawk_android.adapters.TomahawkMenuAdapter;
import org.tomahawk.tomahawk_android.fragments.PlaybackFragment;
import org.tomahawk.tomahawk_android.fragments.SearchableFragment;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.services.PlaybackService.PlaybackServiceConnection;
import org.tomahawk.tomahawk_android.services.PlaybackService.PlaybackServiceConnection.PlaybackServiceConnectionListener;
import org.tomahawk.tomahawk_android.utils.FragmentUtils;
import org.tomahawk.tomahawk_android.utils.ThreadManager;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The main Tomahawk activity
 */
public class TomahawkMainActivity extends ActionBarActivity
        implements PlaybackServiceConnectionListener,
        LoaderManager.LoaderCallbacks<UserCollection>,
        FragmentManager.OnBackStackChangedListener {

    private final static String TAG = TomahawkMainActivity.class.getName();

    public static final String PLAYBACKSERVICE_READY
            = "org.tomahawk.tomahawk_android.playbackservice_ready";

    public static final String SHOW_PLAYBACKFRAGMENT_ON_STARTUP
            = "org.tomahawk.tomahawk_android.show_playbackfragment_on_startup";

    public static final String ID_COUNTER = "org.tomahawk.tomahawk_android.id_counter";

    public static final String FRAGMENT_TAG = "the_ultimate_tag";

    private static Context sApplicationContext;

    private static long mSessionIdCounter = 0;

    private CharSequence mTitle;

    private PlaybackServiceConnection mPlaybackServiceConnection = new PlaybackServiceConnection(
            this);

    private PlaybackService mPlaybackService;

    private User mLoggedInUser;

    private DrawerLayout mDrawerLayout;

    private ListView mDrawerList;

    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;

    private TomahawkMainReceiver mTomahawkMainReceiver;

    private View mNowPlayingFrame;

    private Drawable mProgressDrawable;

    private Handler mAnimationHandler;

    public static boolean sIsConnectedToWifi;

    // Used to display an animated progress drawable
    private Runnable mAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            mProgressDrawable.setLevel(mProgressDrawable.getLevel() + 400);
            getSupportActionBar().setLogo(mProgressDrawable);
            mAnimationHandler.postDelayed(mAnimationRunnable, 50);
        }
    };

    private Handler mShouldShowAnimationHandler;

    private Runnable mShouldShowAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            mAnimationHandler.removeCallbacks(mAnimationRunnable);
            if (ThreadManager.getInstance().isActive()
                    || (mPlaybackService != null && mPlaybackService.isPreparing())) {
                mAnimationHandler.post(mAnimationRunnable);
            } else {
                getSupportActionBar().setLogo(R.drawable.ic_launcher);
            }
            mShouldShowAnimationHandler.postDelayed(mShouldShowAnimationRunnable, 500);
        }
    };

    /**
     * Handles incoming broadcasts.
     */
    private class TomahawkMainReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (UserCollection.COLLECTION_UPDATED.equals(intent.getAction())) {
                PipeLine.getInstance().onCollectionUpdated();
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                boolean noConnectivity =
                        intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                if (!noConnectivity) {
                    AuthenticatorUtils hatchetAuthUtils = AuthenticatorManager.getInstance()
                            .getAuthenticatorUtils(AuthenticatorManager.AUTHENTICATOR_ID_HATCHET);
                    InfoSystem.getInstance().sendLoggedOps(hatchetAuthUtils);
                    UserCollection.getInstance().fetchHatchetUserPlaylists();
                }
                ConnectivityManager connMgr = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null
                        && connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                    sIsConnectedToWifi = true;
                } else {
                    sIsConnectedToWifi = false;
                }
            } else if (UserCollection.COLLECTION_UPDATED.equals(intent.getAction())) {
                onCollectionUpdated();
            } else if (PlaybackService.BROADCAST_CURRENTTRACKCHANGED.equals(intent.getAction())) {
                if (mPlaybackService != null) {
                    updateViewVisibility();
                }
            } else if (PlaybackService.BROADCAST_PLAYSTATECHANGED.equals(intent.getAction())) {
                updateNowPlayingButtons();
            } else if (InfoSystem.INFOSYSTEM_RESULTSREPORTED.equals(intent.getAction())) {
                String requestId = intent.getStringExtra(
                        InfoSystem.INFOSYSTEM_RESULTSREPORTED_REQUESTID);
                InfoRequestData data = InfoSystem.getInstance().getInfoRequestById(requestId);
                if (data != null
                        && data.getType() == InfoRequestData.INFOREQUESTDATA_TYPE_USERS_SELF) {
                    Map<String, List> convertedResultMap = data.getConvertedResultMap();
                    if (convertedResultMap != null) {
                        List users = convertedResultMap.get(HatchetInfoPlugin.HATCHET_USERS);
                        if (users != null && users.size() > 0) {
                            mLoggedInUser = (User) users.get(0);
                            updateDrawer();
                        }
                    }
                }
            }
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        /**
         * Called every time an item inside the {@link android.widget.ListView} is clicked
         *
         * @param parent   The AdapterView where the click happened.
         * @param view     The view within the AdapterView that was clicked (this will be a view
         *                 provided by the adapter)
         * @param position The position of the view in the adapter.
         * @param id       The row id of the item that was clicked.
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Show the correct hub, and if needed, display the search editText inside the ActionBar
            FragmentUtils.showHub(TomahawkMainActivity.this, getSupportFragmentManager(),
                    (int) id, mLoggedInUser);
            if (mDrawerLayout != null) {
                mDrawerLayout.closeDrawer(mDrawerList);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sApplicationContext = getApplicationContext();

        //Setup our services
        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, mPlaybackServiceConnection, Context.BIND_AUTO_CREATE);

        setContentView(R.layout.tomahawk_main_activity);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (!AuthenticatorManager.getInstance().isInitialized()) {
            AuthenticatorManager.getInstance().setContext(getContext());
        }

        if (!PipeLine.getInstance().isInitialized()) {
            PipeLine.getInstance().setContext(getContext());
            PipeLine.getInstance()
                    .addResolver(new DataBaseResolver(PipeLine.RESOLVER_ID_USERCOLLECTION,
                            getContext()));
            ScriptResolver scriptResolver = new ScriptResolver(PipeLine.RESOLVER_ID_JAMENDO,
                    "js/jamendo/content/contents/code/jamendo.js", getContext());
            PipeLine.getInstance().addResolver(scriptResolver);
            scriptResolver = new ScriptResolver(PipeLine.RESOLVER_ID_OFFICIALFM,
                    "js/official.fm/content/contents/code/officialfm.js", getContext());
            PipeLine.getInstance().addResolver(scriptResolver);
            scriptResolver = new ScriptResolver(PipeLine.RESOLVER_ID_EXFM,
                    "js/exfm/content/contents/code/exfm.js", getContext());
            PipeLine.getInstance().addResolver(scriptResolver);
            scriptResolver = new ScriptResolver(PipeLine.RESOLVER_ID_SOUNDCLOUD,
                    "js/soundcloud/content/contents/code/soundcloud.js", getContext());
            PipeLine.getInstance().addResolver(scriptResolver);
            SpotifyResolver spotifyResolver = new SpotifyResolver(PipeLine.RESOLVER_ID_SPOTIFY,
                    getContext());
            PipeLine.getInstance().addResolver(spotifyResolver);
            PipeLine.getInstance().setAllResolversAdded(true);
        }

        // Initialize UserPlaylistsDataSource, which makes it possible to retrieve persisted
        // UserPlaylists
        if (!DatabaseHelper.getInstance().isInitialized()) {
            DatabaseHelper.getInstance().setContext(getContext());
            DatabaseHelper.getInstance().open();
        }

        if (!InfoSystem.getInstance().isInitialized()) {
            InfoSystem.getInstance().setContext(getContext());
            InfoSystem.getInstance().addInfoPlugin(new HatchetInfoPlugin(getContext()));
        }

        if (!UserCollection.getInstance().isInitialized()) {
            Log.d(TAG, "Initializing Local Collection.");
            UserCollection.getInstance().setContext(getContext());
        }

        mProgressDrawable = getResources().getDrawable(R.drawable.progress_indeterminate_tomahawk);

        mTitle = mDrawerTitle = getTitle();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mNowPlayingFrame = findViewById(R.id.now_playing_frame);
        mNowPlayingFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentUtils.showHub(TomahawkMainActivity.this, getSupportFragmentManager(),
                        FragmentUtils.HUB_ID_PLAYBACK);
            }
        });
        ImageButton previousButton = (ImageButton) mNowPlayingFrame
                .findViewById(R.id.now_playing_button_previous);
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPlaybackService != null) {
                    mPlaybackService.previous();
                }
            }
        });
        ImageButton playPauseButton = (ImageButton) mNowPlayingFrame
                .findViewById(R.id.now_playing_button_playpause);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPlaybackService != null) {
                    mPlaybackService.playPause(true);
                }
            }
        });
        ImageButton nextButton = (ImageButton) mNowPlayingFrame
                .findViewById(R.id.now_playing_button_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPlaybackService != null) {
                    mPlaybackService.next();
                }
            }
        });

        if (mDrawerLayout != null) {
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_drawer,
                    R.string.drawer_open, R.string.drawer_close) {

                /** Called when a drawer has settled in a completely closed state. */
                public void onDrawerClosed(View view) {
                    getSupportActionBar().setTitle(mTitle);
                    supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }

                /** Called when a drawer has settled in a completely open state. */
                public void onDrawerOpened(View drawerView) {
                    getSupportActionBar().setTitle(mDrawerTitle);
                    supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }
            };
            // Set the drawer toggle as the DrawerListener
            mDrawerLayout.setDrawerListener(mDrawerToggle);
        }
        updateDrawer();

        // set customization variables on the ActionBar
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        if (savedInstanceState == null) {
            FragmentUtils.addRootFragment(TomahawkMainActivity.this, getSupportFragmentManager());
        }
        getSupportFragmentManager().addOnBackStackChangedListener(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        mAnimationHandler = new Handler();
        mShouldShowAnimationHandler = new Handler();
        mShouldShowAnimationHandler.post(mShouldShowAnimationRunnable);

        if (SHOW_PLAYBACKFRAGMENT_ON_STARTUP.equals(getIntent().getAction())) {
            // if this Activity is being shown after the user clicked the notification
            FragmentUtils.showHub(TomahawkMainActivity.this, getSupportFragmentManager(),
                    FragmentUtils.HUB_ID_PLAYBACK);
        }
        if (getIntent().hasExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)) {
            FragmentUtils.showHub(TomahawkMainActivity.this, getSupportFragmentManager(),
                    FragmentUtils.HUB_ID_SETTINGS);
        }

        if (mPlaybackService != null) {
            setNowPlayingInfo();
        }

        getSupportLoaderManager().destroyLoader(0);
        getSupportLoaderManager().initLoader(0, null, this);

        if (mTomahawkMainReceiver == null) {
            mTomahawkMainReceiver = new TomahawkMainReceiver();
        }

        // Register intents that the BroadcastReceiver should listen to
        registerReceiver(mTomahawkMainReceiver,
                new IntentFilter(UserCollection.COLLECTION_UPDATED));
        registerReceiver(mTomahawkMainReceiver,
                new IntentFilter(PlaybackService.BROADCAST_CURRENTTRACKCHANGED));
        registerReceiver(mTomahawkMainReceiver,
                new IntentFilter(PlaybackService.BROADCAST_PLAYSTATECHANGED));
        registerReceiver(mTomahawkMainReceiver,
                new IntentFilter(InfoSystem.INFOSYSTEM_RESULTSREPORTED));
        registerReceiver(mTomahawkMainReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onPause() {
        super.onPause();

        mAnimationHandler.removeCallbacks(mAnimationRunnable);
        mShouldShowAnimationHandler.removeCallbacks(mShouldShowAnimationRunnable);
        mAnimationHandler = null;
        mShouldShowAnimationHandler = null;

        if (mTomahawkMainReceiver != null) {
            unregisterReceiver(mTomahawkMainReceiver);
            mTomahawkMainReceiver = null;
        }
    }

    @Override
    public void onDestroy() {
        if (mPlaybackService != null) {
            unbindService(mPlaybackServiceConnection);
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        getMenuInflater().inflate(R.menu.tomahawk_main_menu, menu);
        final MenuItem savePlaylistItem = menu.findItem(R.id.action_saveplaylist_item);
        savePlaylistItem.setVisible(false);
        final MenuItem showPlaylistItem = menu.findItem(R.id.action_show_playlist_item);
        showPlaylistItem.setVisible(false);
        final MenuItem goToArtistItem = menu.findItem(R.id.action_gotoartist_item);
        goToArtistItem.setVisible(false);
        final MenuItem goToAlbumItem = menu.findItem(R.id.action_gotoalbum_item);
        goToAlbumItem.setVisible(false);
        final MenuItem loveItem = menu.findItem(R.id.action_love_item);
        loveItem.setVisible(false);
        // customize the searchView
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        SearchView.SearchAutoComplete searchAutoComplete
                = (SearchView.SearchAutoComplete) searchView
                .findViewById(android.support.v7.appcompat.R.id.search_src_text);
        searchAutoComplete.setDropDownBackgroundResource(R.drawable.menu_dropdown_panel_tomahawk);
        View searchEditText = searchView
                .findViewById(android.support.v7.appcompat.R.id.search_plate);
        searchEditText.setBackgroundResource(R.drawable.textfield_searchview_holo_dark);
        searchView.setQueryHint(getString(R.string.searchfragment_title_string));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !TextUtils.isEmpty(query)) {
                    DatabaseHelper.getInstance().addEntryToSearchHistory(query);
                    FragmentUtils.replace(TomahawkMainActivity.this, getSupportFragmentManager(),
                            SearchableFragment.class, null, null, query, false, true);
                    if (searchItem != null) {
                        MenuItemCompat.collapseActionView(searchItem);
                    }
                    searchView.clearFocus();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Cursor cursor = DatabaseHelper.getInstance().getSearchHistoryCursor(newText);
                if (cursor.getCount() != 0) {
                    String[] columns = new String[]{
                            TomahawkSQLiteHelper.SEARCHHISTORY_COLUMN_ENTRY};
                    int[] columnTextId = new int[]{android.R.id.text1};

                    SuggestionSimpleCursorAdapter simple = new SuggestionSimpleCursorAdapter(
                            getBaseContext(), android.R.layout.simple_list_item_1, cursor, columns,
                            columnTextId, 0);

                    searchView.setSuggestionsAdapter(simple);
                    return true;
                } else {
                    return false;
                }
            }
        });
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                SQLiteCursor cursor = (SQLiteCursor) searchView.getSuggestionsAdapter()
                        .getItem(position);
                int indexColumnSuggestion = cursor
                        .getColumnIndex(TomahawkSQLiteHelper.SEARCHHISTORY_COLUMN_ENTRY);

                searchView.setQuery(cursor.getString(indexColumnSuggestion), false);

                return true;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                SQLiteCursor cursor = (SQLiteCursor) searchView.getSuggestionsAdapter()
                        .getItem(position);
                int indexColumnSuggestion = cursor
                        .getColumnIndex(TomahawkSQLiteHelper.SEARCHHISTORY_COLUMN_ENTRY);

                searchView.setQuery(cursor.getString(indexColumnSuggestion), false);

                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content view
        if (mDrawerLayout != null) {
            boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
            getSupportActionBar().setDisplayShowCustomEnabled(!drawerOpen);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item) ||
                super.onOptionsItemSelected(item);
    }

    /**
     * If the PlaybackService signals, that it is ready, this method is being called
     */
    @Override
    public void onPlaybackServiceReady() {
        updateViewVisibility();
        setNowPlayingInfo();
        sendBroadcast(new Intent(PLAYBACKSERVICE_READY));
    }

    @Override
    public void setPlaybackService(PlaybackService ps) {
        mPlaybackService = ps;
    }

    public PlaybackService getPlaybackService() {
        return mPlaybackService;
    }

    @Override
    public Loader<UserCollection> onCreateLoader(int id, Bundle args) {
        return new CollectionLoader(getContext(), UserCollection.getInstance());
    }

    @Override
    public void onLoaderReset(Loader<UserCollection> loader) {
    }

    @Override
    public void onLoadFinished(Loader<UserCollection> loader, UserCollection coll) {
    }

    /**
     * Called when a {@link Collection} has been updated.
     */
    protected void onCollectionUpdated() {
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    private void updateDrawer() {
        if (mLoggedInUser == null) {
            InfoSystem.getInstance().resolve(InfoRequestData.INFOREQUESTDATA_TYPE_USERS_SELF, null);
        }
        // Set up the TomahawkMenuAdapter. Give it its set of menu item texts and icons to display
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        TomahawkMenuAdapter slideMenuAdapter = new TomahawkMenuAdapter(this,
                getResources().getStringArray(R.array.slide_menu_items),
                getResources().obtainTypedArray(R.array.slide_menu_items_icons),
                getResources().obtainTypedArray(R.array.slide_menu_items_colors));
        slideMenuAdapter.showContentHeader(mDrawerList, mLoggedInUser);
        mDrawerList.setAdapter(slideMenuAdapter);

        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
    }

    /**
     * Sets the playback information
     */
    public void setNowPlayingInfo() {
        Query query = null;
        if (mPlaybackService != null) {
            query = mPlaybackService.getCurrentQuery();
        }
        if (mNowPlayingFrame != null) {
            ImageView nowPlayingInfoAlbumArt = (ImageView) mNowPlayingFrame
                    .findViewById(R.id.now_playing_album_art);
            TextView nowPlayingInfoArtist = (TextView) mNowPlayingFrame
                    .findViewById(R.id.now_playing_artist);
            TextView nowPlayingInfoTitle = (TextView) mNowPlayingFrame
                    .findViewById(R.id.now_playing_title);

            if (query != null) {
                if (nowPlayingInfoAlbumArt != null && nowPlayingInfoArtist != null
                        && nowPlayingInfoTitle != null) {
                    if (query.getAlbum() != null) {
                        TomahawkUtils.loadImageIntoImageView(this, nowPlayingInfoAlbumArt,
                                query.getImage(), Image.IMAGE_SIZE_SMALL);
                    }
                    nowPlayingInfoArtist.setText(query.getArtist().toString());
                    nowPlayingInfoTitle.setText(query.getName());
                }
            }
            updateNowPlayingButtons();
        }
    }

    public void updateNowPlayingButtons() {
        ImageButton playPauseButton = (ImageButton) mNowPlayingFrame
                .findViewById(R.id.now_playing_button_playpause);
        if (mPlaybackService != null) {
            if (mPlaybackService.isPlaying()) {
                TomahawkUtils.loadDrawableIntoImageView(this, playPauseButton,
                        R.drawable.ic_player_pause);
            } else {
                TomahawkUtils.loadDrawableIntoImageView(this, playPauseButton,
                        R.drawable.ic_player_play);
            }
        }
    }

    @Override
    public void onBackStackChanged() {
        updateViewVisibility();
    }

    public void updateViewVisibility() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment instanceof PlaybackFragment
                || mPlaybackService == null || mPlaybackService.getCurrentQuery() == null) {
            setNowPlayingInfoVisibility(false);
        } else {
            setNowPlayingInfoVisibility(true);
        }
        if (fragment instanceof SearchableFragment) {
            setSearchPanelVisibility(true);
        } else {
            setSearchPanelVisibility(false);
        }
    }

    public void setSearchPanelVisibility(boolean enabled) {
        View searchPanel = findViewById(R.id.search_panel);
        if (searchPanel != null) {
            if (enabled) {
                searchPanel.setVisibility(View.VISIBLE);
            } else {
                searchPanel.setVisibility(View.GONE);
            }
        }
    }

    public void setNowPlayingInfoVisibility(boolean enabled) {
        if (enabled) {
            mNowPlayingFrame.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            mNowPlayingFrame.setVisibility(View.VISIBLE);
            if (mPlaybackService != null) {
                setNowPlayingInfo();
            }
        } else {
            mNowPlayingFrame.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
            mNowPlayingFrame.setVisibility(View.GONE);
        }
    }

    public static long getSessionUniqueId() {
        return mSessionIdCounter++;
    }

    public static String getSessionUniqueStringId() {
        return String.valueOf(getSessionUniqueId());
    }

    public static long getLifetimeUniqueId() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(sApplicationContext);
        long id = sharedPreferences.getLong(ID_COUNTER, 0);
        sharedPreferences.edit().putLong(ID_COUNTER, id + 1).commit();
        return id;
    }

    public static String getLifetimeUniqueStringId() {
        return String.valueOf(getLifetimeUniqueId());
    }

    public static Context getContext() {
        return sApplicationContext;
    }
}
