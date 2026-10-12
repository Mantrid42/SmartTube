package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Channels explicitly opted into DeArrow, independently of the global options. */
public class ChannelDeArrowData implements AppPrefs.ProfileChangeListener {
    private static final String DATA_KEY = "channel_dearrow_data";
    @SuppressLint("StaticFieldLeak")
    private static ChannelDeArrowData sInstance;
    private final AppPrefs mPrefs;
    private final Set<String> mChannelIds = new HashSet<>();

    private ChannelDeArrowData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        onProfileChanged();
    }

    public static ChannelDeArrowData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelDeArrowData(context.getApplicationContext());
        }
        return sInstance;
    }

    public boolean isEnabled(String channelId) {
        return !TextUtils.isEmpty(channelId) && mChannelIds.contains(channelId);
    }

    public static String getChannelKey(Video channel) {
        return channel == null ? null : !TextUtils.isEmpty(channel.channelId) ? channel.channelId : channel.reloadPageKey;
    }

    public void setEnabled(Video channel, boolean enabled) {
        setEnabled(channel.channelId, enabled);
        // Subscription cards may carry a reload key instead of their canonical channel ID.
        setEnabled(channel.reloadPageKey, enabled);
    }

    public void setEnabled(String channelId, boolean enabled) {
        if (TextUtils.isEmpty(channelId)) {
            return;
        }
        if (enabled) {
            mChannelIds.add(channelId);
        } else {
            mChannelIds.remove(channelId);
        }
        mPrefs.setProfileData(DATA_KEY, Helpers.mergeData(new ArrayList<>(mChannelIds)));
    }

    @Override
    public void onProfileChanged() {
        mChannelIds.clear();
        mChannelIds.addAll(Helpers.parseList(Helpers.splitData(mPrefs.getProfileData(DATA_KEY)), 0, Helpers::parseStr));
    }
}
