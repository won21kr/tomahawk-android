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
package org.tomahawk.libtomahawk.authentication;

import org.codehaus.jackson.map.ObjectMapper;
import org.tomahawk.libtomahawk.infosystem.InfoSystemUtils;
import org.tomahawk.libtomahawk.resolver.spotify.SpotifyLogin;
import org.tomahawk.libtomahawk.resolver.spotify.SpotifyServiceUtils;
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.services.SpotifyService;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

public class SpotifyAuthenticatorUtils extends AuthenticatorUtils {

    // Used for debug logging
    private final static String TAG = SpotifyAuthenticatorUtils.class.getName();

    // String tags used to store Spotify's preferred bitrate
    public static final String SPOTIFY_PREF_BITRATE
            = "org.tomahawk.tomahawk_android.spotify_pref_bitrate";

    public static final int SPOTIFY_PREF_BITRATE_MODE_LOW = 0;

    public static final int SPOTIFY_PREF_BITRATE_MODE_MEDIUM = 1;

    public static final int SPOTIFY_PREF_BITRATE_MODE_HIGH = 2;

    private Messenger mToSpotifyMessenger = null;

    private final Messenger mFromSpotifyMessenger = new Messenger(new FromSpotifyHandler());

    private ObjectMapper mObjectMapper;

    /**
     * Handler of incoming messages from the SpotifyService's messenger.
     */
    private class FromSpotifyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case SpotifyService.MSG_ONINIT:
                        onInit();
                        break;
                    case SpotifyService.MSG_ONLOGIN:
                        onLogin(msg.getData().getString(SpotifyService.STRING_KEY));
                        break;
                    case SpotifyService.MSG_ONLOGINFAILED:
                        onLoginFailed(msg.getData().getString(SpotifyService.STRING_KEY));
                        break;
                    case SpotifyService.MSG_ONLOGOUT:
                        onLogout();
                        break;
                    case SpotifyService.MSG_ONCREDBLOBUPDATED:
                        SpotifyLogin spotifyLogin = mObjectMapper
                                .readValue(msg.getData().getString(SpotifyService.STRING_KEY),
                                        SpotifyLogin.class);
                        onAuthTokenProvided(spotifyLogin.username, spotifyLogin.blob, 0, null, 0);
                        break;
                    default:
                        super.handleMessage(msg);
                }
            } catch (IOException e) {
                Log.e(TAG, "handleMessage: " + e.getClass() + ": " + e.getLocalizedMessage());
            }
        }
    }

    public SpotifyAuthenticatorUtils(Context context) {
        mContext = context;

        mObjectMapper = InfoSystemUtils.constructObjectMapper();
    }

    public void setToSpotifyMessenger(Messenger toSpotifyMessenger) {
        mToSpotifyMessenger = toSpotifyMessenger;
        if (mToSpotifyMessenger != null) {
            SpotifyServiceUtils.registerMsg(mToSpotifyMessenger, mFromSpotifyMessenger);
        }
    }

    public void onInit() {
        loginWithToken();
    }

    public void onLogin(String username) {
        Log.d(TAG,
                "TomahawkService: Spotify user '" + username + "' logged in successfully :)");
    }

    public void onLoginFailed(final String message) {
        Log.d(TAG, "TomahawkService: Spotify login failed :(, Error: " + message);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            }
        });
        mIsAuthenticating = false;
    }

    public void onLogout() {
        Log.d(TAG, "TomahawkService: Spotify user logged out");
        AuthenticatorManager.getInstance()
                .onLoggedInOut(AuthenticatorManager.AUTHENTICATOR_ID_SPOTIFY, false);
        mIsAuthenticating = false;
    }

    /**
     * Store the given blob-string, so we can relogin in a later session
     */
    public void onAuthTokenProvided(String username, String refreshToken,
            int refreshTokenExpiresIn, String accessToken, int accessTokenExpiresIn) {
        if (username != null && !TextUtils.isEmpty(username) && refreshToken != null
                && !TextUtils.isEmpty(refreshToken)) {
            Log.d(TAG, "TomahawkService: Spotify blob is served and yummy");
            Account account = new Account(username,
                    mContext.getString(R.string.accounttype_string));
            AccountManager am = AccountManager.get(mContext);
            if (am != null) {
                am.addAccountExplicitly(account, null, new Bundle());
                am.setUserData(account, AuthenticatorUtils.AUTHENTICATOR_NAME,
                        getAuthenticatorUtilsName());
                am.setAuthToken(account, getAuthenticatorUtilsTokenType(), refreshToken);
            }
        }
        AuthenticatorManager.getInstance()
                .onLoggedInOut(AuthenticatorManager.AUTHENTICATOR_ID_SPOTIFY, true);
        mIsAuthenticating = false;
    }

    public void setBitrate(int bitrate) {
        if (mToSpotifyMessenger != null) {
            SpotifyServiceUtils
                    .sendMsg(mToSpotifyMessenger, SpotifyService.MSG_SETBITRATE, bitrate);
        }
    }

    @Override
    public int getTitleResourceId() {
        return R.string.authenticator_title_spotify;
    }

    @Override
    public int getIconResourceId() {
        return R.drawable.spotify_icon;
    }

    @Override
    public String getAuthenticatorUtilsName() {
        return AuthenticatorUtils.AUTHENTICATOR_NAME_SPOTIFY;
    }

    @Override
    public String getAuthenticatorUtilsTokenType() {
        return AuthenticatorUtils.AUTH_TOKEN_TYPE_SPOTIFY;
    }

    @Override
    public int getUserIdEditTextHintResId() {
        return R.string.logindialog_emailorusername_label_string;
    }

    /**
     * Try to login to spotify with given credentials
     */
    @Override
    public void login(String email, String password) {
        if (mToSpotifyMessenger != null && email != null && password != null) {
            SpotifyLogin spotifyLogin = new SpotifyLogin();
            spotifyLogin.username = email;
            spotifyLogin.password = password;
            mIsAuthenticating = true;
            try {
                String jsonString = mObjectMapper.writeValueAsString(spotifyLogin);
                SpotifyServiceUtils
                        .sendMsg(mToSpotifyMessenger, SpotifyService.MSG_LOGIN, jsonString);
            } catch (IOException e) {
                Log.e(TAG, "login: " + e.getClass() + ": " + e.getLocalizedMessage());
                mIsAuthenticating = false;
            }
        }
    }

    /**
     * Try to login to spotify with stored credentials
     */
    public void loginWithToken() {
        if (mToSpotifyMessenger != null) {
            Account account = TomahawkUtils.getAccountByName(mContext, getAuthenticatorUtilsName());
            if (account != null) {
                String blob = TomahawkUtils.peekAuthTokenForAccount(mContext,
                        getAuthenticatorUtilsName(),
                        getAuthenticatorUtilsTokenType());
                String email = account.name;
                if (email != null && blob != null) {
                    SpotifyLogin spotifyLogin = new SpotifyLogin();
                    spotifyLogin.username = email;
                    spotifyLogin.blob = blob;
                    mIsAuthenticating = true;
                    try {
                        String jsonString = mObjectMapper.writeValueAsString(spotifyLogin);
                        SpotifyServiceUtils
                                .sendMsg(mToSpotifyMessenger, SpotifyService.MSG_LOGIN, jsonString);
                    } catch (IOException e) {
                        Log.e(TAG,
                                "loginWithToken: " + e.getClass() + ": " + e.getLocalizedMessage());
                        mIsAuthenticating = false;
                    }
                }
            }
        }
    }

    /**
     * Logout spotify
     */
    @Override
    public void logout() {
        if (mToSpotifyMessenger != null) {
            mIsAuthenticating = true;
            final AccountManager am = AccountManager.get(mContext);
            Account account = TomahawkUtils.getAccountByName(mContext, getAuthenticatorUtilsName());
            if (am != null && account != null) {
                am.removeAccount(
                        TomahawkUtils.getAccountByName(mContext, getAuthenticatorUtilsName()),
                        null, null);
            }
            SpotifyServiceUtils.sendMsg(mToSpotifyMessenger, SpotifyService.MSG_LOGOUT);
        }
    }
}
