/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2019 The ZAP Development Team
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
package org.zaproxy.zap.extension.pscanrules;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.junit.jupiter.api.Test;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;

class InformationDisclosureReferrerScanRuleUnitTest
        extends PassiveScannerTest<InformationDisclosureReferrerScanRule> {

    private static final String URI = "http://example.com/";
    private static final String BODY = "Some text in the response, doesn't matter.\nLine 2\n";

    @Override
    protected InformationDisclosureReferrerScanRule createScanner() {
        return new InformationDisclosureReferrerScanRule();
    }

    protected HttpMessage createHttpMessageWithRespBody(String testReferer)
            throws HttpMalformedHeaderException, URIException {

        HttpRequestHeader requestHeader = new HttpRequestHeader();
        requestHeader.setURI(new URI(URI, false));
        requestHeader.setHeader("Referer", testReferer);

        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader(requestHeader);
        msg.setResponseBody(BODY);
        msg.setResponseHeader(
                "HTTP/1.1 200 OK\r\n"
                        + "Server: Apache-Coyote/1.1\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: "
                        + BODY.length()
                        + "\r\n");
        return msg;
    }

    @Override
    public void setUpZap() throws Exception {
        super.setUpZap();

        Path xmlDir =
                Files.createDirectories(
                        Paths.get(
                                Constant.getZapHome(),
                                InformationDisclosureReferrerScanRule
                                        .URL_SENSITIVE_INFORMATION_DIR));
        Path testFile =
                xmlDir.resolve(
                        InformationDisclosureReferrerScanRule.URL_SENSITIVE_INFORMATION_FILE);
        Files.write(testFile, Arrays.asList(" user", " Password ", "# notused", "session "));
    }

    @Test
    void shouldReturnExpectedMappings() {
        // Given / When
        Map<String, String> tags = rule.getAlertTags();
        // Then
        assertThat(tags.size(), is(equalTo(5)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2021_A01_BROKEN_AC.getTag()),
                is(equalTo(true)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2017_A03_DATA_EXPOSED.getTag()),
                is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.PENTEST.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.DEV_STD.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.QA_STD.getTag()), is(equalTo(true)));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2021_A01_BROKEN_AC.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2021_A01_BROKEN_AC.getValue())));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2017_A03_DATA_EXPOSED.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2017_A03_DATA_EXPOSED.getValue())));
    }

    @Test
    void shouldHaveExpectedExampleAlert() {
        // Given / When
        List<Alert> alerts = rule.getExampleAlerts();
        // Then
        assertThat(alerts.size(), is(equalTo(1)));
        Alert alert = alerts.get(0);
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_INFO)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        assertThat(alert.getCweId(), is(equalTo(598)));
    }

    @Test
    @Override
    public void shouldHaveValidReferences() {
        super.shouldHaveValidReferences();
    }

    @Test
    void noAlertOnRefererSelfReferenceWithSensitiveInfo()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String testReferer = URI + "?password=whatsup&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }

    @Test
    void noAlertOnNoRefererHeaderWithSensitiveInfoInUrl()
            throws HttpMalformedHeaderException, URIException {

        // Given
        HttpRequestHeader requestHeader = new HttpRequestHeader();
        requestHeader.setURI(new URI(URI + "?password=whatsup&hl=en", false));

        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader(requestHeader);
        msg.setResponseBody(BODY);
        msg.setResponseHeader(
                "HTTP/1.1 200 OK\r\n"
                        + "Server: Apache-Coyote/1.1\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: "
                        + BODY.length()
                        + "\r\n");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }

    @Test
    void noAlertWithUrlAbsolutePathInRefererWithSensitiveInfo()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String testReferer = "/my-page?passWord=whatsup&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }

    @Test
    void alertWhenMalformedRefererContainsSensitiveInfo()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String sensitiveParamName = "passWord";
        String sensitiveValue = "whatsup";
        String testReferer =
                "http://%Jexample.org/?" + sensitiveParamName + "=" + sensitiveValue + "&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(1, alertsRaised.size());
        assertEquals(sensitiveParamName, alertsRaised.get(0).getEvidence());
        assertEquals(
                Constant.messages.getString(
                        InformationDisclosureReferrerScanRule.MESSAGE_PREFIX
                                + "otherinfo.sensitiveinfo"),
                alertsRaised.get(0).getOtherInfo());
    }

    @Test
    void alertWhenNoHostInRefererContainsSensitiveInfo()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String sensitiveParamName = "passWord";
        String sensitiveValue = "whatsup";
        String testReferer =
                "http:example/bar?" + sensitiveParamName + "=" + sensitiveValue + "&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(1, alertsRaised.size());
        assertEquals(sensitiveParamName, alertsRaised.get(0).getEvidence());
        assertEquals(
                Constant.messages.getString(
                        InformationDisclosureReferrerScanRule.MESSAGE_PREFIX
                                + "otherinfo.sensitiveinfo"),
                alertsRaised.get(0).getOtherInfo());
    }

    @Test
    void shouldRaiseAlertWhenSensitiveInfoInReferer()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String sensitiveParamName = "passWord";
        String testReferer = "http://example.org/?passWord=whatsup&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(1, alertsRaised.size());
        assertEquals(sensitiveParamName, alertsRaised.get(0).getEvidence());
        assertEquals(
                Constant.messages.getString(
                        InformationDisclosureReferrerScanRule.MESSAGE_PREFIX
                                + "otherinfo.sensitiveinfo"),
                alertsRaised.get(0).getOtherInfo());
    }

    @Test
    void shouldNotAlertWithNoSensitiveInfoInReferer()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String testReferer = "http://example.org/?words=whatsup&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }

    @Test
    void shouldRaiseAlertWhenCreditCardInReferer()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String sensitiveParamName = "docid";
        String sensitiveValue = "6011000990139424";
        String testReferer =
                "http://example.org/?" + sensitiveParamName + "=" + sensitiveValue + "&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(1, alertsRaised.size());
        assertEquals(sensitiveValue, alertsRaised.get(0).getEvidence());
        assertEquals(
                Constant.messages.getString(
                                InformationDisclosureReferrerScanRule.MESSAGE_PREFIX
                                        + "otherinfo.cc")
                        + '\n'
                        + "Bank Identification Number: 601100"
                        + '\n'
                        + "Brand: DISCOVER"
                        + '\n'
                        + "Category: PLATINUM"
                        + '\n'
                        + "Issuer: DISCOVER",
                alertsRaised.get(0).getOtherInfo());
    }

    @Test
    void shouldNotAlertWithNoCreditCardInReferer()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String testReferer = "http://example.org/?docid=applepie&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }

    @Test
    void shouldRaiseAlertWhenEmailAddressInReferer()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String sensitiveParamName = "docid";
        String sensitiveValue = "example@gmail.com";
        String testReferer =
                "http://example.org/?" + sensitiveParamName + "=" + sensitiveValue + "&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(1, alertsRaised.size());
        assertEquals(sensitiveValue, alertsRaised.get(0).getEvidence());
        assertEquals(
                Constant.messages.getString(
                        InformationDisclosureReferrerScanRule.MESSAGE_PREFIX + "otherinfo.email"),
                alertsRaised.get(0).getOtherInfo());
    }

    @Test
    void shouldNotAlertWithNoEmailAddressInReferer()
            throws HttpMalformedHeaderException, URIException {

        // Given
        String testReferer = "http://example.org/?docid=examplegmail.com&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }

    @Test
    void shouldRaiseAlertWhenSsnInReferer() throws HttpMalformedHeaderException, URIException {

        // Given
        String sensitiveParamName = "docid";
        String sensitiveValue = "000-00-0000";
        String testReferer =
                "http://example.org/?" + sensitiveParamName + "=" + sensitiveValue + "&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(1, alertsRaised.size());
        assertEquals(sensitiveValue, alertsRaised.get(0).getEvidence());
        assertEquals(
                Constant.messages.getString(
                        InformationDisclosureReferrerScanRule.MESSAGE_PREFIX + "otherinfo.ssn"),
                alertsRaised.get(0).getOtherInfo());
    }

    @Test
    void shouldNotAlertWithNoSsnInReferer() throws HttpMalformedHeaderException, URIException {

        // Given
        String testReferer = "http://example.org/?docid=ssn-no-here&hl=en";
        HttpMessage msg = createHttpMessageWithRespBody(testReferer);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertEquals(0, alertsRaised.size());
    }
}
