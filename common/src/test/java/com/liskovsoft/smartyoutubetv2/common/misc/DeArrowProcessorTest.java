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
    private final List<String> requested = new ArrayList<>();
    private final List<Video> updated = new ArrayList<>();

    @Before
    public void setUp() {
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData("channel_dearrow_data", null);
        channels = ChannelDeArrowData.instance(RuntimeEnvironment.getApplication());
        channels.onProfileChanged();
        global = DeArrowData.instance(RuntimeEnvironment.getApplication());
        global.setReplaceTitlesEnabled(false);
        global.setReplaceThumbnailsEnabled(false);
        MediaItemService service = (MediaItemService) Proxy.newProxyInstance(MediaItemService.class.getClassLoader(),
                new Class<?>[]{MediaItemService.class}, (proxy, method, args) -> {
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
    }

    @After
    public void tearDown() {
        processor.dispose();
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

    private static Video video(String id, String channelId) {
        Video video = new Video();
        video.videoId = id;
        video.channelId = channelId;
        video.title = "Original " + id;
        return video;
    }
}
