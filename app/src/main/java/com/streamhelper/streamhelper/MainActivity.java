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

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mobidevelop.spl.widget.SplitPaneLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public TextView textViewChat = null;
    private TextView textViewAlerts = null;
    private SplitPaneLayout spl = null;

    private AlertDialog ad = null;
    private SeekBar fontSizeSeekbar = null;
    private TextView fontSizeHelper = null;
    private CheckBox enableTACb = null;
    private CheckBox displayFollowAlertsCb = null;
    private EditText taTokenText = null;
    private View adView;

    private TwitchChat tc;
    private TwitchAlerts ta;
    private TwitchViewers tv;

    private boolean autoscrollChat = true;
    private boolean autoscrollAlerts = true;
    private boolean displayFollowers = true;
    private boolean displayViewers = true;
    private boolean displayTimestamps = false;
    private boolean enableTA = false;
    private boolean displayWarning = false;

    private Handler h = new Handler();
    private boolean stopped = false;
    private long stopTimestamp = 0;
    private boolean inactiveCheckerRunning = false;

    private int fontSize = 14;

    public void loadSettings() {
        try {
            File f = new File(getFilesDir(), "settings");

            if (!f.exists())
                return;

            FileInputStream fis = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            fis.read(data);
            fis.close();

            String s = new String(data);
            JSONObject obj = new JSONObject(s);

            displayViewers = obj.getBoolean("displayViewers");
            displayFollowers = obj.getBoolean("displayFollowers");
            fontSize = obj.getInt("fontSize");
            enableTA = obj.getBoolean("enableTA");
            if (!obj.isNull("displayTimestamps"))
                displayTimestamps = obj.getBoolean("displayTimestamps");

            CheckBox displayViewersCb = (CheckBox) adView.findViewById(R.id.displayViewersCheckbox);
            displayViewersCb.setChecked(displayViewers);

            CheckBox displayTimestampsCb = (CheckBox) adView.findViewById(R.id.displayTimestampsCheckbox);
            displayTimestampsCb.setChecked(displayTimestamps);
            tc.setDisplayTimestamps(displayTimestamps);

            displayFollowAlertsCb.setChecked(displayFollowers);

            if (!obj.isNull("twitchUser")) {
                EditText et = (EditText) adView.findViewById(R.id.editText);
                et.setText(obj.getString("twitchUser"));
                tc.start(obj.getString("twitchUser"));
                tv.setUser(obj.getString("twitchUser").toLowerCase());
            }

            if (!obj.isNull("twitchAlertsToken")) {
                if (enableTA) {
                    ta.start(obj.getString("twitchAlertsToken"));
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        spl.setSplitterPositionPercent(0.6f);
                    else
                        spl.setSplitterPositionPercent(0.75f);
                }
                taTokenText.setText(obj.getString("twitchAlertsToken"));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    ScrollViewListener svl = new ScrollViewListener() {
        @Override
        public void onScrollChanged(ScrollViewExt scrollView, int x, int y, int oldx, int oldy) {
            // We take the last son in the scrollview
            View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
            int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));

            if (diff <= 80)
                autoscrollChat = true;
            else
                autoscrollChat = false;
        }
    };

    ScrollViewListener sv2 = new ScrollViewListener() {
        @Override
        public void onScrollChanged(ScrollViewExt scrollView, int x, int y, int oldx, int oldy) {
            // We take the last son in the scrollview
            View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
            int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));

            // if diff is zero, then the bottom has been reached
            if (diff <= 80)
                autoscrollAlerts = true;
            else
                autoscrollAlerts = false;
        }
    };

    private void startInactiveChecker() {
        if (inactiveCheckerRunning)
            return;

        inactiveCheckerRunning = true;

        Runnable outer = new Object() {
            public final Runnable r = new Runnable() {
                public void run() {
                    if (stopped && System.currentTimeMillis()/1000 - stopTimestamp >= 180) {
                        inactiveCheckerRunning = false;
                        stopped = false;
                        ta.close();
                        tc.close();
                        tv.close();
                    } else
                        h.postDelayed(r, 20000);
                }
            };
        }.r;

        h.postDelayed(outer, 20000);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spl.setOrientation(SplitPaneLayout.ORIENTATION_HORIZONTAL);
            spl.setSplitterPositionPercent(0.6f);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            spl.setOrientation(SplitPaneLayout.ORIENTATION_VERTICAL);
            spl.setSplitterPositionPercent(0.75f);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startInactiveChecker();
        setContentView(R.layout.activity_main);

        textViewChat = (TextView) findViewById(R.id.textView);
        textViewAlerts = (TextView) findViewById(R.id.textView2);
        ta = new TwitchAlerts(this);
        tc = new TwitchChat(this);
        tv = new TwitchViewers(this);

        spl = (SplitPaneLayout)findViewById(R.id.spl);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final ScrollViewExt sc = (ScrollViewExt)findViewById(R.id.scrollView);
        sc.setScrollViewListener(svl);

        final ScrollViewExt sc2 = (ScrollViewExt)findViewById(R.id.scrollView2);
        sc2.setScrollViewListener(sv2);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View v = inflater.inflate(R.layout.settings, null);
        builder.setView(v);

        adView = v;
        ad = builder.create();

        displayFollowAlertsCb = (CheckBox) v.findViewById(R.id.displayFollowCheckbox);
        taTokenText = (EditText) v.findViewById(R.id.editText2);
        enableTACb = (CheckBox) v.findViewById(R.id.enableTACheckbox);
        enableTACb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    taTokenText.setEnabled(false);
                    taTokenText.setError(null);
                    displayFollowAlertsCb.setEnabled(false);
                    return;
                }

                taTokenText.setEnabled(true);
                displayFollowAlertsCb.setEnabled(true);

                if (!displayWarning)
                    return;

                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Warning");
                alertDialog.setMessage("Always start your stream before connecting to Streamlabs with the app, or some alerts might not show up.\n\nYou can also disconnect/reconnect Streamlabs from the app if you started your stream too late.\n\nYour SL token is a 20-character string located at the end of your alert window URL.");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Got it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();

                displayWarning = false;
            }
        });

        loadSettings();
        tv.init();

        if (!enableTA) {
            taTokenText.setEnabled(false);
            displayFollowAlertsCb.setEnabled(false);
        } else {
            enableTACb.setChecked(true);
        }

        displayWarning = true;

        fontSizeHelper = (TextView) v.findViewById(R.id.textView3);
        fontSizeHelper.setTextSize(fontSize);
        textViewAlerts.setTextSize(fontSize);
        textViewChat.setTextSize(fontSize);

        fontSizeSeekbar = (SeekBar) v.findViewById(R.id.seekBar);
        fontSizeSeekbar.setProgress(fontSize);
        fontSizeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fontSizeHelper.setTextSize(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        taTokenText.setFilters(new InputFilter[]{new InputFilter.AllCaps()});

        Button done = (Button) v.findViewById(R.id.settingsDoneButton);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String taKey = taTokenText.getText().toString();
                enableTA = enableTACb.isChecked();

                if (enableTA && taKey.length() != 20) {
                    taTokenText.setError("Your TA token is a 20-character string located at the end of your alert window URL.");
                    return;
                }

                ad.dismiss();

                EditText chan = (EditText) adView.findViewById(R.id.editText);
                String user = chan.getText().toString();

                if (user.length() >= 4 && (!tc.isActive() || !user.equals(tc.getUser()))) {
                    tc.start(user);
                    tv.setUser(user.toLowerCase());
                    tv.refresh();
                }

                if (enableTA && (!ta.isActive() || !taKey.equals(ta.getToken()))) {
                    ta.start(taKey);
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        spl.setSplitterPositionPercent(0.6f);
                    else
                        spl.setSplitterPositionPercent(0.75f);
                }
                else if (!enableTA)
                    ta.close();

                fontSize = fontSizeSeekbar.getProgress();
                textViewAlerts.setTextSize(fontSize);
                textViewChat.setTextSize(fontSize);

                CheckBox displayViewersCb = (CheckBox) adView.findViewById(R.id.displayViewersCheckbox);

                if (!displayViewers && displayViewersCb.isChecked()) {
                    tv.refresh();
                }

                CheckBox displayTimestampsCb = (CheckBox) adView.findViewById(R.id.displayTimestampsCheckbox);
                displayTimestamps = displayTimestampsCb.isChecked();
                tc.setDisplayTimestamps(displayTimestamps);

                displayViewers = displayViewersCb.isChecked();

                if (!displayViewers) {
                    TextView views = (TextView) findViewById(R.id.viewersView);
                    views.setText("");
                }

                displayFollowers = displayFollowAlertsCb.isChecked();

                FileOutputStream fos = null;
                try {
                    File dir = getFilesDir();
                    File file = new File(dir, "settings");
                    file.delete();
                    fos = openFileOutput("settings", Context.MODE_PRIVATE);
                    fos.flush();

                    JSONObject settings = new JSONObject();
                    settings.put("displayFollowers", displayFollowers);
                    settings.put("displayViewers", displayViewers);
                    settings.put("displayTimestamps", displayTimestamps);
                    settings.put("fontSize", fontSize);
                    settings.put("enableTA", enableTA);

                    if (tc.getUser() != null && tc.getUser().length() >= 4)
                        settings.put("twitchUser", tc.getUser());

                    if (taKey.length() > 0)
                        settings.put("twitchAlertsToken", taKey);

                    fos.write(settings.toString().getBytes());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void settingsClicked(View v) {
        ad.show();
    }

    @Override
    public void onStop() {
        super.onStop();

        stopped = true;
        stopTimestamp = System.currentTimeMillis()/1000;

    }

    @Override
    public void onResume() {
        super.onResume();

        stopped = false;

        if (!inactiveCheckerRunning) {
            if (!tc.isActive() && tc.getUser() != null) {
                tc.start(tc.getUser());
                tv.init();
            }

            if (!ta.isActive() && enableTA && ta.getToken() != null)
                ta.start(ta.getToken());

            startInactiveChecker();
        }
    }

    public synchronized void pushChat(final Spanned s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewChat.append(s);

                int excessLineNumber = textViewChat.getLineCount() - 200;
                if (excessLineNumber > 0)
                    textViewChat.setText(textViewChat.getText().subSequence(textViewChat.getText().length() / 30, textViewChat.getText().length()));

                if (autoscrollChat) {
                    final ScrollViewExt sc = (ScrollViewExt) findViewById(R.id.scrollView);
                    sc.post(new Runnable() {
                        @Override
                        public void run() {
                            sc.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }

    public synchronized void pushAlert(final Spanned s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewAlerts.append(s);

                int excessLineNumber = textViewAlerts.getLineCount() - 200;
                if (excessLineNumber > 0)
                    textViewAlerts.setText(textViewAlerts.getText().subSequence(textViewAlerts.getText().length() / 30, textViewAlerts.getText().length()));

                if (autoscrollAlerts) {
                    final ScrollView sc = (ScrollView) findViewById(R.id.scrollView2);
                    sc.post(new Runnable() {
                        @Override
                        public void run() {
                            sc.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }

    public void setChannel(final String channel) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView v = (TextView) findViewById(R.id.channelView);

                if (channel != null)
                    v.setText(Html.fromHtml("<b>" + channel + "</b>"));
                else
                    v.setText("Not Connected");
            }
        });

        if (channel != null)
            tv.setUser(channel.toLowerCase());
        else
            tv.setUser(null);
    }

    public void setViewers(final int viewers) {
        if (!displayViewers)
            return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView v = (TextView) findViewById(R.id.viewersView);

                SpannableStringBuilder ssb;

                if (viewers == -1)
                    ssb = new SpannableStringBuilder("  -");
                else
                    ssb = new SpannableStringBuilder("  " + NumberFormat.getNumberInstance(Locale.US).format(viewers));

                Drawable d = getResources().getDrawable(R.drawable.twitch_viewers);
                d.setBounds(0, 0, v.getLineHeight(), v.getLineHeight());
                ssb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                v.setText(ssb);
            }
        });
    }

    public boolean isDisplayFollow() {
        return displayFollowers;
    }
}
