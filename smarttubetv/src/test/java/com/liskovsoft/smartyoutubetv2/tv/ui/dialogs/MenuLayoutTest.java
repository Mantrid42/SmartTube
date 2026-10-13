package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class MenuLayoutTest {
    @Test
    public void decorationsAreVisibleButCannotReceiveFocusOrClicks() {
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.App_Theme_Leanback_Browse);
        AppPreferenceManager manager = new AppPreferenceManager(context);
        for (OptionCategory category : new OptionCategory[] {OptionCategory.heading("Global Activation"), OptionCategory.spacer()}) {
            Preference preference = manager.createPreference(category);
            View row = LayoutInflater.from(context).inflate(preference.getLayoutResource(), null);
            preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));
            assertFalse(preference.isSelectable());
            assertFalse(row.isFocusable());
            assertFalse(row.isClickable());
            assertEquals(View.VISIBLE, row.getVisibility());
            if (category.type == OptionCategory.TYPE_HEADING) {
                TextView title = (TextView) row;
                assertEquals("Global Activation", title.getText().toString());
                assertTrue(title.getTypeface().isBold());
                assertTrue(title.getAlpha() < 1f);
            }
        }
    }

    @Test
    public void selectingOneTitleSwitchUnchecksItsAlternative() {
        AppPreferenceManager manager = new AppPreferenceManager(RuntimeEnvironment.getApplication());
        OptionItem original = UiOptionItem.from("Original", option -> {}, true);
        OptionItem crowdsourced = UiOptionItem.from("Crowdsourced", option -> {}, false);
        crowdsourced.setRadio(original);
        SwitchPreference first = (SwitchPreference) manager.createPreference(OptionCategory.singleSwitch(original));
        SwitchPreference second = (SwitchPreference) manager.createPreference(OptionCategory.singleSwitch(crowdsourced));
        first.setChecked(true);
        assertTrue(second.callChangeListener(true));
        assertFalse(first.isChecked());
        assertFalse(original.isSelected());
        assertTrue(crowdsourced.isSelected());
    }
}
