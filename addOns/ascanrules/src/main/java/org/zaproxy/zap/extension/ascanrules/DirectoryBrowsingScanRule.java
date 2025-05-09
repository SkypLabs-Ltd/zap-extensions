/*
 *
 * Paros and its related class files.
 *
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 *
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2012/01/02 Separate param and attack
// ZAP: 2012/04/25 Added @Override annotation to all appropriate methods.
// ZAP: 2012/08/01 Removed the "(non-Javadoc)" comments.
// ZAP: 2012/12/28 Issue 447: Include the evidence in the attack field
// ZAP: 2013/03/03 Issue 546: Remove all template Javadoc comments
// ZAP: 2015/07/27 Issue 1618: Target Technology Not Honored
// ZAP: 2019/05/08 Normalise format/indentation.
// ZAP: 2020/07/24 Normalise scan rule class names.
package org.zaproxy.zap.extension.ascanrules;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.AbstractAppPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;
import org.zaproxy.zap.model.Tech;

public class DirectoryBrowsingScanRule extends AbstractAppPlugin
        implements CommonActiveScanRuleInfo {

    /** Prefix for internationalised messages used by this rule */
    private static final String MESSAGE_PREFIX = "ascanrules.directorybrowsing.";

    private static final Map<String, String> ALERT_TAGS;

    static {
        Map<String, String> alertTags =
                new HashMap<>(
                        CommonAlertTag.toMap(
                                CommonAlertTag.OWASP_2021_A01_BROKEN_AC,
                                CommonAlertTag.OWASP_2017_A05_BROKEN_AC));
        alertTags.put(PolicyTag.API.getTag(), "");
        alertTags.put(PolicyTag.QA_STD.getTag(), "");
        alertTags.put(PolicyTag.QA_FULL.getTag(), "");
        alertTags.put(PolicyTag.PENTEST.getTag(), "");
        ALERT_TAGS = Collections.unmodifiableMap(alertTags);
    }

    private static final Pattern patternIIS = Pattern.compile("Parent Directory", PATTERN_PARAM);
    private static final Pattern patternApache =
            Pattern.compile("\\bDirectory Listing\\b.*(Tomcat|Apache)", PATTERN_PARAM);

    // general match for directory
    private static final Pattern patternGeneralDir1 =
            Pattern.compile("\\bDirectory\\b", PATTERN_PARAM);
    private static final Pattern patternGeneralDir2 =
            Pattern.compile("[\\s<]+IMG\\s*=", PATTERN_PARAM);
    private static final Pattern patternGeneralParent =
            Pattern.compile("Parent directory", PATTERN_PARAM);

    @Override
    public int getId() {
        return 00000;
    }

    @Override
    public String getName() {
        return Constant.messages.getString(MESSAGE_PREFIX + "name");
    }

    @Override
    public String getDescription() {
        return Constant.messages.getString(MESSAGE_PREFIX + "desc");
    }

    @Override
    public int getCategory() {
        return Category.INFO_GATHER;
    }

    @Override
    public String getSolution() {
        return Constant.messages.getString(MESSAGE_PREFIX + "soln");
    }

    @Override
    public String getReference() {
        return Constant.messages.getString(MESSAGE_PREFIX + "refs");
    }

    private static void checkIfDirectory(HttpMessage msg) throws URIException {

        URI uri = msg.getRequestHeader().getURI();
        uri.setQuery(null);
        String sUri = uri.toString();
        if (!sUri.endsWith("/")) {
            sUri = sUri + "/";
        }
        msg.getRequestHeader().setURI(new URI(sUri, true));
    }

    @Override
    public void scan() {

        boolean result = false;
        HttpMessage msg = getNewMsg();
        int confidence = Alert.CONFIDENCE_MEDIUM;
        StringBuilder evidence = new StringBuilder();

        try {
            checkIfDirectory(msg);
            writeProgress(msg.getRequestHeader().getURI().toString());
            sendAndReceive(msg);

            if (!isPage200(msg)) {
                return;
            }

            if (inScope(Tech.IIS) && matchBodyPattern(msg, patternIIS, evidence)) {
                result = true;
            } else if ((inScope(Tech.Apache) || inScope(Tech.Tomcat))
                    && matchBodyPattern(msg, patternApache, evidence)) {
                result = true;
            } else if (matchBodyPattern(msg, patternGeneralParent, evidence)) {
                result = true;
                confidence = Alert.CONFIDENCE_LOW;
            } else if (matchBodyPattern(msg, patternGeneralDir1, evidence)) {
                // Dont append the second matching pattern to the evidence as they will be in
                // different places
                if (matchBodyPattern(msg, patternGeneralDir2, null)) {
                    result = true;
                    confidence = Alert.CONFIDENCE_LOW;
                }
            }

        } catch (IOException e) {
        }

        if (result) {
            createAlert(msg.getRequestHeader().getURI().toString(), confidence, evidence.toString())
                    .setMessage(msg)
                    .raise();
        }
    }

    private AlertBuilder createAlert(String attack, int confidence, String evidence) {
        return newAlert().setConfidence(confidence).setAttack(attack).setEvidence(evidence);
    }

    @Override
    public List<Alert> getExampleAlerts() {
        return List.of(
                createAlert(
                                "https://example.org/uploads/",
                                Alert.CONFIDENCE_MEDIUM,
                                "Parent Directory")
                        .build());
    }

    @Override
    public int getRisk() {
        return Alert.RISK_MEDIUM;
    }

    @Override
    public Map<String, String> getAlertTags() {
        return ALERT_TAGS;
    }

    @Override
    public int getCweId() {
        return 548;
    }

    @Override
    public int getWascId() {
        return 48;
    }
}
