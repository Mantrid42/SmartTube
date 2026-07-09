package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class ChannelCardPresenter extends LongClickPresenter {
    private static final String TAG = VideoCardPresenter.class.getSimpleName();
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mSelectedBackgroundColor;
    private int mNewContentBackgroundColor;
    private int mSelectedTextColor;
    private int mWidth;
    private int mHeight;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardDefaultBackground));
        mDefaultTextColor =
                ContextCompat.getColor(context, R.color.card_default_text);
        mNewContentBackgroundColor =
                ContextCompat.getColor(context, R.color.dark_red);
        mSelectedBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardSelectedBackground));
        mSelectedTextColor =
                ContextCompat.getColor(context, R.color.card_selected_text_grey);

        updateDimensions(context);

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.channel_card, null);
        container.setBackgroundColor(mDefaultBackgroundColor);
        //if (VERSION.SDK_INT >= 23 && MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS)) {
        //    container.setForeground(ContextCompat.getDrawable(context, R.drawable.lb_card_outline));
        //}

        TextView textView = container.findViewById(R.id.channel_title);
        textView.setBackgroundColor(mDefaultBackgroundColor);
        textView.setTextColor(mDefaultTextColor);
        container.findViewById(R.id.channel_card_wrapper).setBackgroundColor(mDefaultBackgroundColor);

        boolean autoScrollEnabled = isCardTextAutoScrollEnabled(context);
        if (autoScrollEnabled) {
            ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));
        }

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int cardBackgroundColor = getBackgroundColor(textView);
            int backgroundColor = hasFocus ? mSelectedBackgroundColor : cardBackgroundColor;
            int textColor = hasFocus ? mSelectedTextColor : mDefaultTextColor;

            container.setBackgroundColor(backgroundColor);
            container.findViewById(R.id.channel_card_wrapper).setBackgroundColor(backgroundColor);
            textView.setBackgroundColor(backgroundColor);
            textView.setTextColor(textColor);
            updateSeenIndicator(container.findViewById(R.id.channel_seen_indicator), hasFocus);

            if (!autoScrollEnabled) {
                return;
            }

            if (hasFocus) {
                ViewUtil.enableMarquee(textView);
            } else {
                ViewUtil.disableMarquee(textView);
            }
        });

        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        Context context = viewHolder.view.getContext();
        Video video = (Video) item;

        ViewUtil.setDimensions(viewHolder.view.findViewById(R.id.channel_card_wrapper), mWidth, -1); // don't do auto height

        TextView textView = viewHolder.view.findViewById(R.id.channel_title);
        textView.setText(video.getTitle());

        // We should setup props each time because object may be reused by the underlying RecyclerView
        int backgroundColor = getBackgroundColor(video);
        int displayBackgroundColor = viewHolder.view.hasFocus() ? mSelectedBackgroundColor : backgroundColor;
        viewHolder.view.setBackgroundColor(displayBackgroundColor);
        viewHolder.view.findViewById(R.id.channel_card_wrapper).setBackgroundColor(displayBackgroundColor);
        textView.setBackgroundColor(displayBackgroundColor);
        textView.setTextColor(viewHolder.view.hasFocus() ? mSelectedTextColor : mDefaultTextColor);
        textView.setTag(R.id.channel_new_content, backgroundColor != mDefaultBackgroundColor ? backgroundColor : null);

        View progress = viewHolder.view.findViewById(R.id.channel_progress);
        progress.setVisibility(video.channelNewContentLoading ? View.VISIBLE : View.GONE);

        updateSeenIndicator(viewHolder.view.findViewById(R.id.channel_seen_indicator), video, viewHolder.view.hasFocus());

        ImageView imageView = viewHolder.view.findViewById(R.id.channel_image);
        imageView.setVisibility(View.VISIBLE);

        if (!Helpers.equals(imageView.getTag(R.id.channel_image_url), video.cardImageUrl) || imageView.getDrawable() == null) {
            imageView.setTag(R.id.channel_image_url, video.cardImageUrl);
            Glide.with(context)
                    .load(video.cardImageUrl)
                    .apply(ViewUtil.glideOptions())
                    .listener(mErrorListener)
                    //.error(R.drawable.card_placeholder) // R.color.lb_grey
                    .into(imageView);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // Remove references to images so that the garbage collector can free up memory.
        ImageView imageView = viewHolder.view.findViewById(R.id.channel_image);
        imageView.setImageDrawable(null);
    }

    private void updateDimensions(Context context) {
        Pair<Integer, Integer> dimens = getCardDimensPx(context);

        mWidth = dimens.first;
        mHeight = dimens.second;
    }

    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return GridFragmentHelper.getCardDimensPx(
                context, R.dimen.channel_card_width,
                R.dimen.channel_card_height,
                MainUIData.instance(context).getVideoGridScale(),
                true);
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }

    private int getBackgroundColor(TextView textView) {
        Object color = textView.getTag(R.id.channel_new_content);

        return color instanceof Integer ? (Integer) color : mDefaultBackgroundColor;
    }

    private int getBackgroundColor(Video video) {
        switch (video.channelNewContentState) {
            case Video.CHANNEL_NEW_CONTENT_UNVERIFIED:
            case Video.CHANNEL_NEW_CONTENT_VERIFIED:
                return mNewContentBackgroundColor;
            default:
                return video.hasNewContent ? mNewContentBackgroundColor : mDefaultBackgroundColor;
        }
    }

    private void updateSeenIndicator(View indicator, Video video, boolean hasFocus) {
        boolean isWatched = video.channelNewContentState == Video.CHANNEL_NEW_CONTENT_WATCHED;
        long seenTimeMs = isWatched ? video.channelNewContentSeenTimeMs : 0;

        indicator.setTag(R.id.channel_new_content_seen, isWatched ? seenTimeMs : null);
        updateSeenIndicator(indicator, hasFocus);
    }

    private void updateSeenIndicator(View indicator, boolean hasFocus) {
        Object tag = indicator.getTag(R.id.channel_new_content_seen);
        long seenTimeMs = tag instanceof Long ? (Long) tag : 0;
        boolean isWatched = tag instanceof Long;
        int mode = MainUIData.instance(indicator.getContext()).getChannelNewContentEyeIndicator();
        boolean show = false;

        if (mode == MainUIData.CHANNEL_NEW_CONTENT_EYE_WHILE_HIGHLIGHTED) {
            show = hasFocus && isWatched;
        } else if (mode > 0 && seenTimeMs != 0) {
            long remainingMs = mode * 1_000L - (System.currentTimeMillis() - seenTimeMs);

            if (remainingMs > 0) {
                show = true;
                indicator.postDelayed(() -> {
                    Object currentTag = indicator.getTag(R.id.channel_new_content_seen);

                    if (currentTag instanceof Long && (Long) currentTag == seenTimeMs) {
                        indicator.setVisibility(View.GONE);
                    }
                }, remainingMs);
            }
        }

        indicator.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}
