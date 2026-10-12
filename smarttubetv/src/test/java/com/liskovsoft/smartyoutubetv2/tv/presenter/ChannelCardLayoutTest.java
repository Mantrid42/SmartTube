package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.view.ContextThemeWrapper;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class ChannelCardLayoutTest {
    @Test
    public void bindingAdditionalContentShowsUnderlineAndRecyclingClearsIt() {
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.App_Theme_Leanback_Browse);
        ChannelCardPresenter presenter = new ChannelCardPresenter();
        Presenter.ViewHolder holder = presenter.onCreateViewHolder(new FrameLayout(context));
        Video channel = new Video();
        channel.title = "Channel";
        channel.hasNewContent = true;
        channel.channelNewContentState = Video.CHANNEL_NEW_CONTENT_ADDITIONAL_THIS_REFRESH;
        presenter.onBindViewHolder(holder, channel);
        View underline = holder.view.findViewById(R.id.channel_new_content_underline);
        assertEquals(View.VISIBLE, underline.getVisibility());
        assertEquals(holder.view.findViewById(R.id.channel_card_wrapper).getLayoutParams().width, underline.getLayoutParams().width);
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        holder.view.measure(unspecified, unspecified);
        assertTrue(underline.getMeasuredWidth() > 0);

        channel.channelNewContentState = Video.CHANNEL_NEW_CONTENT_VERIFIED;
        presenter.onBindViewHolder(holder, channel);
        assertEquals(View.GONE, underline.getVisibility());
    }

    @Test
    public void underlineHasTileWidthWhenWrapContentTileIsMeasured() {
        View tile = LayoutInflater.from(RuntimeEnvironment.getApplication()).inflate(R.layout.channel_card, null);
        View underline = tile.findViewById(R.id.channel_new_content_underline);
        underline.setVisibility(View.VISIBLE);
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        tile.measure(unspecified, unspecified);
        tile.layout(0, 0, tile.getMeasuredWidth(), tile.getMeasuredHeight());
        View content = tile.findViewById(R.id.channel_card_wrapper);
        assertTrue(underline.getWidth() > 0);
        assertEquals(content.getWidth(), underline.getWidth());
        assertTrue(underline.getHeight() > 0);
        assertEquals(tile.getHeight(), underline.getBottom());
    }
}
