/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2013, Enno Gottschalk <mrmaffen@googlemail.com>
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
package org.tomahawk.tomahawk_android.utils;

import org.tomahawk.libtomahawk.collection.Track;
import org.tomahawk.libtomahawk.resolver.spotify.SpotifyServiceUtils;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.services.SpotifyService;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Messenger;

import java.io.IOException;

/**
 * This class wraps a standard {@link MediaPlayer} object together with all functionality to be able
 * to directly playback spotify-resolved tracks with OpenSLES .
 */
public class TomahawkMediaPlayer
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    private PlaybackService mPlaybackService;

    private MediaPlayer mMediaPlayer;

    // Whether to use the MediaPlayer or OpenSLES
    private boolean mUseMediaPlayer;

    private boolean mIsPreparing;

    private boolean mIsPlaying;

    private Messenger mToSpotifyMessenger = null;

    private int mSpotifyCurrentPosition = 0;

    private boolean mSpotifyIsInitalized;

    /**
     * Construct a new {@link TomahawkMediaPlayer}
     */
    public TomahawkMediaPlayer(PlaybackService playbackService) {
        mPlaybackService = playbackService;
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnCompletionListener(this);
    }

    public void setToSpotifyMessenger(Messenger toSpotifyMessenger) {
        mToSpotifyMessenger = toSpotifyMessenger;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mIsPreparing = false;
        mPlaybackService.onPrepared(this);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return mPlaybackService.onError(this, what, extra);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mPlaybackService.onCompletion(this);
    }

    public void setSpotifyCurrentPosition(int position) {
        mSpotifyCurrentPosition = position;
    }

    public void setSpotifyIsInitalized(boolean spotifyIsInitalized) {
        mSpotifyIsInitalized = spotifyIsInitalized;
    }

    /**
     * @return the current track position
     */
    public int getCurrentPosition() {
        if (mUseMediaPlayer) {
            return mMediaPlayer.getCurrentPosition();
        } else {
            return mSpotifyCurrentPosition;
        }
    }

    public void setVolume(float leftVolume, float rightVolume) {
        mMediaPlayer.setVolume(leftVolume, rightVolume);
    }

    public void release() {
        mMediaPlayer.release();
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    /**
     * Start playing the previously prepared {@link Track}
     */
    public void start() throws IllegalStateException {
        mIsPlaying = true;
        if (mUseMediaPlayer) {
            mMediaPlayer.start();
            // mMediaplayer.seekTo(0) should be called whenever a Track has just been prepared
            // and is being started. This workaround is needed because of a bug in Android 4.4.
            if (mMediaPlayer.getCurrentPosition() == 0) {
                mMediaPlayer.seekTo(0);
            }
        } else {
            if (mSpotifyIsInitalized) {
                SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_PLAY);
            }
        }
    }

    /**
     * Pause playing the current {@link Track}
     */
    public void pause() throws IllegalStateException {
        mIsPlaying = false;
        if (mUseMediaPlayer) {
            mMediaPlayer.pause();
        } else {
            if (mSpotifyIsInitalized) {
                SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_PAUSE);
            }
        }
    }

    /**
     * Stop playing the current {@link Track}
     */
    public void stop() throws IllegalStateException {
        mIsPlaying = false;
        if (mUseMediaPlayer) {
            mMediaPlayer.stop();
        } else {
            if (mSpotifyIsInitalized) {
                SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_PAUSE);
            }
        }
    }

    /**
     * Seek to the given playback position (in ms)
     */
    public void seekTo(int msec) throws IllegalStateException {
        if (mUseMediaPlayer) {
            mMediaPlayer.seekTo(msec);
        } else {
            if (mSpotifyIsInitalized) {
                SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_SEEK, msec);
            }
        }
    }

    /**
     * Prepare the given url
     *
     * @param url          the url to prepare
     * @param isSpotifyUrl whether or not the given url is a spotify url
     */
    public void prepare(String url, boolean isSpotifyUrl)
            throws IllegalStateException, IOException {
        mIsPreparing = true;
        if (isSpotifyUrl) {
            mUseMediaPlayer = false;
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.reset();
            if (mSpotifyIsInitalized) {
                SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_PREPARE, url);
            }
        } else {
            mUseMediaPlayer = true;
            if (mSpotifyIsInitalized) {
                SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_PAUSE);
            }
            mMediaPlayer.setDataSource(url);
            mMediaPlayer.prepare();
        }
    }

    public boolean isPreparing() {
        return mIsPreparing;
    }
}
