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
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.adapters.TomahawkListAdapter;
import org.tomahawk.tomahawk_android.utils.FragmentUtils;
import org.tomahawk.tomahawk_android.utils.TomahawkListItem;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TomahawkFragment} which shows a set of {@link Artist}s inside its {@link
 * se.emilsjolander.stickylistheaders.StickyListHeadersListView}
 */
public class ArtistsFragment extends TomahawkFragment implements OnItemClickListener {

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
            Object item = getListAdapter().getItem(position);
            if (getListAdapter().getItem(position) instanceof Artist) {
                String key = TomahawkUtils.getCacheKey((Artist) item);
                FragmentUtils.replace(getActivity(), getActivity().getSupportFragmentManager(),
                        AlbumsFragment.class, key, TomahawkFragment.TOMAHAWK_ARTIST_KEY,
                        mIsLocal);
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

        TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
        Context context = getActivity();
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();

        activity.setTitle(getString(R.string.artistsfragment_title_string));
        List<TomahawkListItem> artists = new ArrayList<TomahawkListItem>();
        if (mIsLocal) {
            artists.addAll(Artist.getLocalArtists());
        } else {
            artists.addAll(Artist.getArtists());
        }
        if (getListAdapter() == null) {
            TomahawkListAdapter tomahawkListAdapter = new TomahawkListAdapter(context,
                    layoutInflater, artists);
            tomahawkListAdapter.setShowArtistAsSingleLine(mIsLocal);
            setListAdapter(tomahawkListAdapter);
        } else {
            ((TomahawkListAdapter) getListAdapter()).setListItems(artists);
        }

        getListView().setOnItemClickListener(this);
    }
}
