package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelDeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.reactivex.Observable;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DeArrowProcessorTest {
    private ChannelDeArrowData channels;
    private DeArrowData global;
    private DeArrowProcessor processor;
    private UnlocalizedTitleProcessor originalTitles;
    private com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData ui;
    private final List<String> requested = new ArrayList<>();
    private final List<Video> updated = new ArrayList<>();

    @Before
    public void setUp() {
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData("channel_dearrow_data", "");
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData("channel_dearrow_data_excluded", "");
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData("channel_dearrow_data_options",
                com.liskovsoft.sharedutils.helpers.Helpers.mergeData(true, true, false, 0));
        channels = ChannelDeArrowData.instance(RuntimeEnvironment.getApplication());
        channels.onProfileChanged();
        global = DeArrowData.instance(RuntimeEnvironment.getApplication());
        global.setReplaceTitlesEnabled(false);
        global.setReplaceThumbnailsEnabled(false);
        ui = com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.instance(RuntimeEnvironment.getApplication());
        ui.setUnlocalizedTitlesEnabled(false);
        MediaItemService service = (MediaItemService) Proxy.newProxyInstance(MediaItemService.class.getClassLoader(),
                new Class<?>[]{MediaItemService.class}, (proxy, method, args) -> {
                    if ("getUnlocalizedTitleObserve".equals(method.getName())) {
                        return Observable.just("Original-language " + args[0]);
                    }
                    if (!"getDeArrowDataObserve".equals(method.getName())) {
                        throw new AssertionError("Unexpected service call: " + method.getName());
                    }
                    List<String> ids = (List<String>) args[0];
                    requested.addAll(ids);
                    return Observable.fromIterable(ids).map(id -> new com.liskovsoft.mediaserviceinterfaces.data.DeArrowData() {
                        public String getVideoId() { return id; }
                        public String getTitle() { return "DeArrow " + id; }
                        public String getThumbnailUrl() { return "https://example.com/" + id; }
                    });
                });
        processor = new DeArrowProcessor(global, channels, service, updated::add);
        originalTitles = new UnlocalizedTitleProcessor(ui, channels, service, updated::add);
    }

    @After
    public void tearDown() {
        processor.dispose();
        originalTitles.dispose();
        ui.removeOnChange(originalTitles);
        global.removeOnChange(processor);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void selectedChannelReplacesBothWhileGlobalOptionsStayOff() {
        channels.setEnabled("selected", true);
        Video selected = video("first", "selected");
        Video other = video("second", "other");
        processor.process(VideoGroup.from(Arrays.asList(selected, other)));
        assertEquals(Arrays.asList("first"), requested);
        assertEquals("DeArrow first", selected.deArrowTitle);
        assertEquals("https://example.com/first", selected.altCardImageUrl);
        assertFalse(other.deArrowProcessed);
        assertNull(other.deArrowTitle);
        assertFalse(global.isReplaceTitlesEnabled());
        assertFalse(global.isReplaceThumbnailsEnabled());
        assertEquals(Arrays.asList(selected), updated);
    }

    @Test
    public void channelContextProcessesContinuationWithoutItemChannelIds() {
        channels.setEnabled("selected", true);
        Video first = video("first", null);
        Video next = video("next", null);
        processor.process(VideoGroup.from(Arrays.asList(first)), "selected");
        processor.process(VideoGroup.from(Arrays.asList(next)), "selected");
        assertEquals(Arrays.asList("first", "next"), requested);
        assertEquals("DeArrow next", next.deArrowTitle);
    }

    @Test
    public void explicitOtherChannelDoesNotInheritSelectedContext() {
        channels.setEnabled("selected", true);
        Video other = video("other", "different");
        processor.process(VideoGroup.from(Arrays.asList(other)), "selected");
        assertTrue(requested.isEmpty());
        assertFalse(other.deArrowProcessed);
    }

    @Test
    public void globalTitleOptionKeepsItsExistingScope() {
        global.setReplaceTitlesEnabled(true);
        Video other = video("other", "different");
        processor.process(VideoGroup.from(Arrays.asList(other)));
        assertEquals("DeArrow other", other.deArrowTitle);
        assertNull(other.altCardImageUrl);
    }

    @Test
    public void channelSelectionPersistsAndCanBeDisabled() {
        channels.setEnabled("selected", true);
        channels.onProfileChanged();
        assertTrue(channels.isEnabled("selected"));
        channels.setEnabled("selected", false);
        channels.onProfileChanged();
        Video selected = video("first", "selected");
        processor.process(VideoGroup.from(Arrays.asList(selected)));
        assertTrue(requested.isEmpty());
        assertFalse(channels.isEnabled("selected"));
    }

    @Test
    public void processedVideosAndChannelCardsDoNotCauseRequests() {
        channels.setEnabled("selected", true);
        Video selected = video("first", "selected");
        Video channelCard = video(null, "selected");
        VideoGroup group = VideoGroup.from(Arrays.asList(selected, channelCard));
        processor.process(group);
        processor.process(group);
        assertEquals(Arrays.asList("first"), requested);
    }

    @Test
    public void subscriptionReloadKeyRetainsChannelOptIn() {
        Video channel = video(null, "selected");
        channel.reloadPageKey = "subscription-key";
        channels.setEnabled(channel, true);
        channels.onProfileChanged();
        channel.channelId = null;
        Video upload = video("first", null);
        processor.process(VideoGroup.from(Arrays.asList(upload)), ChannelDeArrowData.getChannelKey(channel));
        assertEquals("DeArrow first", upload.deArrowTitle);
        channel.channelId = "selected";
        channels.setEnabled(channel, false);
        assertFalse(channels.isEnabled("selected"));
        assertFalse(channels.isEnabled("subscription-key"));
    }

    @Test
    public void includedChannelsHaveIndependentTitleAndThumbnailOptions() {
        channels.setEnabled("selected", true);
        channels.setReplaceTitlesEnabled(false);
        channels.setReplaceThumbnailsEnabled(true);
        global.setReplaceTitlesEnabled(true);
        Video selected = video("first", "selected");
        Video other = video("other", "different");
        processor.process(VideoGroup.from(Arrays.asList(selected, other)));
        assertNull(selected.deArrowTitle);
        assertEquals("https://example.com/first", selected.altCardImageUrl);
        assertEquals("DeArrow other", other.deArrowTitle);
        assertNull(other.altCardImageUrl);
        channels.setReplaceTitlesEnabled(true);
        channels.setReplaceThumbnailsEnabled(false);
        Video titleOnly = video("title", "selected");
        processor.process(VideoGroup.from(Arrays.asList(titleOnly)));
        assertEquals("DeArrow title", titleOnly.deArrowTitle);
        assertNull(titleOnly.altCardImageUrl);
    }

    @Test
    public void exclusionsOverrideGlobalAndIncludedChannelOptions() {
        global.setReplaceTitlesEnabled(true);
        global.setReplaceThumbnailsEnabled(true);
        channels.setEnabled("selected", true);
        Video channel = video(null, "selected");
        channel.reloadPageKey = "reload";
        channels.setExcluded(channel, true);
        channels.onProfileChanged();
        Video selected = video("first", "selected");
        Video continuation = video("next", null);
        processor.process(VideoGroup.from(Arrays.asList(selected)));
        processor.process(VideoGroup.from(Arrays.asList(continuation)), "reload");
        assertTrue(requested.isEmpty());
        assertFalse(selected.deArrowProcessed);
        channels.setExcluded(channel, false);
        processor.process(VideoGroup.from(Arrays.asList(selected)));
        assertEquals("DeArrow first", selected.deArrowTitle);
        assertFalse(channels.isExcluded("reload"));
    }

    @Test
    public void disabledChannelOptionsPersistAndSkipRequests() {
        channels.setEnabled("selected", true);
        channels.setReplaceTitlesEnabled(false);
        channels.setReplaceThumbnailsEnabled(false);
        channels.onProfileChanged();
        global.setReplaceTitlesEnabled(true);
        processor.process(VideoGroup.from(Arrays.asList(video("first", "selected"))));
        assertTrue(requested.isEmpty());
        assertFalse(channels.isReplaceTitlesEnabled());
        assertFalse(channels.isReplaceThumbnailsEnabled());
    }

    @Test
    public void originalTitlesWorkWithChannelThumbnailsAndRespectExclusions() {
        channels.setEnabled("selected", true);
        channels.setUnlocalizedTitlesEnabled(true);
        Video selected = video("first", null);
        Video other = video("other", "different");
        VideoGroup group = VideoGroup.from(Arrays.asList(selected, other));
        processor.process(group, "selected");
        originalTitles.process(group);
        assertEquals("Original-language first", selected.deArrowTitle);
        assertEquals("https://example.com/first", selected.altCardImageUrl);
        assertNull(other.deArrowTitle);
        channels.setExcluded("selected", true);
        ui.setUnlocalizedTitlesEnabled(true);
        Video excluded = video("excluded", "selected");
        originalTitles.process(VideoGroup.from(Arrays.asList(excluded)));
        assertNull(excluded.deArrowTitle);
    }

    @Test
    public void fallbackThumbnailsRespectChannelOptionsAndExclusions() {
        channels.setEnabled("selected", true);
        channels.setThumbQuality(2);
        channels.onProfileChanged();
        Video selected = video("first", null);
        selected.deArrowChannelId = "selected";
        assertEquals(2, channels.getThumbQuality(selected, 3));
        assertEquals(3, channels.getThumbQuality(video("other", "different"), 3));
        channels.setExcluded("selected", true);
        assertEquals(0, channels.getThumbQuality(selected, 3));
    }

    private static Video video(String id, String channelId) {
        Video video = new Video();
        video.videoId = id;
        video.channelId = channelId;
        video.title = "Original " + id;
        return video;
    }
}
