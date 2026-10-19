package com.zfdang.touchhelper;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import com.zfdang.touchhelper.ui.home.HomeViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeViewModelTest {

    private HomeViewModel viewModel;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        viewModel = new HomeViewModel();
    }

    @Test
    public void testLiveDataInitializationAndRefresh() {
        assertNotNull(viewModel.getAccessibilityPermission());
        assertNotNull(viewModel.getPowerOptimization());

        viewModel.refreshServiceStatus(context);

        assertNotNull(viewModel.getAccessibilityPermission().getValue());
        assertNotNull(viewModel.getPowerOptimization().getValue());
    }
}
