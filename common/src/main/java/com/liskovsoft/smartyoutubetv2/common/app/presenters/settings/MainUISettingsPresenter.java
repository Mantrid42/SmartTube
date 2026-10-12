package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;

import java.util.ArrayList;
import java.util.List;

public class MainUISettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final DeArrowData mDeArrowData;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private MainUISettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mDeArrowData = DeArrowData.instance(context);
    }

    public static MainUISettingsPresenter instance(Context context) {
        return new MainUISettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendTopButtonsCategory(settingsPresenter);
        appendColorScheme(settingsPresenter);
        if (Build.VERSION.SDK_INT > 19) {
            appendCardTextScrollSpeed(settingsPresenter);
        }
        appendCardPreviews(settingsPresenter);
        appendCardStyle(settingsPresenter);
        appendThumbSource(settingsPresenter);
        //appendCardTitleLines(settingsPresenter);
        appendChannelSortingCategory(settingsPresenter);
        appendChannelNewContentCategory(settingsPresenter);
        //appendPlaylistsCategoryStyle(settingsPresenter);
        appendScaleUI(settingsPresenter);
        if (Build.VERSION.SDK_INT > 19) {
            appendVideoGridScale(settingsPresenter);
        }
        //appendTimeFormatCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_main_ui), mOnFinish);
    }

    private void appendTopButtonsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.settings_search, MainUIData.TOP_BUTTON_SEARCH},
                {R.string.settings_language_country, MainUIData.TOP_BUTTON_CHANGE_LANGUAGE},
                {R.string.settings_accounts, MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setTopButtonEnabled(pair[1]);
                } else {
                    mMainUIData.setTopButtonDisabled(pair[1]);
                }
            }, mMainUIData.isTopButtonEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.various_buttons), options);
    }

    private void appendColorScheme(AppDialogPresenter settingsPresenter) {
        List<ColorScheme> colorSchemes = mMainUIData.getColorSchemes();

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.color_scheme), fromColorSchemes(colorSchemes));
    }

    private List<OptionItem> fromColorSchemes(List<ColorScheme> colorSchemes) {
        List<OptionItem> styleOptions = new ArrayList<>();

        for (ColorScheme colorScheme : colorSchemes) {
            styleOptions.add(UiOptionItem.from(
                    getContext().getString(colorScheme.nameResId),
                    option -> {
                        mMainUIData.setColorScheme(colorScheme);
                        mRestartApp = true;
                    },
                    colorScheme.equals(mMainUIData.getColorScheme())));
        }

        return styleOptions;
    }

    private void appendCardPreviews(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.option_disabled, MainUIData.CARD_PREVIEW_DISABLED},
                {R.string.card_preview_full, MainUIData.CARD_PREVIEW_FULL},
                {R.string.card_preview_muted, MainUIData.CARD_PREVIEW_MUTED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setCardPreviewType(pair[1]);
            }, mMainUIData.getCardPreviewType() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_preview), options);
    }

    private void appendCardStyle(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem multilineTitle = UiOptionItem.from(getContext().getString(R.string.card_multiline_title),
                option -> mMainUIData.setCardMultilineTitleEnabled(option.isSelected()), mMainUIData.isCardMultilineTitleEnabled());

        OptionItem multilineSubtitle = UiOptionItem.from(getContext().getString(R.string.card_multiline_subtitle),
                option -> mMainUIData.setCardMultilineSubtitleEnabled(option.isSelected()), mMainUIData.isCardMultilineSubtitleEnabled());

        OptionItem autoScrolledTitle = UiOptionItem.from(getContext().getString(R.string.card_auto_scrolled_title),
                option -> mMainUIData.setCardTextAutoScrollEnabled(option.isSelected()), mMainUIData.isCardTextAutoScrollEnabled());

        OptionItem unlocalizedTitle = UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected()), mMainUIData.isUnlocalizedTitlesEnabled());

        OptionItem roundedCardCorners = UiOptionItem.from(getContext().getString(R.string.rounded_card_corners),
                option -> {
                    if (option.isSelected()) {
                        mMainUIData.setUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
                    } else {
                        mMainUIData.setUiTweakDisabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
                    }
                    mRestartApp = true;
                },
                mMainUIData.isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        
        options.add(multilineTitle);
        options.add(multilineSubtitle);
        if (Build.VERSION.SDK_INT > 19) {
            options.add(autoScrolledTitle);
        }
        options.add(unlocalizedTitle);
        options.add(roundedCardCorners);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.cards_style), options);
    }

    private void appendCardTitleLines(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int linesNum : new int[] {1, 2, 3, 4}) {
            options.add(UiOptionItem.from(String.format("%s", linesNum),
                    optionItem -> mMainUIData.setCartTitleLinesNum(linesNum),
                    linesNum == mMainUIData.getCardTitleLinesNum()));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_title_lines_num), options);
    }

    private void appendThumbSource(AppDialogPresenter settingsPresenter) {
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

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_content), options);
    }

    private void appendChannelSortingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.sorting_last_viewed, MainUIData.CHANNEL_SORTING_LAST_VIEWED},
                {R.string.sorting_alphabetically, MainUIData.CHANNEL_SORTING_NAME},
                {R.string.sorting_by_new_content, MainUIData.CHANNEL_SORTING_NEW_CONTENT}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setChannelCategorySorting(pair[1]);
                BrowsePresenter.instance(getContext()).updateChannelSorting();
            }, mMainUIData.getChannelCategorySorting() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channels_section_sorting), options);
    }

    private void appendChannelNewContentCategory(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.local_unseen_list),
                optionItem -> showChannelNewContentSettings()));
    }

    private void showChannelNewContentSettings() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        OptionItem verifyOption = createChannelNewContentVerificationOption();
        OptionItem channelHighlighted = createChannelNewContentChannelHighlightedOption();
        OptionItem markSeenOption = UiOptionItem.from(
                getContext().getString(R.string.channel_new_content_mark_channel_as_seen),
                optionItem -> showChannelNewContentMarkSeenSettings());
        OptionItem delayOption = UiOptionItem.from(
                getContext().getString(R.string.channel_new_content_highlight_delay),
                optionItem -> showChannelNewContentHighlightDelaySettings());
        OptionItem eyeOption = UiOptionItem.from(
                getContext().getString(R.string.channel_new_content_show_eye_indicator),
                optionItem -> showChannelNewContentEyeIndicatorSettings());
        OptionItem videoMarkerOption = UiOptionItem.from(
                getContext().getString(R.string.channel_new_content_video_marker),
                optionItem -> showChannelNewContentVideoMarkerSettings());

        markSeenOption.setRequired(verifyOption);
        delayOption.setRequired(verifyOption, channelHighlighted);
        eyeOption.setRequired(verifyOption, channelHighlighted);
        videoMarkerOption.setRequired(verifyOption);

        settingsPresenter.appendSingleSwitch(verifyOption);
        settingsPresenter.appendSingleButton(markSeenOption);
        settingsPresenter.appendSingleButton(delayOption);
        settingsPresenter.appendSingleButton(eyeOption);
        settingsPresenter.appendSingleButton(videoMarkerOption);
        settingsPresenter.showDialog(getContext().getString(R.string.local_unseen_list));
    }

    private OptionItem createChannelNewContentVerificationOption() {
        return UiOptionItem.from(getContext().getString(R.string.channel_new_content_verification),
                optionItem -> mMainUIData.setChannelNewContentVerificationEnabled(optionItem.isSelected()),
                mMainUIData.isChannelNewContentVerificationEnabled());
    }

    private OptionItem createChannelNewContentChannelHighlightedOption() {
        return new OptionItem() {
            @Override
            public int getId() {
                return 0;
            }

            @Override
            public CharSequence getTitle() {
                return getContext().getString(R.string.channel_new_content_when_channel_highlighted);
            }

            @Override
            public CharSequence getDescription() {
                return null;
            }

            @Override
            public boolean isSelected() {
                return mMainUIData.getChannelNewContentMarkSeenMode() == MainUIData.CHANNEL_NEW_CONTENT_MARK_SEEN_ON_CHANNEL;
            }

            @Override
            public void onSelect(boolean isSelected) {
            }

            @Override
            public Object getData() {
                return null;
            }

            @Override
            public void setRequired(OptionItem... items) {
            }

            @Override
            public OptionItem[] getRequired() {
                return null;
            }

            @Override
            public void setRadio(OptionItem... items) {
            }

            @Override
            public OptionItem[] getRadio() {
                return null;
            }

            @Override
            public ChatReceiver getChatReceiver() {
                return null;
            }

            @Override
            public CommentsReceiver getCommentsReceiver() {
                return null;
            }
        };
    }

    private void showChannelNewContentMarkSeenSettings() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.channel_new_content_when_any_video_highlighted, MainUIData.CHANNEL_NEW_CONTENT_MARK_SEEN_ON_VIDEO},
                {R.string.channel_new_content_when_channel_highlighted, MainUIData.CHANNEL_NEW_CONTENT_MARK_SEEN_ON_CHANNEL}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (mMainUIData.isChannelNewContentVerificationEnabled()) {
                    mMainUIData.setChannelNewContentMarkSeenMode(pair[1]);
                }
            }, mMainUIData.getChannelNewContentMarkSeenMode() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channel_new_content_mark_channel_as_seen), options);
        settingsPresenter.showDialog(getContext().getString(R.string.channel_new_content_mark_channel_as_seen));
    }

    private void showChannelNewContentHighlightDelaySettings() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (int delaySec : new int[] {2, 3, 5, 10, 20, 30}) {
            options.add(UiOptionItem.from(formatSeconds(delaySec), optionItem -> {
                if (mMainUIData.isChannelNewContentVerificationEnabled() &&
                        mMainUIData.getChannelNewContentMarkSeenMode() == MainUIData.CHANNEL_NEW_CONTENT_MARK_SEEN_ON_CHANNEL) {
                    mMainUIData.setChannelNewContentHighlightDelaySec(delaySec);
                }
            }, mMainUIData.getChannelNewContentHighlightDelaySec() == delaySec));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channel_new_content_highlight_delay), options);
        settingsPresenter.showDialog(getContext().getString(R.string.channel_new_content_highlight_delay));
    }

    private void showChannelNewContentEyeIndicatorSettings() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.option_never), optionItem -> {
            if (isChannelNewContentChannelHighlightEnabled()) {
                mMainUIData.setChannelNewContentEyeIndicator(MainUIData.CHANNEL_NEW_CONTENT_EYE_NEVER);
            }
        }, mMainUIData.getChannelNewContentEyeIndicator() == MainUIData.CHANNEL_NEW_CONTENT_EYE_NEVER));

        for (int seconds : new int[] {1, 2, 3, 5, 10}) {
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.channel_new_content_eye_for_seconds, formatSeconds(seconds)),
                    optionItem -> {
                        if (isChannelNewContentChannelHighlightEnabled()) {
                            mMainUIData.setChannelNewContentEyeIndicator(seconds);
                        }
                    },
                    mMainUIData.getChannelNewContentEyeIndicator() == seconds));
        }

        options.add(UiOptionItem.from(getContext().getString(R.string.channel_new_content_eye_while_highlighted),
                optionItem -> {
                    if (isChannelNewContentChannelHighlightEnabled()) {
                        mMainUIData.setChannelNewContentEyeIndicator(MainUIData.CHANNEL_NEW_CONTENT_EYE_WHILE_HIGHLIGHTED);
                    }
                },
                mMainUIData.getChannelNewContentEyeIndicator() == MainUIData.CHANNEL_NEW_CONTENT_EYE_WHILE_HIGHLIGHTED));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channel_new_content_show_eye_indicator), options);
        settingsPresenter.showDialog(getContext().getString(R.string.channel_new_content_show_eye_indicator));
    }

    private void showChannelNewContentVideoMarkerSettings() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.option_disabled, MainUIData.CHANNEL_NEW_CONTENT_VIDEO_MARKER_DISABLED},
                {R.string.channel_new_content_video_marker_underline, MainUIData.CHANNEL_NEW_CONTENT_VIDEO_MARKER_UNDERLINE},
                {R.string.channel_new_content_video_marker_card, MainUIData.CHANNEL_NEW_CONTENT_VIDEO_MARKER_CARD}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (mMainUIData.isChannelNewContentVerificationEnabled()) {
                    mMainUIData.setChannelNewContentVideoMarkerMode(pair[1]);
                }
            }, mMainUIData.getChannelNewContentVideoMarkerMode() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channel_new_content_video_marker), options);
        settingsPresenter.showDialog(getContext().getString(R.string.channel_new_content_video_marker));
    }

    private boolean isChannelNewContentChannelHighlightEnabled() {
        return mMainUIData.isChannelNewContentVerificationEnabled() &&
                mMainUIData.getChannelNewContentMarkSeenMode() == MainUIData.CHANNEL_NEW_CONTENT_MARK_SEEN_ON_CHANNEL;
    }

    private String formatSeconds(int seconds) {
        return getContext().getResources().getQuantityString(R.plurals.seconds, seconds, seconds);
    }

    private void appendPlaylistsCategoryStyle(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.playlists_style_grid, MainUIData.PLAYLISTS_STYLE_GRID},
                {R.string.playlists_style_rows, MainUIData.PLAYLISTS_STYLE_ROWS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setPlaylistsStyle(pair[1]);
                BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
            }, mMainUIData.getPlaylistsStyle() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.playlists_style), options);
    }

    private void appendScaleUI(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.4f, 0.5f, 0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setUIScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getUIScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.scale_ui), options);
    }

    private void appendCardTextScrollSpeed(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float factor : new float[] {1, 1.5f, 2, 2.5f, 3, 3.5f, 4, 4.5f, 5, 5.5f, 6, 6.5f, 7, 7.5f, 8}) {
            options.add(UiOptionItem.from(String.format("%sx", Helpers.formatFloat(factor)),
                    optionItem -> mMainUIData.setCardTextScrollSpeed(factor),
                    Helpers.floatEquals(factor, mMainUIData.getCardTextScrollSpeed())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_text_scroll_factor), options);
    }

    private void appendVideoGridScale(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f, 1.5f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setVideoGridScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getVideoGridScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.video_grid_scale), options);
    }

    private void appendTimeFormatCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_24),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(true);
                    mRestartApp = true;
                },
                mGeneralData.is24HourLocaleEnabled()));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_12),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(false);
                    mRestartApp = true;
                },
                !mGeneralData.is24HourLocaleEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        
        options.add(UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> {
                    mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected());
                    mDeArrowData.setReplaceTitlesEnabled(false);
                },
                mMainUIData.isUnlocalizedTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.time_format_24) + " " + getContext().getString(R.string.time_format),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.is24HourLocaleEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.app_corner_clock),
                option -> {
                    mGeneralData.setGlobalClockEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_clock),
                option -> mPlayerData.setGlobalClockEnabled(option.isSelected()),
                mPlayerData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_ending_time),
                option -> mPlayerData.setGlobalEndingTimeEnabled(option.isSelected()),
                mPlayerData.isGlobalEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_old_look),
                optionItem -> {
                    mMainUIData.setUploadsOldLookEnabled(optionItem.isSelected());
                    mRestartApp = true;
                },
                mMainUIData.isUploadsOldLookEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.fullscreen_mode),
                option -> {
                    mGeneralData.setFullscreenModeEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isFullscreenModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.pinned_channel_rows),
                optionItem -> {
                    mMainUIData.setPinnedChannelRowsEnabled(optionItem.isSelected());
                    mRestartApp = true;
                },
                mMainUIData.isPinnedChannelRowsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.playlists_rows),
                optionItem -> {
                    mMainUIData.setPlaylistsStyle(optionItem.isSelected() ? MainUIData.PLAYLISTS_STYLE_ROWS : MainUIData.PLAYLISTS_STYLE_GRID);
                    BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
                },
                mMainUIData.getPlaylistsStyle() == MainUIData.PLAYLISTS_STYLE_ROWS));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_filter),
                optionItem -> mMainUIData.setChannelsFilterEnabled(optionItem.isSelected()),
                mMainUIData.isChannelsFilterEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channel_search_bar),
                optionItem -> mMainUIData.setChannelSearchBarEnabled(optionItem.isSelected()),
                mMainUIData.isChannelSearchBarEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_auto_load),
                optionItem -> mMainUIData.setUploadsAutoLoadEnabled(optionItem.isSelected()),
                mMainUIData.isUploadsAutoLoadEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
