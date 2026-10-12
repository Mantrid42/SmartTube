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
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelNewContentData implements ProfileChangeListener {
    private static final String CHANNEL_NEW_CONTENT_DATA = "channel_new_content_data";
    private static final String VIDEO_LIST_DELIM = "&cnvl;";
    private static final int MAX_LATEST_ITEMS = 50;
    private static final Pattern RELATIVE_TIME_PATTERN =
            Pattern.compile("\\b(\\d+|a|an|one)\\s+(second|minute|hour|day|week|month|year)s?\\s+ago\\b", Pattern.CASE_INSENSITIVE);
    private static final long MONTH_MS = TimeUnit.DAYS.toMillis(30);
    private static final long YEAR_MS = TimeUnit.DAYS.toMillis(365);
    @SuppressLint("StaticFieldLeak")
    private static ChannelNewContentData sInstance;
    private final AppPrefs mPrefs;
    private List<Channel> mChannels;
    private long mLastNewContentReportTimeMs;
    private long mCurrentNewContentReportTimeMs;
    private final Set<String> mNewContentThisRefreshKeys = new HashSet<>();
    private final Set<String> mAdditionalContentThisRefreshKeys = new HashSet<>();
    private final Set<String> mVerifiedThisRefreshKeys = new HashSet<>();
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
        public List<String> newVideoIds;

        public Channel(String key) {
            this.key = key;
            this.seenVideoIds = new ArrayList<>();
            this.latestVideoIds = new ArrayList<>();
            this.newVideoIds = new ArrayList<>();
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
            channel.newVideoIds = split.length > 8 ?
                    Helpers.parseList(split, 8, VIDEO_LIST_DELIM, Helpers::parseStr) : new ArrayList<>();

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
                    Helpers.mergeList(VIDEO_LIST_DELIM, latestVideoIds),
                    Helpers.mergeList(VIDEO_LIST_DELIM, newVideoIds));
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

    public void beginRefresh() {
        mNewContentThisRefreshKeys.clear();
        mAdditionalContentThisRefreshKeys.clear();
        mVerifiedThisRefreshKeys.clear();

        for (Channel channel : mChannels) {
            channel.newVideoIds.clear();
        }

        mCurrentNewContentReportTimeMs = System.currentTimeMillis();
        persistState();
    }

    public void finishRefresh() {
        if (mCurrentNewContentReportTimeMs <= 0 || mCurrentNewContentReportTimeMs <= mLastNewContentReportTimeMs) {
            return;
        }

        mLastNewContentReportTimeMs = mCurrentNewContentReportTimeMs;
        mCurrentNewContentReportTimeMs = 0;
        persistState();
    }

    public boolean apply(Video channel) {
        if (channel == null) {
            return false;
        }

        Channel item = findChannel(channel);
        boolean changed = false;

        if (channel.hasNewContentFromYouTube) {
            item = obtainChannel(channel);

            if (item != null) {
                changed = item.update(channel);

                if (!isNewContentState(item.state)) {
                    item.state = Video.CHANNEL_NEW_CONTENT_UNVERIFIED;
                    mNewContentThisRefreshKeys.remove(item.key);
                    changed = true;
                }
            }
        } else if (item != null && item.state == Video.CHANNEL_NEW_CONTENT_UNVERIFIED) {
            item.state = Video.CHANNEL_NEW_CONTENT_NOT_NEW;
            mNewContentThisRefreshKeys.remove(item.key);
            changed = true;
        }

        applyTo(channel, item);

        if (changed) {
            persistState();
        }

        return item != null;
    }

    public boolean needsVerification(Video channel) {
        Channel item = findChannel(channel);
        return channel != null && channel.hasNewContentFromYouTube && item != null &&
                isNewContentState(item.state) && !mVerifiedThisRefreshKeys.contains(item.key);
    }

    public void setVerified(Video channel, MediaGroup mediaGroup) {
        VideoGroup group = mediaGroup != null ? VideoGroup.from(mediaGroup) : null;
        setVerified(channel, group != null ? group.getVideos() : null, mediaGroup);
    }

    public void setVerified(Video channel, @Nullable List<Video> videos, MediaGroup mediaGroup) {
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

        List<String> previousLatestVideoIds = new ArrayList<>(item.latestVideoIds);
        List<String> latestVideoIds = getVideoIdsFromVideos(videos);
        boolean alreadyHadNewContent = item.state == Video.CHANNEL_NEW_CONTENT_VERIFIED;
        boolean hasNewContent;

        if (latestVideoIds.isEmpty()) {
            hasNewContent = false;
        } else if (!item.seenVideoIds.isEmpty()) {
            hasNewContent = containsNewVideoIds(latestVideoIds, item.seenVideoIds);
        } else if (alreadyHadNewContent) {
            // A scan does not acknowledge previously discovered, unseen uploads.
            hasNewContent = true;
        } else if (!item.latestVideoIds.isEmpty()) {
            hasNewContent = containsNewVideoIds(latestVideoIds, item.latestVideoIds);
        } else {
            hasNewContent = containsVideoPublishedAfter(videos, getReportCutoffTimeMs());
        }

        if (item.manualNewContent) {
            hasNewContent = true;
        }

        if (alreadyHadNewContent) {
            // Highlight only uploads added since the last scan, not the old unread backlog.
            item.newVideoIds = getNewVideoIds(videos, latestVideoIds, previousLatestVideoIds,
                    previousLatestVideoIds, getReportCutoffTimeMs(), false);
        } else {
            item.newVideoIds = hasNewContent ?
                getNewVideoIds(videos, latestVideoIds, item.seenVideoIds, previousLatestVideoIds, getReportCutoffTimeMs(), item.manualNewContent) :
                new ArrayList<>();
        }
        item.latestVideoIds = latestVideoIds;
        item.state = hasNewContent ? Video.CHANNEL_NEW_CONTENT_VERIFIED : Video.CHANNEL_NEW_CONTENT_NOT_NEW;
        mVerifiedThisRefreshKeys.add(item.key);

        if (hasNewContent && (!alreadyHadNewContent || !item.newVideoIds.isEmpty())) {
            mNewContentThisRefreshKeys.add(item.key);
            if (alreadyHadNewContent) {
                mAdditionalContentThisRefreshKeys.add(item.key);
            }
        } else {
            mNewContentThisRefreshKeys.remove(item.key);
            mAdditionalContentThisRefreshKeys.remove(item.key);
            if (!hasNewContent) {
                item.manualNewContent = false;
            }
        }

        applyTo(channel, item);
        applyNewVideoMarkers(channel, videos);
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
        item.newVideoIds = new ArrayList<>(item.latestVideoIds);
        mNewContentThisRefreshKeys.remove(item.key);
        channel.channelNewContentSeenTimeMs = 0;
        item.update(channel);
        applyTo(channel, item);
        persistState();
    }

    public void applyNewVideoMarkers(Video channel, @Nullable List<Video> videos) {
        Channel item = findChannel(channel);
        applyNewVideoMarkersToVideos(videos, item);
    }

    public static boolean isNewContentState(int state) {
        return state == Video.CHANNEL_NEW_CONTENT_UNVERIFIED ||
                state == Video.CHANNEL_NEW_CONTENT_VERIFIED ||
                state == Video.CHANNEL_NEW_CONTENT_ADDITIONAL_THIS_REFRESH ||
                state == Video.CHANNEL_NEW_CONTENT_VERIFIED_THIS_REFRESH;
    }

    private static int getSortRank(Video video) {
        if (video == null) {
            return 3;
        }

        switch (video.channelNewContentState) {
            case Video.CHANNEL_NEW_CONTENT_UNVERIFIED:
            case Video.CHANNEL_NEW_CONTENT_VERIFIED:
            case Video.CHANNEL_NEW_CONTENT_VERIFIED_THIS_REFRESH:
            case Video.CHANNEL_NEW_CONTENT_ADDITIONAL_THIS_REFRESH:
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
            video.channelNewContentNewVideo = false;
            return;
        }

        if (TextUtils.isEmpty(video.channelId) && !TextUtils.isEmpty(channel.channelId)) {
            video.channelId = channel.channelId;
        }

        int state = channel.state;

        if (state == Video.CHANNEL_NEW_CONTENT_VERIFIED && mNewContentThisRefreshKeys.contains(channel.key)) {
            state = mAdditionalContentThisRefreshKeys.contains(channel.key) ?
                    Video.CHANNEL_NEW_CONTENT_ADDITIONAL_THIS_REFRESH : Video.CHANNEL_NEW_CONTENT_VERIFIED_THIS_REFRESH;
        }

        video.channelNewContentState = state;
        video.hasNewContent = isNewContentState(state);
    }

    private void applyNewVideoMarkersToVideos(@Nullable List<Video> videos, @Nullable Channel channel) {
        if (videos == null) {
            return;
        }

        for (Video video : videos) {
            video.channelNewContentNewVideo = isNewVideo(channel, video);
        }
    }

    private boolean isNewVideo(@Nullable Channel channel, @Nullable Video video) {
        if (channel == null || video == null || TextUtils.isEmpty(video.videoId) ||
                !mNewContentThisRefreshKeys.contains(channel.key)) {
            return false;
        }

        return channel.newVideoIds.contains(video.videoId);
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

    private List<String> getNewVideoIds(@Nullable List<Video> videos, List<String> latestVideoIds, List<String> seenVideoIds,
                                        List<String> previousLatestVideoIds, long cutoffTimeMs, boolean manualNewContent) {
        List<String> result = new ArrayList<>();

        if (latestVideoIds.isEmpty()) {
            return result;
        }

        if (!seenVideoIds.isEmpty()) {
            addMissingVideoIds(result, latestVideoIds, seenVideoIds);
        } else if (!previousLatestVideoIds.isEmpty()) {
            addMissingVideoIds(result, latestVideoIds, previousLatestVideoIds);
        } else {
            addVideoIdsPublishedAfter(result, videos, latestVideoIds, cutoffTimeMs);
        }

        if (manualNewContent && result.isEmpty()) {
            result.addAll(latestVideoIds);
        }

        return result;
    }

    private void addMissingVideoIds(List<String> result, List<String> sourceVideoIds, List<String> knownVideoIds) {
        for (String videoId : sourceVideoIds) {
            if (!knownVideoIds.contains(videoId)) {
                result.add(videoId);
            }
        }
    }

    private void addVideoIdsPublishedAfter(List<String> result, @Nullable List<Video> videos, List<String> latestVideoIds, long cutoffTimeMs) {
        if (videos == null || cutoffTimeMs <= 0) {
            return;
        }

        for (Video video : videos) {
            if (video != null && latestVideoIds.contains(video.videoId) && getPublishedTimeMs(video) > cutoffTimeMs) {
                result.add(video.videoId);
            }
        }
    }

    private boolean containsVideoPublishedAfter(@Nullable List<Video> videos, long cutoffTimeMs) {
        if (videos == null || cutoffTimeMs <= 0) {
            return false;
        }

        for (Video video : videos) {
            if (getPublishedTimeMs(video) > cutoffTimeMs) {
                return true;
            }
        }

        return false;
    }

    private long getReportCutoffTimeMs() {
        return mLastNewContentReportTimeMs > 0 ? mLastNewContentReportTimeMs : mCurrentNewContentReportTimeMs;
    }

    private long getPublishedTimeMs(@Nullable Video video) {
        if (video == null) {
            return 0;
        }

        if (video.mediaItem != null) {
            long publishedTimeMs = getPublishedTimeMs(video.mediaItem);

            if (publishedTimeMs > 0) {
                return publishedTimeMs;
            }
        }

        return parseRelativeTimeMs(video.getSecondTitle());
    }

    private long getPublishedTimeMs(@Nullable MediaItem item) {
        if (item == null) {
            return 0;
        }

        long publishedDate = item.getPublishedDate();

        if (publishedDate > 0) {
            return publishedDate < 100_000_000_000L ? publishedDate * 1_000 : publishedDate;
        }

        long productionTimeMs = parseRelativeTimeMs(item.getProductionDate());

        return productionTimeMs > 0 ? productionTimeMs : parseRelativeTimeMs(item.getSecondTitle());
    }

    private long parseRelativeTimeMs(@Nullable CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }

        String value = text.toString().toLowerCase(Locale.US);

        if (value.contains("just now") || value.contains("today")) {
            return System.currentTimeMillis();
        }

        if (value.contains("yesterday")) {
            return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        }

        Matcher matcher = RELATIVE_TIME_PATTERN.matcher(value);

        if (!matcher.find()) {
            return 0;
        }

        long amount = parseRelativeAmount(matcher.group(1));
        long unitMs = getRelativeUnitMs(matcher.group(2));

        if (amount <= 0 || unitMs <= 0) {
            return 0;
        }

        long durationMs = amount > Long.MAX_VALUE / unitMs ? Long.MAX_VALUE : amount * unitMs;

        return System.currentTimeMillis() - durationMs;
    }

    private long parseRelativeAmount(String amount) {
        if ("a".equals(amount) || "an".equals(amount) || "one".equals(amount)) {
            return 1;
        }

        return Helpers.parseLong(amount, 0);
    }

    private long getRelativeUnitMs(String unit) {
        switch (unit.toLowerCase(Locale.US)) {
            case "second":
                return TimeUnit.SECONDS.toMillis(1);
            case "minute":
                return TimeUnit.MINUTES.toMillis(1);
            case "hour":
                return TimeUnit.HOURS.toMillis(1);
            case "day":
                return TimeUnit.DAYS.toMillis(1);
            case "week":
                return TimeUnit.DAYS.toMillis(7);
            case "month":
                return MONTH_MS;
            case "year":
                return YEAR_MS;
            default:
                return 0;
        }
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(CHANNEL_NEW_CONTENT_DATA);
        String[] split = Helpers.splitData(data);

        mChannels = Helpers.parseList(split, 0, Channel::fromString);
        mLastNewContentReportTimeMs = Helpers.parseLong(split, 1, 0);
        mCurrentNewContentReportTimeMs = 0;
        mNewContentThisRefreshKeys.clear();
        mAdditionalContentThisRefreshKeys.clear();
        mVerifiedThisRefreshKeys.clear();
    }

    public void persistNow() {
        Utils.removeCallbacks(mPersistStateInt);
        persistStateInt();
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        mPrefs.setProfileData(CHANNEL_NEW_CONTENT_DATA, Helpers.mergeData(mChannels, mLastNewContentReportTimeMs));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
