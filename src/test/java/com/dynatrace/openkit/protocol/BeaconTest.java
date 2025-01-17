/**
 * Copyright 2018-2019 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dynatrace.openkit.protocol;

import com.dynatrace.openkit.CrashReportingLevel;
import com.dynatrace.openkit.DataCollectionLevel;
import com.dynatrace.openkit.api.Logger;
import com.dynatrace.openkit.core.caching.BeaconCacheImpl;
import com.dynatrace.openkit.core.configuration.BeaconConfiguration;
import com.dynatrace.openkit.core.configuration.Configuration;
import com.dynatrace.openkit.core.configuration.HTTPClientConfiguration;
import com.dynatrace.openkit.core.configuration.PrivacyConfiguration;
import com.dynatrace.openkit.core.objects.BaseActionImpl;
import com.dynatrace.openkit.core.objects.Device;
import com.dynatrace.openkit.core.objects.OpenKitComposite;
import com.dynatrace.openkit.core.objects.RootActionImpl;
import com.dynatrace.openkit.core.objects.SessionImpl;
import com.dynatrace.openkit.core.objects.WebRequestTracerBaseImpl;
import com.dynatrace.openkit.core.objects.WebRequestTracerStringURL;
import com.dynatrace.openkit.core.objects.WebRequestTracerURLConnection;
import com.dynatrace.openkit.providers.HTTPClientProvider;
import com.dynatrace.openkit.providers.ThreadIDProvider;
import com.dynatrace.openkit.providers.TimingProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class BeaconTest {

    private static final String APP_ID = "appID";
    private static final String APP_NAME = "appName";
    private static final int ACTION_ID = 17;
    private static final int SERVER_ID = 123;
    private static final String DEVICE_ID = "456";
    private static final int THREAD_ID = 1234567;

    private Configuration configuration;
    private ThreadIDProvider threadIDProvider;
    private TimingProvider timingProvider;
    private OpenKitComposite parentOpenKitObject;

    private Logger logger;

    @Before
    public void setUp() {
        configuration = mock(Configuration.class);
        when(configuration.getApplicationID()).thenReturn(APP_ID);
        when(configuration.getApplicationIDPercentEncoded()).thenReturn(APP_ID);
        when(configuration.getApplicationName()).thenReturn(APP_NAME);
        when(configuration.getDevice()).thenReturn(new Device("", "", ""));
        when(configuration.getDeviceID()).thenReturn(DEVICE_ID);
        when(configuration.isCapture()).thenReturn(true);
        when(configuration.isCaptureErrors()).thenReturn(true);
        when(configuration.isCaptureCrashes()).thenReturn(true);
        when(configuration.getMaxBeaconSize()).thenReturn(30 * 1024); // 30kB

        HTTPClientConfiguration mockHTTPClientConfiguration = mock(HTTPClientConfiguration.class);
        when(mockHTTPClientConfiguration.getServerID()).thenReturn(SERVER_ID);
        when(configuration.getHttpClientConfig()).thenReturn(mockHTTPClientConfiguration);

        BeaconConfiguration beaconConfiguration = new BeaconConfiguration(1);
        when(configuration.getBeaconConfiguration()).thenReturn(beaconConfiguration);

        PrivacyConfiguration privacyConfiguration = new PrivacyConfiguration(PrivacyConfiguration.DEFAULT_DATA_COLLECTION_LEVEL,
            PrivacyConfiguration.DEFAULT_CRASH_REPORTING_LEVEL);
        when(configuration.getPrivacyConfiguration()).thenReturn(privacyConfiguration);

        threadIDProvider = mock(ThreadIDProvider.class);
        when(threadIDProvider.getThreadID()).thenReturn(THREAD_ID);

        timingProvider = mock(TimingProvider.class);
        when(timingProvider.provideTimestampInMilliseconds()).thenReturn(0L);

        logger = mock(Logger.class);

        parentOpenKitObject = mock(OpenKitComposite.class);
        when(parentOpenKitObject.getActionID()).thenReturn(0);
    }

    @Test
    public void defaultBeaconConfigurationDoesNotDisableCapturing() {

        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // then
        assertThat(target.isCapturingDisabled(), is(false));
    }

    @Test
    public void defaultBeaconConfigurationSetsMultiplicityToOne() {

        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // then
        assertThat(target.getMultiplicity(), is(equalTo(1)));
    }

    @Test
    public void createIDs() {
        // create test environment
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // verify that the created sequence numbers are incremented
        int id1 = beacon.createID();
        assertThat(id1, is(1));

        int id2 = beacon.createID();
        assertThat(id2, is(2));

        int id3 = beacon.createID();
        assertThat(id3, is(3));
    }

    @Test
    public void getCurrentTimestamp() {

        // given
        when(timingProvider.provideTimestampInMilliseconds()).thenReturn(42L);
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when obtaining the timestamp
        long timestamp = beacon.getCurrentTimestamp();

        // then verify
        assertThat(timestamp, is(equalTo(42L)));

        // verify called twice (once in Beacon's ctor) and once when invoking the call
        verify(timingProvider, times(2)).provideTimestampInMilliseconds();
    }

    @Test
    public void createSequenceNumbers() {
        // create test environment
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // verify that the created sequence numbers are incremented
        int id1 = beacon.createSequenceNumber();
        assertThat(id1, is(1));

        int id2 = beacon.createSequenceNumber();
        assertThat(id2, is(2));

        int id3 = beacon.createSequenceNumber();
        assertThat(id3, is(3));
    }

    @Test
    public void createWebRequestTag() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        int sequenceNo = 42;
        String tag = beacon.createTag(ACTION_ID, sequenceNo);

        // then
        assertThat(tag, is(equalTo("MT_3_" + SERVER_ID + "_" + DEVICE_ID + "_0_" + APP_ID + "_" + ACTION_ID + "_" + THREAD_ID + "_" + sequenceNo)));
    }

    @Test
    public void createWebRequestTagEncodesDeviceIDPropperly() {
        // given
        when(configuration.getDeviceID()).thenReturn("device_id/");
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        int sequenceNo = 42;
        String tag = beacon.createTag(ACTION_ID, sequenceNo);

        // then
        assertThat(tag, is(equalTo("MT_3_" + SERVER_ID + "_device%5Fid%2F_0_" + APP_ID + "_" + ACTION_ID + "_" + THREAD_ID + "_" + sequenceNo)));

        // also ensure that the application ID is the encoded one
        verify(configuration, times(1)).getApplicationIDPercentEncoded();
    }

    @Test
    public void addValidActionEvent() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        BaseActionImpl action = mock(BaseActionImpl.class);
        when(action.getID()).thenReturn(ACTION_ID);
        int parentID = 13;
        when(action.getParentID()).thenReturn(parentID);
        String actionName = "MyAction";
        when(action.getName()).thenReturn(actionName);

        // when
        beacon.addAction(action);
        String[] actions = beacon.getActions();

        // then
        assertThat(actions, is(equalTo(new String[]{
            "et=1&na=" + actionName + "&it=" + THREAD_ID + "&ca=" + ACTION_ID + "&pa=" + parentID + "&s0=0&t0=0&s1=0&t1=0"
        })));
    }

    @Test
    public void addEndSessionEvent() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        SessionImpl session = mock(SessionImpl.class);

        // when
        beacon.endSession(session);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=19&it=" + THREAD_ID + "&pa=0&s0=1&t0=0"})));
    }

    @Test
    public void reportValidValueInt() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String valueName = "IntValue";
        int value = 42;

        // when
        beacon.reportValue(ACTION_ID, valueName, value);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=12&na=" + valueName + "&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0&vl=" + String.valueOf(value)
        })));
    }

    @Test
    public void reportValidValueDouble() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String valueName = "DoubleValue";
        double value = 3.1415;

        // when
        beacon.reportValue(ACTION_ID, valueName, value);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=13&na=" + valueName + "&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0&vl=" + String.valueOf(value)
        })));
    }

    @Test
    public void reportValidValueString() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String valueName = "StringValue";
        String value = "HelloWorld";

        // when
        beacon.reportValue(ACTION_ID, valueName, value);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=11&na=" + valueName + "&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0&vl=" + value
        })));
    }

    @Test
    public void reportValueStringWithValueNull() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String valueName = "StringValue";

        // when
        beacon.reportValue(ACTION_ID, valueName, null);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=11&na=StringValue&it=1234567&pa=17&s0=1&t0=0"})));
    }

    @Test
    public void reportValueStringWithValueNullAndNameNull() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        beacon.reportValue(ACTION_ID, null, null);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=11&it=1234567&pa=17&s0=1&t0=0"})));
    }

    @Test
    public void reportValidEvent() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String eventName = "SomeEvent";

        // when
        beacon.reportEvent(ACTION_ID, eventName);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=10&na=" + eventName + "&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0"})));
    }

    @Test
    public void reportEventWithNameNull() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        beacon.reportEvent(ACTION_ID, null);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=10&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0"})));
    }

    @Test
    public void reportError() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String errorName = "SomeEvent";
        int errorCode = -123;
        String reason = "SomeReason";

        // when
        beacon.reportError(ACTION_ID, errorName, errorCode, reason);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=40&na=" + errorName + "&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0&ev=" + errorCode + "&rs=" + reason
        })));
    }

    @Test
    public void reportErrorNull() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        int errorCode = -123;

        // when
        beacon.reportError(ACTION_ID, null, errorCode, null);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=40&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=1&t0=0&ev=" + errorCode})));
    }

    @Test
    public void reportValidCrash() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String errorName = "SomeEvent";
        String reason = "SomeReason";
        String stacktrace = "SomeStacktrace";

        // when
        beacon.reportCrash(errorName, reason, stacktrace);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=50&na=" + errorName + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&rs=" + reason + "&st=" + stacktrace
        })));
    }

    @Test
    public void reportCrashWithDetailsNull() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String errorName = "errorName";

        // when
        beacon.reportCrash(errorName, null, null);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=50&na=" + errorName + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0"})));
    }

    @Test
    public void addWebRequest() {
        // given
        final Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        WebRequestTracerURLConnection webRequestTracer = mock(WebRequestTracerURLConnection.class);
        when(webRequestTracer.getBytesSent()).thenReturn(13);
        when(webRequestTracer.getBytesReceived()).thenReturn(14);
        when(webRequestTracer.getResponseCode()).thenReturn(15);

        // when
        beacon.addWebRequest(ACTION_ID, webRequestTracer);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=30&it=" + THREAD_ID + "&pa=" + ACTION_ID + "&s0=0&t0=0&s1=0&t1=0&bs=13&br=14&rc=15"
        })));
    }

    @Test
    public void addUserIdentifyEvent() {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String userID = "myTestUser";

        // when
        beacon.identifyUser(userID);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=60&na=" + userID + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0"})));
    }

    @Test
    public void addUserIdentifyWithNullUserIDEvent() {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        beacon.identifyUser(null);
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=60&it=" + THREAD_ID + "&pa=0&s0=1&t0=0"})));
    }

    @Test
    public void canAddSentBytesToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);
        int bytesSent = 12321;

        // when
        webRequest.start().setBytesSent(bytesSent).stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0&bs=" + bytesSent
        })));
    }

    @Test
    public void canAddSentBytesValueZeroToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);
        int bytesSent = 0;

        // when
        webRequest.start().setBytesSent(bytesSent).stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0&bs=" + bytesSent
        })));
    }

    @Test
    public void cannotAddSentBytesWithInvalidValueSmallerZeroToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);

        // when
        webRequest.start().setBytesSent(-5).stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0"})));
    }

    @Test
    public void canAddReceivedBytesToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);
        int bytesReceived = 12321;

        // when
        webRequest.start().setBytesReceived(bytesReceived).stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0&br=" + String
                .valueOf(bytesReceived)
        })));
    }

    @Test
    public void canAddReceivedBytesValueZeroToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);
        int bytesReceived = 0;

        // when
        webRequest.start().setBytesReceived(bytesReceived).stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0&br=" + String
                .valueOf(bytesReceived)
        })));
    }

    @Test
    public void cannotAddReceivedBytesWithInvalidValueSmallerZeroToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);

        // when
        webRequest.start().setBytesReceived(-1).stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{"et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0"})));
    }

    @Test
    public void canAddBothSentBytesAndReceivedBytesToWebRequestTracer() throws UnsupportedEncodingException {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        String testURL = "https://localhost";
        WebRequestTracerStringURL webRequest = new WebRequestTracerStringURL(logger, parentOpenKitObject, beacon, testURL);
        int bytesReceived = 12321;
        int bytesSent = 123;

        // when
        webRequest.start()
                  .setBytesSent(bytesSent)
                  .setBytesReceived(bytesReceived)
                  .stop(-1); // stop will add the web request to the beacon
        String[] events = beacon.getEvents();

        // then
        assertThat(events, is(equalTo(new String[]{
            "et=30&na=" + URLEncoder.encode(testURL, "UTF-8") + "&it=" + THREAD_ID + "&pa=0&s0=1&t0=0&s1=2&t1=0&bs="
                    + bytesSent + "&br=" + bytesReceived
        })));
    }

    @Test
    public void canAddRootActionIfCaptureIsOn() {
        // given
        when(configuration.isCapture()).thenReturn(true);
        String actionName = "rootAction";
        RootActionImpl rootAction = mock(RootActionImpl.class);
        when(rootAction.getName()).thenReturn(actionName);

        // when
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        beacon.addAction(rootAction);

        String[] actions = beacon.getActions();

        // then
        assertThat(actions, is(equalTo(new String[]{"et=1&na=" + actionName + "&it=" + THREAD_ID + "&ca=0&pa=0&s0=0&t0=0&s1=0&t1=0"})));
    }

    @Test
    public void cannotAddRootActionIfCaptureIsOff() {
        // given
        when(configuration.isCapture()).thenReturn(false);
        String actionName = "rootAction";
        RootActionImpl rootAction = mock(RootActionImpl.class);
        when(rootAction.getName()).thenReturn(actionName);

        // when
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        beacon.addAction(rootAction);

        String[] actions = beacon.getActions();

        // then
        assertThat(actions, is(arrayWithSize(0)));
    }

    @Test
    public void canHandleNoDataInBeaconSend() {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        HTTPClientProvider httpClientProvider = mock(HTTPClientProvider.class);
        HTTPClient mockClient = mock(HTTPClient.class);
        when(httpClientProvider.createClient(any(HTTPClientConfiguration.class))).thenReturn(mockClient);

        // when
        StatusResponse response = beacon.send(httpClientProvider);

        // then (verify, that null is returned as no data was sent)
        assertThat(response, nullValue());
    }

    @Test
    public void sendValidData() {
        // given
        String ipAddress = "127.0.0.1";
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, ipAddress, threadIDProvider, timingProvider);
        HTTPClientProvider httpClientProvider = mock(HTTPClientProvider.class);
        HTTPClient httpClient = mock(HTTPClient.class);
        int responseCode = 200;
        when(httpClient.sendBeaconRequest(any(String.class), any(byte[].class))).thenReturn(new StatusResponse(logger, "", responseCode, Collections.<String, List<String>>emptyMap()));
        when(httpClientProvider.createClient(any(HTTPClientConfiguration.class))).thenReturn(httpClient);

        // when (add data and try to send it)
        beacon.reportCrash("errorName", "errorReason", "errorStackTrace");
        StatusResponse response = beacon.send(httpClientProvider);

        // then
        assertThat(response, notNullValue());
        assertThat(response.getResponseCode(), is(responseCode));
        verify(httpClient, times(1)).sendBeaconRequest(eq(ipAddress), any(byte[].class));
    }

    @Test
    public void sendDataAndFakeErrorResponse() {
        // given
        String ipAddress = "127.0.0.1";
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, ipAddress, threadIDProvider, timingProvider);
        HTTPClientProvider httpClientProvider = mock(HTTPClientProvider.class);
        HTTPClient httpClient = mock(HTTPClient.class);
        int responseCode = 418;
        when(httpClient.sendBeaconRequest(any(String.class), any(byte[].class))).thenReturn(new StatusResponse(logger, "", responseCode, Collections.<String, List<String>>emptyMap()));
        when(httpClientProvider.createClient(any(HTTPClientConfiguration.class))).thenReturn(httpClient);

        // when (add data and try to send it)
        beacon.reportCrash("errorName", "errorReason", "errorStackTrace");
        StatusResponse response = beacon.send(httpClientProvider);

        // then
        assertThat(response, notNullValue());
        assertThat(response.getResponseCode(), is(responseCode));
        verify(httpClient, times(1)).sendBeaconRequest(eq(ipAddress), any(byte[].class));
    }

    @Test
    public void clearDataFromBeaconCache() {
        // given
        Beacon beacon = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        // add various data
        BaseActionImpl action = mock(BaseActionImpl.class);
        when(action.getID()). thenReturn(ACTION_ID);
        beacon.addAction(action);
        beacon.reportValue(ACTION_ID, "IntValue", 42);
        beacon.reportValue(ACTION_ID, "DoubleValue", 3.1415);
        beacon.reportValue(ACTION_ID, "StringValue", "HelloWorld");
        beacon.reportEvent(ACTION_ID, "SomeEvent");
        beacon.reportError(ACTION_ID, "SomeError", -123, "SomeReason");
        beacon.reportCrash("SomeCrash", "SomeReason", "SomeStacktrace");
        SessionImpl session = mock(SessionImpl.class);
        beacon.endSession(session);

        // when
        beacon.clearData();

        // then (verify, all data is cleared)
        String[] events = beacon.getEvents();
        assertThat(events, emptyArray());
        String[] actions = beacon.getActions();
        assertThat(actions, emptyArray());
        assertThat(beacon.isEmpty(), is(true));
    }

    @Test
    public void noSessionIsAddedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));
        SessionImpl session = mock(SessionImpl.class);

        // when
        target.endSession(session);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
        verifyZeroInteractions(session);
    }

    @Test
    public void noActionIsAddedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));
        BaseActionImpl action = mock(BaseActionImpl.class);
        when(action.getID()). thenReturn(ACTION_ID);

        // when
        target.addAction(action);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
        verifyZeroInteractions(action);
    }

    @Test
    public void noIntValueIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        int intValue = 42;

        // when
        target.reportValue(ACTION_ID, "intValue", intValue);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noDoubleValueIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        double doubleValue = Math.E;

        // when
        target.reportValue(ACTION_ID, "doubleValue", doubleValue);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noStringValueIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        String stringValue = "Write once, debug everywhere";

        // when
        target.reportValue(ACTION_ID, "doubleValue", stringValue);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noEventIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        // when
        target.reportEvent(ACTION_ID, "Event name");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noErrorIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        // when
        target.reportError(ACTION_ID, "Error name", 123, "The reason for this error");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noCrashIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        // when
        target.reportCrash("Error name", "The reason for this error", "the stack trace");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noWebRequestIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));
        WebRequestTracerBaseImpl webRequestTracer = mock(WebRequestTracerBaseImpl.class);

        // when
        target.addWebRequest(ACTION_ID, webRequestTracer);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
        verifyZeroInteractions(webRequestTracer);
    }

    @Test
    public void noUserIdentificationIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        // when
        target.identifyUser("jane.doe@acme.com");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void noWebRequestIsReportedForDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        WebRequestTracerURLConnection mockWebRequestTracer = mock(WebRequestTracerURLConnection.class);
        //when
        target.addWebRequest(ACTION_ID, mockWebRequestTracer);

        //then
        verifyZeroInteractions(mockWebRequestTracer);
        //verify nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void webRequestIsReportedForDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        WebRequestTracerURLConnection mockWebRequestTracer = mock(WebRequestTracerURLConnection.class);

        //when
        target.addWebRequest(ACTION_ID, mockWebRequestTracer);

        //then
        verify(mockWebRequestTracer, times(1)).getBytesReceived();
        verify(mockWebRequestTracer, times(1)).getBytesSent();
        verify(mockWebRequestTracer, times(1)).getResponseCode();
        //verify nothing has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void webRequestIsReportedForDataCollectionLevel2() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        WebRequestTracerURLConnection mockWebRequestTracer = mock(WebRequestTracerURLConnection.class);

        //when
        target.addWebRequest(ACTION_ID, mockWebRequestTracer);

        //then
        verify(mockWebRequestTracer, times(1)).getBytesReceived();
        verify(mockWebRequestTracer, times(1)).getBytesSent();
        verify(mockWebRequestTracer, times(1)).getResponseCode();
        //verify nothing has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void beaconReturnsEmptyTagOnDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        String returnedTag = target.createTag(ACTION_ID, 1);

        //then
        assertThat(returnedTag, isEmptyString());
    }

    @Test
    public void beaconReturnsValidTagOnDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        String returnedTag = target.createTag(ACTION_ID, 1);

        //then
        assertThat(returnedTag.isEmpty(), is(false));
    }

    @Test
    public void beaconReturnsValidTagOnDataCollectionLevel2() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        String returnedTag = target.createTag(ACTION_ID, 1);

        //then
        assertThat(returnedTag, not(isEmptyString()));
    }

    @Test
    public void cannotIdentifyUserOnDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.identifyUser("jane@doe.com");

        //then
        //verify nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void cannotIdentifyUserOnDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.identifyUser("jane@doe.com");

        //then
        //verify nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void canIdentifyUserOnDataCollectionLevel2() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.identifyUser("jane@doe.com");

        //then
        //verify user tag has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void deviceIDIsRandomizedOnDataCollectionLevel0() {
        //given
        when(configuration.getApplicationID()).thenReturn(APP_ID);
        when(configuration.getApplicationName()).thenReturn(APP_NAME);
        when(configuration.getApplicationVersion()).thenReturn("v1");
        Device testDevice = new Device("OS", "MAN", "MODEL");
        when(configuration.getDevice()).thenReturn(testDevice);
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));

        Random mockRandom = mock(Random.class);
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider, mockRandom);

        //when
        target.getDeviceID();

        // then verify that the device id is not taken from the configuration
        // this means it must have been generated randomly
        verify(configuration, times(0)).getDeviceID();
        verify(mockRandom, times(1)).nextLong();
    }

    @Test
    public void deviceIDIsRandomizedOnDataCollectionLevel1() {
        //given
        when(configuration.getApplicationID()).thenReturn(APP_ID);
        when(configuration.getApplicationName()).thenReturn(APP_NAME);
        when(configuration.getApplicationVersion()).thenReturn("v1");
        Device testDevice = new Device("OS", "MAN", "MODEL");
        when(configuration.getDevice()).thenReturn(testDevice);
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));

        Random mockRandom = mock(Random.class);
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider, mockRandom);

        //when
        target.getDeviceID();

        // then verify that the device id is not taken from the configuration
        // this means it must have been generated randomly
        verify(configuration, times(0)).getDeviceID();
        verify(mockRandom, times(1)).nextLong();
    }

    @Test
    public void givenDeviceIDIsUsedOnDataCollectionLevel2() {
        String TEST_DEVICE_ID = "1338";
        //given
        when(configuration.getApplicationVersion()).thenReturn("v1");
        Device testDevice = new Device("OS", "MAN", "MODEL");
        when(configuration.getDevice()).thenReturn(testDevice);
        when(configuration.getDeviceID()).thenReturn(TEST_DEVICE_ID);
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));

        Random mockRandom = mock(Random.class);
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider, mockRandom);

        //when
        String deviceID = target.getDeviceID();

        //then verify that device id is taken from configuration
        verify(configuration, times(1)).getDeviceID();
        verifyNoMoreInteractions(mockRandom);
        assertThat(deviceID, is(equalTo(TEST_DEVICE_ID)));
    }

    @Test
    public void randomDeviceIDCannotBeNegativeOnDataCollectionLevel0() {
        String TEST_DEVICE_ID = "1338";
        //given
        when(configuration.getApplicationVersion()).thenReturn("v1");
        Device testDevice = new Device("OS", "MAN", "MODEL");
        when(configuration.getDevice()).thenReturn(testDevice);
        when(configuration.getDeviceID()).thenReturn(TEST_DEVICE_ID);
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));

        Random mockRandom = mock(Random.class);
        when(mockRandom.nextLong()).thenReturn(-123456789L);
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider, mockRandom);

        //when
        long deviceID = Long.valueOf(target.getDeviceID());

        //then verify that the id is positive regardless of the data collection level
        verify(mockRandom, times(1)).nextLong();
        assertThat(deviceID, is(greaterThanOrEqualTo(0L)));
        assertThat(deviceID, is(equalTo(-123456789L & Long.MAX_VALUE)));
    }

    @Test
    public void randomDeviceIDCannotBeNegativeOnDataCollectionLevel1() {
        String TEST_DEVICE_ID = "1338";
        //given
        when(configuration.getApplicationVersion()).thenReturn("v1");
        Device testDevice = new Device("OS", "MAN", "MODEL");
        when(configuration.getDevice()).thenReturn(testDevice);
        when(configuration.getDeviceID()).thenReturn(TEST_DEVICE_ID);
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));

        Random mockRandom = mock(Random.class);
        when(mockRandom.nextLong()).thenReturn(-123456789L);
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider, mockRandom);

        //when
        long deviceID = Long.valueOf(target.getDeviceID());

        //then verify that the id is positive regardless of the data collection level
        verify(mockRandom, times(1)).nextLong();
        assertThat(deviceID, is(greaterThanOrEqualTo(0L)));
        assertThat(deviceID, is(equalTo(-123456789L & Long.MAX_VALUE)));
    }

    @Test
    public void deviceIDIsTruncatedTo250Characters() {
        // given
        StringBuilder deviceIDBuilder = new StringBuilder();

        // append 249 times the character 'a'
        for (int i = 0; i < Beacon.MAX_NAME_LEN - 1; i++) {
            deviceIDBuilder.append('a');
        }
        // append character 'b' and 'c'
        deviceIDBuilder.append('b').append('c');

        String deviceID = deviceIDBuilder.toString();

        when(configuration.getDeviceID()).thenReturn(deviceID);

        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        String obtained = target.getDeviceID();

        // then
        assertThat(obtained.length(), is(equalTo(Beacon.MAX_NAME_LEN)));
        assertThat(obtained, is(equalTo(deviceID.substring(0, deviceID.length() - 1))));
    }

    @Test
    public void sessionIDIsAlwaysValue1OnDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        int sessionNumber = target.getSessionNumber();

        //then
        assertThat(sessionNumber, is(equalTo(1)));
    }

    @Test
    public void sessionIDIsAlwaysValue1OnDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        int sessionNumber = target.getSessionNumber();

        //then
        assertThat(sessionNumber, is(equalTo(1)));
    }

    @Test
    public void sessionIDIsValueFromSessionIDProviderOnDataCollectionLevel2() {
        // given
        final int SESSION_ID = 1234;
        when(configuration.getApplicationVersion()).thenReturn("v1");
        Device testDevice = new Device("OS", "MAN", "MODEL");
        when(configuration.getDevice()).thenReturn(testDevice);
        when(configuration.createSessionNumber()).thenReturn(SESSION_ID);
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));

        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        int sessionNumber = target.getSessionNumber();

        //then
        assertThat(sessionNumber, is(equalTo(SESSION_ID)));
    }

    @Test
    public void reportCrashDoesNotReportOnCrashReportingLevel0() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.reportCrash("OutOfMemory exception", "insufficient memory", "stacktrace:123");

        //then
        verify(timingProvider, times(1)).provideTimestampInMilliseconds();
        assertThat(target.isEmpty(), is(equalTo(true)));
    }

    @Test
    public void reportCrashDoesNotReportOnCrashReportingLevel1() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OPT_OUT_CRASHES));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.reportCrash("OutOfMemory exception", "insufficient memory", "stacktrace:123");

        //then
        verify(timingProvider, times(1)).provideTimestampInMilliseconds();
        assertThat(target.isEmpty(), is(equalTo(true)));
    }

    @Test
    public void reportCrashDoesReportOnCrashReportingLevel2() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OPT_IN_CRASHES));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.reportCrash("OutOfMemory exception", "insufficient memory", "stacktrace:123");

        //then
        verify(timingProvider, times(2)).provideTimestampInMilliseconds();
        assertThat(target.isEmpty(), is(equalTo(false)));
    }

    @Test
    public void actionNotReportedForDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        BaseActionImpl action = mock(BaseActionImpl.class);
        when(action.getID()). thenReturn(ACTION_ID);

        //when
        target.addAction(action);

        //then
        //verify action has not been serialized
        verify(action, times(0)).getID();
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void actionReportedForDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        BaseActionImpl action = mock(BaseActionImpl.class);
        when(action.getID()). thenReturn(ACTION_ID);

        //when
        target.addAction(action);

        //then
        //verify action has been serialized
        verify(action, times(1)).getID();
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void actionReportedForDataCollectionLevel2() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        BaseActionImpl action = mock(BaseActionImpl.class);
        when(action.getID()). thenReturn(ACTION_ID);

        //when
        target.addAction(action);

        //then
        //verify action has been serialized
        verify(action, times(1)).getID();
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void sessionNotReportedForDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        SessionImpl mockSession = mock(SessionImpl.class);

        //when
        target.endSession(mockSession);

        //then
        //verify session has not been serialized
        verify(mockSession, times(0)).getEndTime();
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void sessionReportedForDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        SessionImpl mockSession = mock(SessionImpl.class);

        //when
        target.endSession(mockSession);

        //then
        //verify session has been serialized
        verify(mockSession, times(2)).getEndTime();
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void sessionReportedForDataCollectionLevel2() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        SessionImpl mockSession = mock(SessionImpl.class);

        //when
        target.endSession(mockSession);

        //then
        //verify session has been serialized
        verify(mockSession, times(2)).getEndTime();
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void errorNotReportedForDataCollectionLevel0() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.reportError(ACTION_ID, "DivByZeroError", 127, "out of math");

        //then
        //verify action has not been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void errorReportedForDataCollectionLevel1() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.reportError(ACTION_ID, "DivByZeroError", 127, "out of math");

        //then
        //verify action has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void errorReportedForDataCollectionLevel2() {
        //given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        //when
        target.reportError(ACTION_ID, "DivByZeroError", 127, "out of math");

        //then
        //verify action has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void IntValueIsNotReportedForDataCollectionLevel0() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", 123);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void IntValueIsNotReportedForDataCollectionLevel1() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", 123);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void IntValueIsReportedForDataCollectionLevel2() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", 123);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void DoubleValueIsNotReportedForDataCollectionLevel0() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", 2.71);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void DoubleValueIsNotReportedForDataCollectionLevel1() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", 2.71);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void DoubleValueIsReportedForDataCollectionLevel2() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", 2.71);

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void StringValueIsNotReportedForDataCollectionLevel0() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", "test data");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void StringValueIsNotReportedForDataCollectionLevel1() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", "test data");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void StringValueIsReportedForDataCollectionLevel2() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportValue(ACTION_ID, "test value", "test data");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void NamedEventIsNotReportedForDataCollectionLevel0() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportEvent(ACTION_ID, "test event");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void NamedEventIsNotReportedForDataCollectionLevel1() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportEvent(ACTION_ID, "test event");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }

    @Test
    public void NamedEventIsReportedForDataCollectionLevel2() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OFF));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.reportEvent(ACTION_ID, "test event");

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void sessionStartIsReported() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.startSession();

        // then ensure session start has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void sessionStartIsReportedForDataCollectionLevel0() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.OFF, CrashReportingLevel.OPT_IN_CRASHES));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.startSession();

        // then ensure session start has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void sessionStartIsReportedForDataCollectionLevel1() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.PERFORMANCE, CrashReportingLevel.OPT_IN_CRASHES));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.startSession();

        // then ensure session start has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void sessionStartIsReportedForDataCollectionLevel2() {
        // given
        when(configuration.getPrivacyConfiguration()).thenReturn(new PrivacyConfiguration(DataCollectionLevel.USER_BEHAVIOR, CrashReportingLevel.OPT_IN_CRASHES));
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);

        // when
        target.startSession();

        // then ensure session start has been serialized
        assertThat(target.isEmpty(), is(false));
    }

    @Test
    public void noSessionStartIsReportedIfBeaconConfigurationDisablesCapturing() {
        // given
        Beacon target = new Beacon(logger, new BeaconCacheImpl(logger), configuration, "127.0.0.1", threadIDProvider, timingProvider);
        target.setBeaconConfiguration(new BeaconConfiguration(0));

        // when
        target.startSession();

        // then ensure nothing has been serialized
        assertThat(target.isEmpty(), is(true));
    }
}
