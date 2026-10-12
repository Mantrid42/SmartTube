package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelDeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import io.reactivex.disposables.Disposable;

public class DeArrowProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = DeArrowProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final DeArrowData mDeArrowData;
    private final ChannelDeArrowData mChannelData;
    private boolean mIsReplaceTitlesEnabled;
    private boolean mIsReplaceThumbnailsEnabled;
    private Disposable mResult;

    public DeArrowProcessor(Context context, OnItemReady onItemReady) {
        this(DeArrowData.instance(context), ChannelDeArrowData.instance(context),
                YouTubeServiceManager.instance().getMediaItemService(), onItemReady);
    }

    DeArrowProcessor(DeArrowData data, ChannelDeArrowData channelData, MediaItemService itemService, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        mItemService = itemService;
        mDeArrowData = data;
        mChannelData = channelData;
        mDeArrowData.setOnChange(this);
        initData();
    }

    @Override
    public void onDataChange() {
        initData();
    }

    private void initData() {
        mIsReplaceTitlesEnabled = mDeArrowData.isReplaceTitlesEnabled();
        mIsReplaceThumbnailsEnabled = mDeArrowData.isReplaceThumbnailsEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        process(videoGroup, null);
    }

    public void process(VideoGroup videoGroup, String channelId) {
        if (videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        if (TextUtils.isEmpty(channelId) && videoGroup.getMediaGroup() != null) {
            channelId = videoGroup.getMediaGroup().getChannelId();
        }
        final boolean replaceTitles = mIsReplaceTitlesEnabled;
        final boolean replaceThumbnails = mIsReplaceThumbnailsEnabled;
        Set<String> channelVideoIds = new HashSet<>();
        List<String> videoIds = new ArrayList<>();
        for (Video video : videoGroup.getVideos()) {
            boolean channelEnabled = mChannelData.isEnabled(!TextUtils.isEmpty(video.channelId) ? video.channelId : channelId);
            if (TextUtils.isEmpty(video.videoId) || video.deArrowProcessed ||
                    (!replaceTitles && !replaceThumbnails && !channelEnabled)) {
                continue;
            }
            video.deArrowProcessed = true;
            videoIds.add(video.videoId);
            if (channelEnabled) {
                channelVideoIds.add(video.videoId);
            }
        }
        if (videoIds.isEmpty()) {
            return;
        }
        mResult = mItemService.getDeArrowDataObserve(videoIds)
                .subscribe(deArrowData -> {
                    Video video = videoGroup.findVideoById(deArrowData.getVideoId());
                    if (video == null) {
                        return;
                    }
                    if (replaceTitles || channelVideoIds.contains(video.videoId)) {
                        video.deArrowTitle = deArrowData.getTitle();
                    }
                    if (replaceThumbnails || channelVideoIds.contains(video.videoId)) {
                        video.altCardImageUrl = deArrowData.getThumbnailUrl();
                    }
                    mOnItemReady.onItemReady(video);
                },
                error -> {
                    Log.d(TAG, "DeArrow cannot process the video");
                });
    }

    @Override
    public void dispose() {
        RxHelper.disposeActions(mResult);
    }

}
