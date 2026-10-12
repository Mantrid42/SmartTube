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
    private final Set<String> mExcludedIds = new HashSet<>();
    private boolean mReplaceTitles;
    private boolean mReplaceThumbnails;
    private boolean mUnlocalizedTitles;
    private int mThumbQuality;

    public boolean isUnlocalizedTitlesEnabled() { return mUnlocalizedTitles; }
    public int getThumbQuality() { return mThumbQuality; }

    public int getThumbQuality(Video video, int globalQuality) {
        String key = video.channelId != null ? video.channelId : video.deArrowChannelId;
        return isExcluded(key) ? 0 : isEnabled(key) ? mThumbQuality : globalQuality;
    }

    public void setUnlocalizedTitlesEnabled(boolean enabled) {
        mUnlocalizedTitles = enabled;
        if (enabled) mReplaceTitles = false;
        persistOptions();
    }

    public void setThumbQuality(int quality) {
        mThumbQuality = quality;
        persistOptions();
    }

    public boolean isExcluded(String channelId) {
        return channelId != null && mExcludedIds.contains(channelId);
    }

    public void setExcluded(Video channel, boolean excluded) {
        setExcluded(channel.channelId, excluded);
        setExcluded(channel.reloadPageKey, excluded);
    }

    public void setExcluded(String channelId, boolean excluded) {
        if (TextUtils.isEmpty(channelId)) return;
        if (excluded) mExcludedIds.add(channelId);
        else mExcludedIds.remove(channelId);
        mPrefs.setProfileData(DATA_KEY + "_excluded", Helpers.mergeData(new ArrayList<>(mExcludedIds)));
    }

    public boolean isReplaceTitlesEnabled() { return mReplaceTitles; }
    public boolean isReplaceThumbnailsEnabled() { return mReplaceThumbnails; }

    public void setReplaceTitlesEnabled(boolean enabled) {
        mReplaceTitles = enabled;
        if (enabled) mUnlocalizedTitles = false;
        persistOptions();
    }

    public void setReplaceThumbnailsEnabled(boolean enabled) {
        mReplaceThumbnails = enabled;
        persistOptions();
    }

    private void persistOptions() {
        mPrefs.setProfileData(DATA_KEY + "_options", Helpers.mergeData(mReplaceTitles, mReplaceThumbnails, mUnlocalizedTitles, mThumbQuality));
    }

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
        mExcludedIds.clear();
        mExcludedIds.addAll(Helpers.parseList(Helpers.splitData(mPrefs.getProfileData(DATA_KEY + "_excluded")), 0, Helpers::parseStr));
        String[] options = Helpers.splitData(mPrefs.getProfileData(DATA_KEY + "_options"));
        mReplaceTitles = Helpers.parseBoolean(options, 0, true);
        mReplaceThumbnails = Helpers.parseBoolean(options, 1, true);
        mUnlocalizedTitles = Helpers.parseBoolean(options, 2, false);
        mThumbQuality = Helpers.parseInt(options, 3, 0);
    }
}
