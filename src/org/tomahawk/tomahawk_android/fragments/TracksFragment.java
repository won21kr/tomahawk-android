/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
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
package org.tomahawk.tomahawk_android.fragments;

import org.tomahawk.libtomahawk.collection.Artist;
import org.tomahawk.libtomahawk.collection.Track;
import org.tomahawk.libtomahawk.collection.UserCollection;
import org.tomahawk.libtomahawk.collection.UserPlaylist;
import org.tomahawk.libtomahawk.database.DatabaseHelper;
import org.tomahawk.libtomahawk.infosystem.InfoSystem;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.adapters.TomahawkListAdapter;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.utils.FragmentUtils;
import org.tomahawk.tomahawk_android.utils.ThreadManager;
import org.tomahawk.tomahawk_android.utils.TomahawkListItem;
import org.tomahawk.tomahawk_android.utils.TomahawkRunnable;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * {@link TomahawkFragment} which shows a set of {@link Track}s inside its {@link
 * se.emilsjolander.stickylistheaders.StickyListHeadersListView}
 */
public class TracksFragment extends TomahawkFragment implements OnItemClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mAlbum != null) {
            menu.findItem(R.id.action_gotoartist_item).setVisible(true);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * If the user clicks on a menuItem, handle what should be done here
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item != null) {
            if (item.getItemId() == R.id.action_gotoartist_item) {
                String key = TomahawkUtils.getCacheKey(mAlbum.getArtist());
                FragmentUtils.replace(getActivity(), getActivity().getSupportFragmentManager(),
                        AlbumsFragment.class, key, TomahawkFragment.TOMAHAWK_ARTIST_KEY,
                        false);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

        updateAdapter();
    }

    /**
     * Called every time an item inside the {@link se.emilsjolander.stickylistheaders.StickyListHeadersListView}
     * is clicked
     *
     * @param parent   The AdapterView where the click happened.
     * @param view     The view within the AdapterView that was clicked (this will be a view
     *                 provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id       The row id of the item that was clicked.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        position -= getListView().getHeaderViewsCount();
        if (position >= 0) {
            if (getListAdapter().getItem(position) instanceof Query) {
                Query query = (Query) getListAdapter().getItem(position);
                if (query.isPlayable()) {
                    ArrayList<Query> queries = new ArrayList<Query>();
                    TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
                    if (mAlbum != null) {
                        queries = mAlbum.getQueries(mIsLocal);
                    } else if (mArtist != null) {
                        queries = mArtist.getQueries(mIsLocal);
                    } else if (mUserPlaylist != null) {
                        queries = mUserPlaylist.getQueries();
                    } else {
                        queries.addAll(UserCollection.getInstance().getQueries());
                    }
                    PlaybackService playbackService = activity.getPlaybackService();
                    if (playbackService != null && shouldShowPlaystate() && mQueryPositions
                            .get(playbackService.getCurrentPlaylist().getCurrentQueryIndex())
                            == position) {
                        playbackService.playPause();
                    } else {
                        UserPlaylist playlist = UserPlaylist.fromQueryList(
                                DatabaseHelper.CACHED_PLAYLIST_ID,
                                DatabaseHelper.CACHED_PLAYLIST_NAME, queries,
                                queries.get(position));
                        if (playbackService != null) {
                            playbackService.setCurrentPlaylist(playlist);
                            playbackService.start();
                        }
                    }
                }
            }
        }
    }

    /**
     * Update this {@link TomahawkFragment}'s {@link TomahawkListAdapter} content
     */
    @Override
    protected void updateAdapter() {
        if (!mIsResumed) {
            return;
        }

        mResolveQueriesHandler.removeCallbacksAndMessages(null);
        mResolveQueriesHandler.sendEmptyMessage(RESOLVE_QUERIES_REPORTER_MSG);
        ArrayList<TomahawkListItem> queries = new ArrayList<TomahawkListItem>();
        TomahawkListAdapter tomahawkListAdapter;
        TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
        Context context = getActivity();
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View rootView = getActivity().findViewById(android.R.id.content);
        if (mAlbum != null) {
            activity.setTitle(mAlbum.getName());
            queries.addAll(mAlbum.getQueries(mIsLocal));
            if (getListAdapter() == null) {
                tomahawkListAdapter = new TomahawkListAdapter(context, layoutInflater, queries);
                tomahawkListAdapter.setShowResolvedBy(true);
                tomahawkListAdapter.setShowCategoryHeaders(true, false);
                tomahawkListAdapter.showContentHeader(rootView, getListView(), mAlbum, mIsLocal);
                setListAdapter(tomahawkListAdapter);
            } else {
                ((TomahawkListAdapter) getListAdapter()).setListItems(queries);
                ((TomahawkListAdapter) getListAdapter()).showContentHeader(rootView, getListView(),
                        mAlbum, mIsLocal);
            }
        } else if (mArtist != null) {
            activity.setTitle(mArtist.getName());
            queries.addAll(mArtist.getQueries(mIsLocal));
            if (getListAdapter() == null) {
                tomahawkListAdapter = new TomahawkListAdapter(context, layoutInflater, queries);
                tomahawkListAdapter.setShowResolvedBy(true);
                tomahawkListAdapter.setShowCategoryHeaders(true, false);
                tomahawkListAdapter.showContentHeader(rootView, getListView(), mArtist, mIsLocal);
                setListAdapter(tomahawkListAdapter);
            } else {
                ((TomahawkListAdapter) getListAdapter()).setListItems(queries);
                ((TomahawkListAdapter) getListAdapter()).showContentHeader(rootView, getListView(),
                        mArtist, mIsLocal);
            }
        } else if (mUserPlaylist != null) {
            ThreadManager.getInstance().execute(
                    new TomahawkRunnable(TomahawkRunnable.PRIORITY_IS_INFOSYSTEM_MEDIUM) {
                        @Override
                        public void run() {
                            getUserPlaylistArtists(mUserPlaylist);
                        }
                    }
            );
            if (!mUserPlaylist.isFilled()) {
                mUserPlaylist.setFilled(true);
                ThreadManager.getInstance().execute(
                        new TomahawkRunnable(TomahawkRunnable.PRIORITY_IS_INFOSYSTEM_HIGH) {
                            @Override
                            public void run() {
                                mUserPlaylist = DatabaseHelper.getInstance()
                                        .getUserPlaylist(mUserPlaylist.getId());
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateAdapter();
                                    }
                                });
                            }
                        }
                );
            } else {
                activity.setTitle(mUserPlaylist.getName());
                queries.addAll(mUserPlaylist.getQueries());
                if (getListAdapter() == null) {
                    tomahawkListAdapter = new TomahawkListAdapter(context, layoutInflater, queries);
                    tomahawkListAdapter.setShowResolvedBy(true);
                    tomahawkListAdapter.setShowCategoryHeaders(true, false);
                    tomahawkListAdapter
                            .showContentHeader(rootView, getListView(), mUserPlaylist, mIsLocal);
                    setListAdapter(tomahawkListAdapter);
                } else {
                    ((TomahawkListAdapter) getListAdapter()).setListItems(queries);
                    ((TomahawkListAdapter) getListAdapter())
                            .showContentHeader(rootView, getListView(),
                                    mUserPlaylist, mIsLocal);
                }
            }
        } else {
            activity.setTitle(getString(R.string.tracksfragment_title_string));
            queries.addAll(UserCollection.getInstance().getQueries());
            if (getListAdapter() == null) {
                tomahawkListAdapter = new TomahawkListAdapter(context, layoutInflater, queries);
                setListAdapter(tomahawkListAdapter);
            } else {
                ((TomahawkListAdapter) getListAdapter()).setListItems(queries);
            }
        }

        mShownQueries.clear();
        int i = 0;
        for (TomahawkListItem item : queries) {
            mShownQueries.add((Query) item);
            mQueryPositions.put(i, i);
            i++;
        }

        getListView().setOnItemClickListener(this);

        updateShowPlaystate();
    }

    private void getUserPlaylistArtists(UserPlaylist userPlaylist) {
        if (userPlaylist.getContentHeaderArtists().size() < 6) {
            final HashMap<Artist, Integer> countMap = new HashMap<Artist, Integer>();
            for (Query query : userPlaylist.getQueries()) {
                Artist artist = query.getArtist();
                if (countMap.containsKey(artist)) {
                    countMap.put(artist, countMap.get(artist) + 1);
                } else {
                    countMap.put(artist, 1);
                }
            }
            TreeMap<Artist, Integer> sortedCountMap = new TreeMap<Artist, Integer>(
                    new Comparator<Artist>() {
                        @Override
                        public int compare(Artist lhs, Artist rhs) {
                            return countMap.get(lhs) >= countMap.get(rhs) ? -1 : 1;
                        }
                    }
            );
            sortedCountMap.putAll(countMap);
            for (Artist artist : sortedCountMap.keySet()) {
                userPlaylist.addContentHeaderArtists(artist);
                ArrayList<String> requestIds = InfoSystem.getInstance().resolve(artist, true);
                for (String requestId : requestIds) {
                    mCurrentRequestIds.add(requestId);
                }
                if (userPlaylist.getContentHeaderArtists().size() == 6) {
                    break;
                }
            }
        }
    }
}
