package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelDeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class UnlocalizedTitleProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = UnlocalizedTitleProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final MainUIData mMainUIData;
    private final ChannelDeArrowData mChannelData;
    private boolean mIsUnlocalizedTitlesEnabled;
    private Disposable mResult;

    public UnlocalizedTitleProcessor(Context context, OnItemReady onItemReady) {
        this(MainUIData.instance(context), ChannelDeArrowData.instance(context),
                YouTubeServiceManager.instance().getMediaItemService(), onItemReady);
    }

    UnlocalizedTitleProcessor(MainUIData data, ChannelDeArrowData channels, MediaItemService service, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        mItemService = service;
        mMainUIData = data;
        mChannelData = channels;
        mMainUIData.setOnChange(this);
        initData();
    }

    @Override
    public void onDataChange() {
        initData();
    }

    private void initData() {
        mIsUnlocalizedTitlesEnabled = mMainUIData.isUnlocalizedTitlesEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if (videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        List<String> videoIds = getVideoIds(videoGroup);
        mResult = Observable.fromIterable(videoIds)
                .flatMap(videoId -> mItemService.getUnlocalizedTitleObserve(videoId)
                        .map(newTitle -> new Pair<>(videoId, newTitle)))
                .subscribe(title -> {
                    Video video = videoGroup.findVideoById(title.first);
                    if (video == null) {
                        return;
                    }
                    video.deArrowTitle = title.second; // sometimes player titles localized while cards not
                    if (!Helpers.equals(video.title, video.deArrowTitle)) {
                        mOnItemReady.onItemReady(video);
                    }
                },
                error -> {
                    Log.d(TAG, "Unlocalized title: Cannot process the video");
                });
    }

    @Override
    public void dispose() {
        RxHelper.disposeActions(mResult);
    }

    private List<String> getVideoIds(VideoGroup videoGroup) {
        List<String> result = new ArrayList<>();

        for (Video video : videoGroup.getVideos()) {
            String key = video.channelId != null ? video.channelId : video.deArrowChannelId;
            boolean enabled = mChannelData.isEnabled(key) ? mChannelData.isUnlocalizedTitlesEnabled() : mIsUnlocalizedTitlesEnabled;
            if (!enabled || mChannelData.isExcluded(key) || video.videoId == null || video.unlocalizedTitleProcessed) {
                continue;
            }
            video.unlocalizedTitleProcessed = true;
            result.add(video.videoId);
        }

        return result;
    }
}
