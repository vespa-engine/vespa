package com.yahoo.vespa.model.admin.monitoring.builder.xml;

import com.yahoo.vespa.model.admin.monitoring.CloudWatch;
import com.yahoo.vespa.model.admin.monitoring.CloudWatch.HostedAuth;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import org.w3c.dom.Element;

import static com.yahoo.config.model.builder.xml.XmlHelper.getOptionalChildValue;

/**
 * @author gjoranv
 */
public class CloudWatchBuilder {

    private static final String REGION_ATTRIBUTE = "region";
    private static final String NAMESPACE_ATTRIBUTE = "namespace";
    private static final String ACCESS_KEY_ELEMENT = "access-key-name";
    private static final String SECRET_KEY_ELEMENT = "secret-key-name";
    private static final String PROFILE_ELEMENT = "profile";

    public static CloudWatch buildCloudWatch(Element cloudwatchElement, MetricsConsumer consumer) {
        CloudWatch cloudWatch = new CloudWatch(cloudwatchElement.getAttribute(REGION_ATTRIBUTE),
                                               cloudwatchElement.getAttribute(NAMESPACE_ATTRIBUTE),
                                               consumer);

        getOptionalChildValue(cloudwatchElement, PROFILE_ELEMENT).ifPresent(cloudWatch::setProfile);

        getOptionalChildValue(cloudwatchElement, ACCESS_KEY_ELEMENT)
                .ifPresent(accessKey -> cloudWatch.setHostedAuth(
                        new HostedAuth(accessKey,
                                       getOptionalChildValue(cloudwatchElement, SECRET_KEY_ELEMENT)
                                               .orElseThrow(() -> new IllegalArgumentException("Access key given without a secret key.")))));
        return cloudWatch;
    }

}
