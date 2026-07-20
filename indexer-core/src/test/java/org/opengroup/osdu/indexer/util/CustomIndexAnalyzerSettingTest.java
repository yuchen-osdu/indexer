/*
 * Copyright © Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class CustomIndexAnalyzerSettingTest {
    @Mock
    private BooleanFeatureFlagClient booleanFeatureFlagClient;

    @InjectMocks
    private CustomIndexAnalyzerSetting customIndexAnalyzerSetting;

    @Test
    public void isEnabled_is_true() {
        when(booleanFeatureFlagClient.isEnabled(eq("custom-index-analyzer-enabled"), anyBoolean())).thenReturn(true);
        Assert.assertTrue(customIndexAnalyzerSetting.isEnabled());
    }

    @Test
    public void isEnabled_is_false() {
        when(booleanFeatureFlagClient.isEnabled(eq("custom-index-analyzer-enabled"), anyBoolean())).thenReturn(false);
        Assert.assertFalse(customIndexAnalyzerSetting.isEnabled());
    }
}
