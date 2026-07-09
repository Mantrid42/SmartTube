package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class ChannelNewContentData implements ProfileChangeListener {
    private static final String CHANNEL_NEW_CONTENT_DATA = "channel_new_content_data";
    private static final String VIDEO_LIST_DELIM = "&cnvl;";
    private static final int MAX_LATEST_ITEMS = 50;
    @SuppressLint("StaticFieldLeak")
    private static ChannelNewContentData sInstance;
    private final AppPrefs mPrefs;
    private List<Channel> mChannels;
    private final Runnable mPersistStateInt = this::persistStateInt;

    private static class Channel {
        public String key;
        public String channelId;
        public String title;
        public String iconUrl;
        public int state;
        public boolean manualNewContent;
        public List<String> seenVideoIds;
        public List<String> latestVideoIds;

        public Channel(String key) {
            this.key = key;
            this.seenVideoIds = new ArrayList<>();
            this.latestVideoIds = new ArrayList<>();
        }

        public static Channel fromString(String spec) {
            String[] split = Helpers.splitObj(spec);

            if (split == null || split.length < 8) {
                return null;
            }

            Channel channel = new Channel(Helpers.parseStr(split[0]));
            channel.channelId = Helpers.parseStr(split[1]);
            channel.title = Helpers.parseStr(split[2]);
            channel.iconUrl = Helpers.parseStr(split[3]);
            channel.state = Helpers.parseInt(split[4], Video.CHANNEL_NEW_CONTENT_NONE);
            channel.manualNewContent = Helpers.parseBoolean(split[5]);
            channel.seenVideoIds = Helpers.parseList(split, 6, VIDEO_LIST_DELIM, Helpers::parseStr);
            channel.latestVideoIds = Helpers.parseList(split, 7, VIDEO_LIST_DELIM, Helpers::parseStr);

            return TextUtils.isEmpty(channel.key) ? null : channel;
        }

        public boolean update(Video video) {
            boolean changed = false;

            if (video == null) {
                return false;
            }

            if (!TextUtils.isEmpty(video.channelId) && !Helpers.equals(channelId, video.channelId)) {
                channelId = video.channelId;
                changed = true;
            }

            if (!TextUtils.isEmpty(video.getTitle()) && !Helpers.equals(title, video.getTitle())) {
                title = video.getTitle();
                changed = true;
            }

            if (!TextUtils.isEmpty(video.getCardImageUrl()) && !Helpers.equals(iconUrl, video.getCardImageUrl())) {
                iconUrl = video.getCardImageUrl();
                changed = true;
            }

            return changed;
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(
                    key,
                    channelId,
                    title,
                    iconUrl,
                    state,
                    manualNewContent,
                    Helpers.mergeList(VIDEO_LIST_DELIM, seenVideoIds),
                    Helpers.mergeList(VIDEO_LIST_DELIM, latestVideoIds));
        }
    }

    private ChannelNewContentData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static ChannelNewContentData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelNewContentData(context.getApplicationContext());
        }

        return sInstance;
    }

    public boolean apply(Video channel) {
        if (channel == null) {
            return false;
        }

        Channel item = findChannel(channel);
        boolean changed = false;

        if (channel.hasNewContent && !isNewContentState(channel.channelNewContentState)) {
            item = obtainChannel(channel);

            if (item != null) {
                changed = item.update(channel);

                if (item.state != Video.CHANNEL_NEW_CONTENT_UNVERIFIED) {
                    item.state = Video.CHANNEL_NEW_CONTENT_UNVERIFIED;
                    changed = true;
                }
            }
        } else if (item != null && item.state == Video.CHANNEL_NEW_CONTENT_UNVERIFIED) {
            item.state = Video.CHANNEL_NEW_CONTENT_NOT_NEW;
            changed = true;
        }

        applyTo(channel, item);

        if (changed) {
            persistState();
        }

        return item != null;
    }

    public void setVerified(Video channel, MediaGroup mediaGroup) {
        Channel item = obtainChannel(channel);

        if (item == null || mediaGroup == null) {
            return;
        }

        String channelId = mediaGroup.getChannelId();
        if (!TextUtils.isEmpty(channelId)) {
            item.channelId = channelId;
            channel.channelId = channelId;
        }

        item.update(channel);

        List<String> latestVideoIds = getVideoIds(mediaGroup.getMediaItems());
        boolean hasNewContent;

        if (latestVideoIds.isEmpty()) {
            hasNewContent = false;
        } else if (item.seenVideoIds.isEmpty()) {
            hasNewContent = !isFirstVisibleVideoWatched(mediaGroup);

            if (!hasNewContent) {
                item.seenVideoIds = new ArrayList<>(latestVideoIds);
            }
        } else {
            hasNewContent = containsNewVideoIds(latestVideoIds, item.seenVideoIds);
        }

        if (item.manualNewContent) {
            hasNewContent = true;
        }

        item.latestVideoIds = latestVideoIds;
        item.state = hasNewContent ? Video.CHANNEL_NEW_CONTENT_VERIFIED : Video.CHANNEL_NEW_CONTENT_NOT_NEW;

        if (!hasNewContent) {
            item.manualNewContent = false;
        }

        applyTo(channel, item);
        persistState();
    }

    public void markWatched(Video channel, @Nullable List<Video> videos) {
        markWatchedInt(channel, getVideoIdsFromVideos(videos));
    }

    public void markWatched(Video channel, @Nullable MediaGroup mediaGroup) {
        markWatchedInt(channel, mediaGroup != null ? getVideoIds(mediaGroup.getMediaItems()) : null);
    }

    private void markWatchedInt(Video channel, @Nullable List<String> videoIds) {
        Channel item = obtainChannel(channel);

        if (item == null) {
            return;
        }

        List<String> latestVideoIds = videoIds != null ? videoIds : new ArrayList<>();

        if (latestVideoIds.isEmpty()) {
            latestVideoIds = new ArrayList<>(item.latestVideoIds);
        }

        if (!latestVideoIds.isEmpty()) {
            item.seenVideoIds = new ArrayList<>(latestVideoIds);
            item.latestVideoIds = latestVideoIds;
        }

        item.manualNewContent = false;
        item.state = Video.CHANNEL_NEW_CONTENT_WATCHED;
        channel.channelNewContentSeenTimeMs = System.currentTimeMillis();
        item.update(channel);
        applyTo(channel, item);
        persistState();
    }

    public void markUnseen(Video channel) {
        Channel item = obtainChannel(channel);

        if (item == null) {
            return;
        }

        item.manualNewContent = true;
        item.state = Video.CHANNEL_NEW_CONTENT_VERIFIED;
        channel.channelNewContentSeenTimeMs = 0;
        item.update(channel);
        applyTo(channel, item);
        persistState();
    }

    public static boolean isNewContentState(int state) {
        return state == Video.CHANNEL_NEW_CONTENT_UNVERIFIED ||
                state == Video.CHANNEL_NEW_CONTENT_VERIFIED;
    }

    private static int getSortRank(Video video) {
        if (video == null) {
            return 3;
        }

        switch (video.channelNewContentState) {
            case Video.CHANNEL_NEW_CONTENT_UNVERIFIED:
            case Video.CHANNEL_NEW_CONTENT_VERIFIED:
                return 0;
            case Video.CHANNEL_NEW_CONTENT_NOT_NEW:
            case Video.CHANNEL_NEW_CONTENT_WATCHED:
                return 2;
            default:
                return video.hasNewContent ? 0 : 2;
        }
    }

    public static int compare(Video video1, Video video2) {
        int result = getSortRank(video1) - getSortRank(video2);

        if (result != 0) {
            return result;
        }

        String title1 = video1 != null && video1.getTitle() != null ? video1.getTitle() : "";
        String title2 = video2 != null && video2.getTitle() != null ? video2.getTitle() : "";

        return title1.compareToIgnoreCase(title2);
    }

    private Channel obtainChannel(Video video) {
        String key = getKey(video);

        if (TextUtils.isEmpty(key)) {
            return null;
        }

        Channel channel = findChannel(video);

        if (channel == null) {
            channel = new Channel(key);
            mChannels.add(channel);
        }

        return channel;
    }

    private Channel findChannel(Video video) {
        String key = getKey(video);

        if (TextUtils.isEmpty(key) || mChannels.isEmpty()) {
            return null;
        }

        for (Channel item : mChannels) {
            if (Helpers.equals(key, item.key) ||
                    (!TextUtils.isEmpty(video.channelId) && Helpers.equals(video.channelId, item.channelId))) {
                return item;
            }
        }

        return null;
    }

    private String getKey(Video video) {
        if (video == null) {
            return null;
        }

        if (!TextUtils.isEmpty(video.channelId)) {
            return video.channelId;
        }

        if (!TextUtils.isEmpty(video.getReloadPageKey())) {
            return video.getReloadPageKey();
        }

        return video.getTitle();
    }

    private void applyTo(Video video, @Nullable Channel channel) {
        if (video == null) {
            return;
        }

        if (channel == null) {
            video.channelNewContentState = Video.CHANNEL_NEW_CONTENT_NONE;
            return;
        }

        if (TextUtils.isEmpty(video.channelId) && !TextUtils.isEmpty(channel.channelId)) {
            video.channelId = channel.channelId;
        }

        video.channelNewContentState = channel.state;
        video.hasNewContent = isNewContentState(channel.state);
    }

    private List<String> getVideoIds(@Nullable List<MediaItem> items) {
        List<String> result = new ArrayList<>();

        if (items == null) {
            return result;
        }

        for (MediaItem item : items) {
            if (item == null || TextUtils.isEmpty(item.getVideoId()) || result.contains(item.getVideoId())) {
                continue;
            }

            result.add(item.getVideoId());

            if (result.size() >= MAX_LATEST_ITEMS) {
                break;
            }
        }

        return result;
    }

    private List<String> getVideoIdsFromVideos(@Nullable List<Video> items) {
        List<String> result = new ArrayList<>();

        if (items == null) {
            return result;
        }

        for (Video item : items) {
            if (item == null || TextUtils.isEmpty(item.videoId) || result.contains(item.videoId)) {
                continue;
            }

            result.add(item.videoId);

            if (result.size() >= MAX_LATEST_ITEMS) {
                break;
            }
        }

        return result;
    }

    private boolean containsNewVideoIds(List<String> latestVideoIds, List<String> seenVideoIds) {
        for (String videoId : latestVideoIds) {
            if (!seenVideoIds.contains(videoId)) {
                return true;
            }
        }

        return false;
    }

    private boolean isFirstVisibleVideoWatched(MediaGroup mediaGroup) {
        if (mediaGroup == null || mediaGroup.getMediaItems() == null || mediaGroup.getMediaItems().isEmpty()) {
            return false;
        }

        MediaItem first = mediaGroup.getMediaItems().get(0);

        return first != null && !first.isLive() && !first.isUpcoming() && first.getPercentWatched() > 80;
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(CHANNEL_NEW_CONTENT_DATA);
        String[] split = Helpers.splitData(data);

        mChannels = Helpers.parseList(split, 0, Channel::fromString);
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        mPrefs.setProfileData(CHANNEL_NEW_CONTENT_DATA, Helpers.mergeData(mChannels));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
