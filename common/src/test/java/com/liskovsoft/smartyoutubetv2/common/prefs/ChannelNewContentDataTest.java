package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ChannelNewContentDataTest {
    private ChannelNewContentData data;
    private Video channel;
    private MediaGroup group;

    @Before
    public void setUp() {
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData("channel_new_content_data", null);
        data = ChannelNewContentData.instance(RuntimeEnvironment.getApplication());
        data.onProfileChanged();
        channel = new Video();
        channel.channelId = "channel";
        channel.title = "Channel";
        group = (MediaGroup) Proxy.newProxyInstance(MediaGroup.class.getClassLoader(), new Class<?>[]{MediaGroup.class},
                (proxy, method, args) -> "getChannelId".equals(method.getName()) ? "channel" : null);
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void newlyFlaggedChannelKeepsYellowChannelAndVideoHighlights() {
        seedUnreadChannel();
        assertEquals(Video.CHANNEL_NEW_CONTENT_VERIFIED_THIS_REFRESH, channel.channelNewContentState);
        List<Video> uploads = videos("unread", "seen");
        data.applyNewVideoMarkers(channel, uploads);
        assertTrue(uploads.get(0).channelNewContentNewVideo);
        assertFalse(uploads.get(1).channelNewContentNewVideo);
        assertFalse(data.needsVerification(channel));
    }

    @Test
    public void existingUnreadChannelHighlightsOnlyAdditionalUploads() {
        seedUnreadChannel();
        data.beginRefresh();
        data.apply(channel);
        assertTrue(data.needsVerification(channel));
        List<Video> uploads = videos("additional", "unread", "seen");
        data.setVerified(channel, uploads, group);
        assertEquals(Video.CHANNEL_NEW_CONTENT_ADDITIONAL_THIS_REFRESH, channel.channelNewContentState);
        assertTrue(uploads.get(0).channelNewContentNewVideo);
        assertFalse(uploads.get(1).channelNewContentNewVideo);
        assertFalse(uploads.get(2).channelNewContentNewVideo);
        assertTrue(ChannelNewContentData.isNewContentState(channel.channelNewContentState));
        assertFalse(data.needsVerification(channel));

        List<Video> reopened = videos("additional", "unread");
        data.applyNewVideoMarkers(channel, reopened);
        assertTrue(reopened.get(0).channelNewContentNewVideo);
        assertFalse(reopened.get(1).channelNewContentNewVideo);
    }

    @Test
    public void unchangedUnreadChannelStaysRedWithoutFreshHighlights() {
        seedUnreadChannel();
        data.beginRefresh();
        List<Video> uploads = videos("unread", "seen");
        data.setVerified(channel, uploads, group);
        assertEquals(Video.CHANNEL_NEW_CONTENT_VERIFIED, channel.channelNewContentState);
        assertTrue(channel.hasNewContent);
        assertFalse(uploads.get(0).channelNewContentNewVideo);
        assertFalse(data.needsVerification(channel));
    }

    @Test
    public void nextRefreshClearsAdditionalMarkersAndCanScanAgain() {
        seedUnreadChannel();
        data.beginRefresh();
        data.setVerified(channel, videos("additional", "unread", "seen"), group);
        data.beginRefresh();
        data.apply(channel);
        assertEquals(Video.CHANNEL_NEW_CONTENT_VERIFIED, channel.channelNewContentState);
        assertTrue(data.needsVerification(channel));
        List<Video> uploads = videos("additional", "unread", "seen");
        data.setVerified(channel, uploads, group);
        assertEquals(Video.CHANNEL_NEW_CONTENT_VERIFIED, channel.channelNewContentState);
        assertFalse(uploads.get(0).channelNewContentNewVideo);
    }

    @Test
    public void manuallyUnreadChannelDoesNotHighlightOldUploadsAgain() {
        data.markWatched(channel, videos("seen"));
        data.markUnseen(channel);
        data.beginRefresh();
        List<Video> uploads = videos("seen");
        data.setVerified(channel, uploads, group);
        assertEquals(Video.CHANNEL_NEW_CONTENT_VERIFIED, channel.channelNewContentState);
        assertFalse(uploads.get(0).channelNewContentNewVideo);
    }

    @Test
    public void unreadChannelWithoutWatchedBaselineSurvivesAnUnchangedScan() {
        data.setVerified(channel, videos("old"), group);
        data.markUnseen(channel);
        data.beginRefresh();
        data.setVerified(channel, videos("added", "old"), group);
        assertEquals(Video.CHANNEL_NEW_CONTENT_ADDITIONAL_THIS_REFRESH, channel.channelNewContentState);
        data.beginRefresh();
        List<Video> uploads = videos("added", "old");
        data.setVerified(channel, uploads, group);
        assertEquals(Video.CHANNEL_NEW_CONTENT_VERIFIED, channel.channelNewContentState);
        assertFalse(uploads.get(0).channelNewContentNewVideo);
    }

    private void seedUnreadChannel() {
        data.markWatched(channel, videos("seen"));
        data.beginRefresh();
        channel.hasNewContent = true;
        data.apply(channel);
        assertTrue(data.needsVerification(channel));
        data.setVerified(channel, videos("unread", "seen"), group);
        data.finishRefresh();
    }

    private static List<Video> videos(String... ids) {
        Video[] result = new Video[ids.length];
        for (int i = 0; i < ids.length; i++) {
            result[i] = new Video();
            result[i].videoId = ids[i];
        }
        return Arrays.asList(result);
    }
}
