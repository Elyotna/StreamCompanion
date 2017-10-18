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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitchChat {

    private boolean intentionalClose = false;

    public class User {
        public String color;
        public int lastTimestamp;

        public User(String color, int lastTimestamp) {
            this.color = color;
            this.lastTimestamp = lastTimestamp;
        }
    }

    private WebSocket chatWs = null;
    private MainActivity mMain;
    private String mUser = null;
    private static final Pattern patternChatMessage = Pattern.compile("display-name=([a-zA-Z0-9_]{4,25});.*?PRIVMSG.*?:(.*)");
    private static final Pattern patternChatMessage2 = Pattern.compile("!([a-zA-Z0-9_]{4,25})@.*?PRIVMSG.*?:(.*)");

    private static final Pattern patternBadges = Pattern.compile("badges=([a-zA-Z/0-9,]+);");
    private static final Pattern patternEmotes = Pattern.compile("emotes=([0-9,/:-]+);");
    private static final Pattern patternSubEmote = Pattern.compile("(.*?)/");
    private static final Pattern patternSubSubEmote = Pattern.compile("([0-9]+)-([0-9]+),");

    private boolean displayTimestamps = false;

    private HashMap<String, User> users = new HashMap<String, User>();

    final static String COLORS[] = {
            "#e21400", "#58dc00", "#f8a700", "#a700ff",
            "#91580f", "#287b00", "#a8f07a", "#4ae8c4",
            "#3b88eb", "#3824aa", "#f78b00", "#d300e7"
    };

    int curColor = 0;
    private boolean connecting = false;
    private HashMap<Integer, Drawable> emotes = new HashMap<Integer, Drawable>();
    private Drawable subBadge = null;

    public TwitchChat(MainActivity main) {
        mMain = main;
    }

    public Drawable getSubBadge() {
        if (subBadge == null)
            fetchBadge();

        return subBadge;
    }

    public static byte[] fetchBytes(String sUrl) {
        InputStream input = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(sUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return null;

            input = connection.getInputStream();

            ByteArrayOutputStream data = new ByteArrayOutputStream();

            byte tmp[] = new byte[4096];
            int count;
            while ((count = input.read(tmp)) != -1)
                data.write(tmp, 0, count);

            return data.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
    }

    private void fetchBadge() {
        byte data[] = fetchBytes("https://api.twitch.tv/api/channels/"+mUser.toLowerCase()+"/product");
        if (data == null)
            return;

        String json = new String(data);
        try {
            JSONObject obj = new JSONObject(json);

            if (obj.isNull("badge"))
                return;

            byte badgeData[] = fetchBytes(obj.getString("badge"));

            if (badgeData == null)
                return;

            Bitmap bitmap = BitmapFactory.decodeByteArray(badgeData, 0, badgeData.length);

            if (bitmap == null) {
                Log.d("TC", "Couldn't decode sub bitmap!");
                return;
            }

            subBadge = new BitmapDrawable(mMain.getResources(), bitmap);
        } catch (JSONException e) {
            return;
        }
    }

    public synchronized Drawable getEmote(int id) {
        if (emotes.containsKey(id))
            return emotes.get(id);

        checkEmotesDir();

        File f = new File(mMain.getFilesDir(), "emotes/"+id+".png");
        if (!f.exists() && !downloadEmote(id))
            return null;

        f = new File(mMain.getFilesDir(), "emotes/"+id+".png");

        Drawable d = new BitmapDrawable(mMain.getResources(), f.getAbsolutePath());
        emotes.put(id, d);

        return d;
    }

    private boolean downloadEmote(int id) {

        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL("https://static-cdn.jtvnw.net/emoticons/v1/"+id+"/1.0");
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return false;

            // download the file
            input = connection.getInputStream();
            File f = new File(mMain.getFilesDir(), "emotes/"+id+".png");
            output = new FileOutputStream(f);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);
            }

            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
    }

    private void checkEmotesDir() {
        File f = new File(mMain.getFilesDir(), "emotes");
        if (!f.exists())
            f.mkdir();
    }

    private static void withinStyle(StringBuilder out, CharSequence text,
                                    int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);

            if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                if (c < 0xDC00 && i + 1 < end) {
                    char d = text.charAt(i + 1);
                    if (d >= 0xDC00 && d <= 0xDFFF) {
                        i++;
                        int codepoint = 0x010000 | (int) c - 0xD800 << 10 | (int) d - 0xDC00;
                        out.append("&#").append(codepoint).append(";");
                    }
                }
            } else if (c > 0x7E || c < ' ') {
                out.append("&#").append((int) c).append(";");
            } else if (c == ' ') {
                while (i + 1 < end && text.charAt(i + 1) == ' ') {
                    out.append("&nbsp;");
                    i++;
                }

                out.append(' ');
            } else {
                out.append(c);
            }
        }
    }

    public static String escapeHtml(CharSequence text) {
        StringBuilder out = new StringBuilder();
        withinStyle(out, text, 0, text.length());
        return out.toString();
    }

    public void start(String user) {
        if (connecting)
            return;

        if (chatWs != null)
            close();

        connecting = true;
        final String channel = "#"+user.toLowerCase();
        mUser = user;
        subBadge = null;

        class MySpan implements LineHeightSpan {
            private final int height;

            public MySpan(int height) {
                this.height = height;
            }

            @Override
            public void chooseHeight(CharSequence text, int start, int end,
                                     int spanstartv, int v, Paint.FontMetricsInt fm) {
                Spanned spanned = (Spanned) text;
                int st = spanned.getSpanStart(this);
                int en = spanned.getSpanEnd(this);

                if (end == en) {
                    fm.descent -= height;
                }
            }
        }

        AsyncHttpClient.WebSocketConnectCallback mWebSocketConnectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                connecting = false;
                if (ex != null) {
                    mMain.pushChat(Html.fromHtml("Couldn't connect to channel <b>#"+mUser+"</b><br />"));
                    ex.printStackTrace();
                    return;
                }

                chatWs = webSocket;
                mMain.pushChat(Html.fromHtml("Connected to channel <b>#"+mUser+"</b><br />"));
                mMain.setChannel(mUser);

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        if (s.length() >= 4 && s.substring(0, 4).equalsIgnoreCase("PING")) {
                            chatWs.send("PONG");
                            return;
                        }

                        Matcher m = patternChatMessage.matcher(s);

                        String name;
                        String message;

                        if (!m.find()) {
                            Matcher m2 = patternChatMessage2.matcher(s);
                            if (!m2.find())
                                return;

                            name = m2.group(1);
                            message = m2.group(2);
                        } else {
                            name = m.group(1);
                            message = m.group(2);
                        }

                        User u = users.get(name);
                        if (u == null) {
                            u = new User(COLORS[(curColor++)], 0);
                            if (curColor == COLORS.length)
                                curColor = 0;

                            users.put(name, u);
                        }

                        SpannableStringBuilder ssb = new SpannableStringBuilder();

                        if (displayTimestamps) {
                            Calendar c = Calendar.getInstance();
                            ssb.append("["+String.format("%02d",c.get(Calendar.HOUR_OF_DAY))+":"+String.format("%02d", c.get(Calendar.MINUTE))+"] ");
                        }

                        Matcher badgeMatcher = patternBadges.matcher(s);
                        if (badgeMatcher.find()) {
                            String badges = badgeMatcher.group(1);

                            if (badges.contains("moderator")) {
                                ssb.append(" ");
                                Drawable d = mMain.getResources().getDrawable(R.mipmap.ic_mod);
                                d.setBounds(0, 0, mMain.textViewChat.getLineHeight(), mMain.textViewChat.getLineHeight());
                                ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);
                                ssb.setSpan(span, ssb.length()-1, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }

                            if (badges.contains("subscriber")) {
                                Drawable d = getSubBadge();
                                if (d != null) {
                                    ssb.append(" ");

                                    int size = (int)(mMain.textViewChat.getLineHeight()*1.2);

                                    double aspectRatio = (double)(d.getIntrinsicWidth()) / d.getIntrinsicHeight();
                                    if (d.getIntrinsicWidth() > d.getIntrinsicHeight())
                                        d.setBounds(0, 0, mMain.textViewChat.getLineHeight(), (int)(mMain.textViewChat.getLineHeight()/aspectRatio));
                                    else
                                        d.setBounds(0, 0, (int)(mMain.textViewChat.getLineHeight()*aspectRatio), mMain.textViewChat.getLineHeight());

                                    ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);
                                    ssb.setSpan(span, ssb.length()-1, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            }

                            if ((displayTimestamps && ssb.length() > 8) || (!displayTimestamps && ssb.length() > 0))
                                ssb.append(" ");
                        }

                        int userIndex = ssb.length();
                        ssb.append(name);
                        ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), userIndex, userIndex+name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ssb.setSpan(new ForegroundColorSpan(Color.parseColor(u.color)), userIndex, userIndex+name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        int startMessage = ssb.length() + 2;
                        ssb.append(": "+message+"\n\n");

                        Matcher emoteMatcher = patternEmotes.matcher(s);

                        if (emoteMatcher.find()) {
                            Matcher emoteSubMatcher = patternSubEmote.matcher(emoteMatcher.group(1)+"/");

                            while (emoteSubMatcher.find()) {
                                Matcher emoteSubSubMatcher = patternSubSubEmote.matcher(emoteSubMatcher.group(1)+",");

                                while (emoteSubSubMatcher.find()) {
                                    int emoteIdEnd = 0;
                                    while (emoteSubMatcher.group(1).charAt(++emoteIdEnd) != ':');

                                    int emoteId = Integer.parseInt(emoteSubMatcher.group(1).substring(0, emoteIdEnd));
                                    int start = Integer.parseInt(emoteSubSubMatcher.group(1));
                                    int end = Integer.parseInt(emoteSubSubMatcher.group(2));

                                    Drawable d = getEmote(emoteId);
                                    double aspectRatio = (double)(d.getIntrinsicWidth()) / d.getIntrinsicHeight();
                                    int size = (int)(mMain.textViewChat.getLineHeight()*1.2);

                                    if (d.getIntrinsicWidth() > d.getIntrinsicHeight())
                                        d.setBounds(0, 0, size, (int)(size/aspectRatio));
                                    else
                                        d.setBounds(0, 0, (int)(size*aspectRatio), size);

                                    ssb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BOTTOM), startMessage+start, startMessage+end+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            }
                        }

                        int lineHeight = (int)(9*(mMain.textViewChat.getTextSize()/14.0));
                        ssb.setSpan(new MySpan(lineHeight), ssb.length()-1, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        mMain.pushChat(ssb);
                    }
                });

                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (!intentionalClose)
                            mMain.pushChat(Html.fromHtml("Lost connection to channel <b>#"+mUser+"</b><br />"));

                        mMain.setChannel(null);
                        chatWs = null;
                    }
                });

                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands");
                webSocket.send("PASS blah");
                webSocket.send("NICK justinfan645564");
                webSocket.send("JOIN "+channel);
            }
        };

        AsyncHttpClient.getDefaultInstance().websocket("wss://irc-ws.chat.twitch.tv", null, mWebSocketConnectCallback);
    }

    public void close() {
        if (chatWs != null) {
            intentionalClose = true;
            chatWs.close();
            intentionalClose = false;
        }

        users.clear();
    }

    public String getUser() {
        return mUser;
    }

    public boolean isActive() {
        return chatWs != null;
    }

    public void setDisplayTimestamps(boolean displayTimestamps) {
        this.displayTimestamps = displayTimestamps;
    }
}
