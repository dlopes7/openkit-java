/**
 * Copyright 2018-2019 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dynatrace.openkit.core.configuration;

import com.dynatrace.openkit.protocol.StatusResponse;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServerConfigurationTest {

    private StatusResponse statusResponse;

    @Before
    public void setUp() {
        statusResponse = mock(StatusResponse.class);
        when(statusResponse.isCapture()).thenReturn(ServerConfiguration.DEFAULT_CAPTURE_ENABLED);
        when(statusResponse.isCaptureCrashes()).thenReturn(ServerConfiguration.DEFAULT_CRASH_REPORTING_ENABLED);
        when(statusResponse.isCaptureErrors()).thenReturn(ServerConfiguration.DEFAULT_ERROR_REPORTING_ENABLED);
        when(statusResponse.getSendInterval()).thenReturn(ServerConfiguration.DEFAULT_SEND_INTERVAL);
        when(statusResponse.getServerID()).thenReturn(ServerConfiguration.DEFAULT_SERVER_ID);
        when(statusResponse.getMaxBeaconSize()).thenReturn(ServerConfiguration.DEFAULT_BEACON_SIZE);
        when(statusResponse.getMultiplicity()).thenReturn(ServerConfiguration.DEFAULT_MULTIPLICITY);
    }

    @Test
    public void inDefaultServerConfigurationCapturingIsEnabled() {
        // then
        assertThat(ServerConfiguration.DEFAULT.isCaptureEnabled(), is(true));
    }

    @Test
    public void inDefaultServerConfigurationCrashReportingIsEnabled() {
        // then
        assertThat(ServerConfiguration.DEFAULT.isCrashReportingEnabled(), is(true));
    }

    @Test
    public void inDefaultServerConfigurationErrorReportingIsEnabled() {
        // then
        assertThat(ServerConfiguration.DEFAULT.isErrorReportingEnabled(), is(true));
    }

    @Test
    public void inDefaultServerConfigurationSendIntervalIsMinusOne() {
        // then
        assertThat(ServerConfiguration.DEFAULT.getSendIntervalInMilliseconds(), is(-1));
    }

    @Test
    public void inDefaultServerConfigurationServerIDIsMinusOne() {
        // then
        assertThat(ServerConfiguration.DEFAULT.getServerID(), is(-1));
    }

    @Test
    public void inDefaultServerConfigurationBeaconSizeIsMinusOne() {
        // then
        assertThat(ServerConfiguration.DEFAULT.getBeaconSizeInBytes(), is(-1));
    }

    @Test
    public void inDefaultServerConfigurationMultiplicityIsOne() {
        // then
        assertThat(ServerConfiguration.DEFAULT.getMultiplicity(), is(1));
    }

    @Test
    public void creatingAServerConfigurationFromNullStatusResponseGivesNull() {
        // when, then
        assertThat(ServerConfiguration.from(null), is(nullValue()));
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesCaptureSettings() {
        // given
        when(statusResponse.isCapture()).thenReturn(false);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.isCaptureEnabled(), is(false));

        verify(statusResponse, times(1)).isCapture();
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesCrashReportingSettings() {
        // given
        when(statusResponse.isCaptureCrashes()).thenReturn(false);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.isCrashReportingEnabled(), is(false));

        verify(statusResponse, times(1)).isCaptureCrashes();
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesErrorReportingSettings() {
        // given
        when(statusResponse.isCaptureErrors()).thenReturn(false);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.isErrorReportingEnabled(), is(false));

        verify(statusResponse, times(1)).isCaptureErrors();
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesSendingIntervalSettings() {
        // given
        when(statusResponse.getSendInterval()).thenReturn(1234);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.getSendIntervalInMilliseconds(), is(1234));

        verify(statusResponse, times(1)).getSendInterval();
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesServerIDSettings() {
        // given
        when(statusResponse.getServerID()).thenReturn(42);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.getServerID(), is(42));

        verify(statusResponse, times(1)).getServerID();
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesBeaconSizeSettings() {
        // given
        when(statusResponse.getMaxBeaconSize()).thenReturn(100 * 1024);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.getBeaconSizeInBytes(), is(100 * 1024));

        verify(statusResponse, times(1)).getMaxBeaconSize();
    }

    @Test
    public void creatingAServerConfigurationFromStatusResponseCopiesMultiplicitySettings() {
        // given
        when(statusResponse.getMultiplicity()).thenReturn(7);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // then
        assertThat(target.getMultiplicity(), is(7));

        verify(statusResponse, times(1)).getMultiplicity();
    }

    @Test
    public void sendingDataToTheServerIsAllowedIfCapturingIsEnabledAndMultiplicityIsGreaterThanZero() {
        // given
        when(statusResponse.isCapture()).thenReturn(true);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingDataAllowed();

        // then
        assertThat(obtained, is(true));
    }

    @Test
    public void sendingDataToTheServerIsNotAllowedIfCapturingIsDisabled() {
        // given
        when(statusResponse.isCapture()).thenReturn(false);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingDataAllowed();

        // then
        assertThat(obtained, is(false));
    }

    @Test
    public void sendingDataToTheServerIsNotAllowedIfCapturingIsEnabledButMultiplicityIsZero() {
        // given
        when(statusResponse.isCapture()).thenReturn(true);
        when(statusResponse.getMultiplicity()).thenReturn(0);
        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingDataAllowed();

        // then
        assertThat(obtained, is(false));
    }

    @Test
    public void sendingCrashesToTheServerIsAllowedIfDataSendingIsAllowedAndCaptureCrashesIsEnabled() {
        // given
        when(statusResponse.isCapture()).thenReturn(true);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        when(statusResponse.isCaptureCrashes()).thenReturn(true);

        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingCrashesAllowed();

        // then
        assertThat(obtained, is(true));
    }

    @Test
    public void sendingCrashesToTheServerIsNotAllowedIfDataSendingIsNotAllowed() {
        // given
        when(statusResponse.isCapture()).thenReturn(false);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        when(statusResponse.isCaptureCrashes()).thenReturn(true);

        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingCrashesAllowed();

        // then
        assertThat(obtained, is(false));
    }

    @Test
    public void sendingCrashesToTheServerIsNotAllowedIfDataSendingIsAllowedButCaptureCrashesIsDisabled() {
        // given
        when(statusResponse.isCapture()).thenReturn(true);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        when(statusResponse.isCaptureCrashes()).thenReturn(false);

        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingCrashesAllowed();

        // then
        assertThat(obtained, is(false));
    }

    @Test
    public void sendingErrorToTheServerIsAllowedIfDataSendingIsAllowedAndCaptureErrorIsEnabled() {
        // given
        when(statusResponse.isCapture()).thenReturn(true);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        when(statusResponse.isCaptureErrors()).thenReturn(true);

        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingErrorsAllowed();

        // then
        assertThat(obtained, is(true));
    }

    @Test
    public void sendingErrorToTheServerIsNotAllowedIfDataSendingIsNotAllowed() {
        // given
        when(statusResponse.isCapture()).thenReturn(false);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        when(statusResponse.isCaptureErrors()).thenReturn(true);

        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingErrorsAllowed();

        // then
        assertThat(obtained, is(false));
    }

    @Test
    public void sendingErrorsToTheServerIsNotAllowedIfDataSendingIsAllowedButCaptureErrorsDisabled() {
        // given
        when(statusResponse.isCapture()).thenReturn(true);
        when(statusResponse.getMultiplicity()).thenReturn(1);
        when(statusResponse.isCaptureErrors()).thenReturn(false);

        ServerConfiguration target = ServerConfiguration.from(statusResponse);

        // when
        boolean obtained = target.isSendingErrorsAllowed();

        // then
        assertThat(obtained, is(false));
    }
}
