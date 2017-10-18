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

import android.net.Uri;
import android.os.Handler;
import android.text.Html;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TwitchAlerts {
    private WebSocket taWs = null;
    private MainActivity mMain = null;
    private String taToken = null;
    final Handler handler = new Handler();
    private boolean connecting = false;
    private boolean intentionalClose = false;

    public TwitchAlerts(MainActivity main) {
        mMain = main;
    }

    public void handleDonations(JSONArray arr) {
        for (int i = 0; i < arr.length(); ++i) {
            try {
                JSONObject obj = arr.getJSONObject(i);

                String name = obj.getString("name");
                String formatAmount = obj.getString("formattedAmount");
                String message = obj.getString("message");

                String finalText = "<font color=#00802b>" + name + " has donated <b>" + formatAmount + "</b></font>: " + message + "<br />";
                mMain.pushAlert(Html.fromHtml(finalText));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleSubscriptions(JSONArray arr) {
        for (int i = 0; i < arr.length(); ++i) {
            try {
                JSONObject obj = arr.getJSONObject(i);

                String name = obj.getString("name");
                int months = obj.getInt("months");

                String finalText;
                if (months == 1)
                    finalText = "<font color=#0052cc>" + name + " has subscribed!</font><br />";
                else
                    finalText = "<font color=#0052cc>" + name + " has subscribed for <b>"+months+"</b> months in a row!</font><br />";

                mMain.pushAlert(Html.fromHtml(finalText));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleHosts(JSONArray arr) {
        for (int i = 0; i < arr.length(); ++i) {
            try {
                JSONObject obj = arr.getJSONObject(i);

                String name = obj.getString("name");
                int viewers = 0;

                if (!obj.isNull("viewers"))
                    viewers = obj.getInt("viewers");

                String finalText = "<font color=#cc00cc>" + name + " is hosting you for <b>"+viewers+"</b> viewers!</font><br />";
                mMain.pushAlert(Html.fromHtml(finalText));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleFollows(JSONArray arr) {
        for (int i = 0; i < arr.length(); ++i) {
            try {
                JSONObject obj = arr.getJSONObject(i);

                String name = obj.getString("name");

                String finalText = "<font color=#ff6666>" + name + " has followed!</font><br />";
                mMain.pushAlert(Html.fromHtml(finalText));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleBits(JSONArray arr) {
        for (int i = 0; i < arr.length(); ++i) {
            try {
                JSONObject obj = arr.getJSONObject(i);

                String name = obj.getString("name");
                String message = obj.getString("message");
                String amount = message.substring(5, message.indexOf(' '));
                String toDisplay = message.substring(message.indexOf(' ')+1);

                String finalText = "<font color=#000000>" + name + " has donated <b>" + amount + "</b> bits </font>: " + toDisplay + "<br />";
                mMain.pushAlert(Html.fromHtml(finalText));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void start(String taToken) {
        if (connecting)
            return;

        connecting = true;
        this.taToken = taToken;

        if (taWs != null)
            close();

        // TA websocket on which we receive follows, donations, [maybe subs & hosts - untested]
        final AsyncHttpClient.WebSocketConnectCallback mTwitchAlertsCallback = new AsyncHttpClient.WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                connecting = false;

                if (ex != null)
                    return;

                taWs = webSocket;
                mMain.pushAlert(Html.fromHtml("Connected to Streamlabs<br />"));

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        if (s.equals("3")) // PONG
                            return;

                        if (!s.substring(0, 2).equals("42"))
                            return;

                        try {
                            JSONArray arr = new JSONArray(s.substring(2, s.length()));
                            for (int i = 0; i < arr.length(); i += 2) {
                                if (arr.getString(i).equals("donations"))
                                    handleDonations(arr.getJSONArray(i+1));
                                else if (arr.getString(i).equals("subscriptions"))
                                    handleSubscriptions(arr.getJSONArray(i+1));
                                else if (arr.getString(i).equals("hosts"))
                                    handleHosts(arr.getJSONArray(i+1));
                                else if (mMain.isDisplayFollow() && arr.getString(i).equals("follows"))
                                    handleFollows(arr.getJSONArray(i+1));
                                else if (arr.getString(i).equals("bits"))
                                    handleBits(arr.getJSONArray(i+1));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (!intentionalClose)
                            mMain.pushAlert(Html.fromHtml("Lost connection to Streamlabs<br />"));
                        taWs = null;
                        close();
                    }
                });

                webSocket.send("2probe");
            }
        };


        AsyncHttpClient.JSONObjectCallback mTaWsTokenCallback = new AsyncHttpClient.JSONObjectCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, JSONObject result) {
                try {
                    if (e != null || result.isNull("token")) {
                        connecting = false;
                        mMain.pushAlert(Html.fromHtml("Couldn't connect to Streamlabs. The token is a 20-character located at the end of your alert window URL.<br />"));
                        return;
                    }

                    String taWsToken = result.getString("token");
                    Log.d("AWS", "Connecting with token "+taWsToken);
                    AsyncHttpClient.getDefaultInstance().websocket("https://aws-io.streamlabs.com/socket.io/?token="+taWsToken+"&EIO=3&transport=websocket", null, mTwitchAlertsCallback);
                } catch (JSONException ex) {
                    mMain.pushAlert(Html.fromHtml("Couldn't connect to Streamlabs. The token is a 20-character located at the end of your alert window URL.<br />"));
                    e.printStackTrace();
                }
            }
        };

        // We need to ping TA every 25s
        Runnable outer = new Object() {
            public final Runnable r = new Runnable() {
                public void run() {
                    if (taWs != null && taWs.isOpen()) {
                        taWs.send("2");
                        handler.postDelayed(r, 25000);
                    }
                }
            };
        }.r;

        handler.postDelayed(outer, 25000);
        AsyncHttpClient.getDefaultInstance().executeJSONObject(new AsyncHttpRequest(Uri.parse("https://streamlabs.com/service/get-socket-token?token=" + taToken + "&ts=" + System.currentTimeMillis() / 1000), "GET"), mTaWsTokenCallback);
    }

    public void close() {
        if (taWs != null) {
            mMain.pushAlert(Html.fromHtml("Disconnected from Streamlabs<br />"));
            intentionalClose = true;
            taWs.close();
            intentionalClose = false;
        }
        handler.removeCallbacksAndMessages(null);
    }

    public String getToken() {
        return taToken;
    }

    public boolean isActive() {
        return taWs != null;
    }
}
