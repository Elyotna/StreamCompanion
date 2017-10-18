/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.streamhelper.streamhelper;

import android.os.Handler;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.callback.HttpConnectCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

public class TwitchViewers {
    private int viewers = 0;
    final Handler handler = new Handler();
    private MainActivity mMain;
    private String mUser = null;

    public void refresh() {
        if (mUser == null)
            return;

        final DataCallback dc = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                try {
                    String result = new String(bb.getAllByteArray(), "UTF-8");
                    JSONObject reader = new JSONObject(result);
                    JSONObject stream = reader.getJSONObject("stream");

                    if (stream == null)
                        viewers = -1;
                    else
                        viewers = stream.getInt("viewers");

                } catch (JSONException e) {
                    viewers = -1;
                } catch (UnsupportedEncodingException e) {
                    viewers = -1;
                }

                mMain.setViewers(viewers);

                bb.recycle();
            }
        };

        HttpConnectCallback mTaWsTokenCallback = new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null)
                    return;

                response.setDataCallback(dc);
            }
        };

        AsyncHttpClient.getDefaultInstance().execute("https://api.twitch.tv/kraken/streams/"+mUser, mTaWsTokenCallback);
    }

    public void init() {
        handler.removeCallbacksAndMessages(null);

        Runnable outer = new Object() {
            public final Runnable r = new Runnable() {
                public void run() {
                    refresh();
                    handler.postDelayed(r, 30000);
                }
            };
        }.r;

        handler.post(outer);
    }

    TwitchViewers(MainActivity main) {
        mMain = main;
    }

    public void setUser(String user) {
        mUser = user;
    }

    public void close() {
        handler.removeCallbacksAndMessages(null);
    }
}
