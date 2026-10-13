package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelDeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class DeArrowSettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final DeArrowData mDeArrowData;

    private DeArrowSettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mDeArrowData = DeArrowData.instance(context);
    }

    public static DeArrowSettingsPresenter instance(Context context) {
        return new DeArrowSettingsPresenter(context);
    }

    public void show(Runnable onFinish) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        
        settingsPresenter.appendHeading(getContext().getString(R.string.dearrow_global_activation));
        appendSwitches(settingsPresenter);
        appendThumbQuality(settingsPresenter);
        settingsPresenter.appendHeading(getContext().getString(R.string.dearrow_channel_activation));
        appendChannelSwitches(settingsPresenter);
        settingsPresenter.appendSpacer();
        appendLinks(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dearrow_provider), onFinish);
    }

    public void show() {
        show(null);
    }

    private void appendChannelSwitches(AppDialogPresenter settingsPresenter) {
        ChannelDeArrowData data = ChannelDeArrowData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> data.setUnlocalizedTitlesEnabled(option.isSelected()), data.isUnlocalizedTitlesEnabled()));
        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsoursed_titles),
                option -> data.setReplaceTitlesEnabled(option.isSelected()), data.isReplaceTitlesEnabled()));
        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsourced_thumbnails),
                option -> data.setReplaceThumbnailsEnabled(option.isSelected()), data.isReplaceThumbnailsEnabled()));
        options.get(0).setRadio(options.get(1));
        options.get(1).setRadio(options.get(0));
        for (OptionItem option : options) settingsPresenter.appendSingleSwitch(option);
        List<OptionItem> thumbnails = new ArrayList<>();
        for (int[] pair : new int[][] {
                {R.string.thumb_quality_default, ClickbaitRemover.THUMB_QUALITY_DEFAULT},
                {R.string.thumb_quality_start, ClickbaitRemover.THUMB_QUALITY_START},
                {R.string.thumb_quality_middle, ClickbaitRemover.THUMB_QUALITY_MIDDLE},
                {R.string.thumb_quality_end, ClickbaitRemover.THUMB_QUALITY_END}}) {
            thumbnails.add(UiOptionItem.from(getContext().getString(pair[0]),
                    option -> data.setThumbQuality(pair[1]), data.getThumbQuality() == pair[1]));
        }
        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dearrow_thumbnail_source), thumbnails);
    }

    private void appendThumbQuality(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.thumb_quality_default, ClickbaitRemover.THUMB_QUALITY_DEFAULT},
                {R.string.thumb_quality_start, ClickbaitRemover.THUMB_QUALITY_START},
                {R.string.thumb_quality_middle, ClickbaitRemover.THUMB_QUALITY_MIDDLE},
                {R.string.thumb_quality_end, ClickbaitRemover.THUMB_QUALITY_END}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mMainUIData.setThumbQuality(pair[1]),
                    mMainUIData.getThumbQuality() == pair[1]
            ));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dearrow_thumbnail_source), options);
    }

    private void appendSwitches(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> {
                    mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected());
                    if (option.isSelected()) mDeArrowData.setReplaceTitlesEnabled(false);
                },
                mMainUIData.isUnlocalizedTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsoursed_titles),
                optionItem -> {
                    mDeArrowData.setReplaceTitlesEnabled(optionItem.isSelected());
                    if (optionItem.isSelected()) mMainUIData.setUnlocalizedTitlesEnabled(false);
                },
                mDeArrowData.isReplaceTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsourced_thumbnails),
                optionItem -> mDeArrowData.setReplaceThumbnailsEnabled(optionItem.isSelected()),
                mDeArrowData.isReplaceThumbnailsEnabled()));

        options.get(0).setRadio(options.get(1));
        options.get(1).setRadio(options.get(0));
        for (OptionItem option : options) settingsPresenter.appendSingleSwitch(option);
    }

    private void appendLinks(AppDialogPresenter settingsPresenter) {
        OptionItem statsCheckOption = UiOptionItem.from(getContext().getString(R.string.dearrow_status),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.dearrow_status_url)));

        OptionItem webSiteOption = UiOptionItem.from(getContext().getString(R.string.about_dearrow),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.dearrow_provider_url)));

        settingsPresenter.appendSingleButton(statsCheckOption);
        settingsPresenter.appendSingleButton(webSiteOption);
    }

    private void appendDeArrowSwitch(AppDialogPresenter settingsPresenter) {
        String title = String.format(
                "%s (%s)",
                getContext().getString(R.string.dearrow_provider),
                getContext().getString(R.string.dearrow_provider_url)
        );
        OptionItem sponsorBlockOption = UiOptionItem.from(title,
                option -> mDeArrowData.setDeArrowEnabled(option.isSelected()),
                mDeArrowData.isDeArrowEnabled()
        );

        settingsPresenter.appendSingleSwitch(sponsorBlockOption);
    }
}
