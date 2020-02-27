package com.yahoo.vespa.model.admin.monitoring.builder.xml;

import com.yahoo.vespa.model.admin.monitoring.CloudWatch;
import com.yahoo.vespa.model.admin.monitoring.CloudWatch.HostedAuth;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import org.w3c.dom.Element;

import static com.yahoo.config.model.builder.xml.XmlHelper.getOptionalChild;

/**
 * @author gjoranv
 */
public class CloudWatchBuilder {

    private static final String REGION_ATTRIBUTE = "region";
    private static final String NAMESPACE_ATTRIBUTE = "namespace";
    private static final String CREDENTIALS_ELEMENT = "credentials";
    private static final String ACCESS_KEY_ATTRIBUTE = "access-key-name";
    private static final String SECRET_KEY_ATTRIBUTE = "secret-key-name";
    private static final String SHARED_CREDENTIALS_ELEMENT = "shared-credentials";
    private static final String PROFILE_ATTRIBUTE = "profile";

    public static CloudWatch buildCloudWatch(Element cloudwatchElement, MetricsConsumer consumer) {
        CloudWatch cloudWatch = new CloudWatch(cloudwatchElement.getAttribute(REGION_ATTRIBUTE),
                                               cloudwatchElement.getAttribute(NAMESPACE_ATTRIBUTE),
                                               consumer);

        getOptionalChild(cloudwatchElement, CREDENTIALS_ELEMENT)
                .ifPresent(elem -> cloudWatch.setHostedAuth(new HostedAuth(elem.getAttribute(ACCESS_KEY_ATTRIBUTE),
                                                                           elem.getAttribute(SECRET_KEY_ATTRIBUTE))));
        getOptionalChild(cloudwatchElement, SHARED_CREDENTIALS_ELEMENT)
                .ifPresent(elem -> cloudWatch.setProfile(elem.getAttribute(PROFILE_ATTRIBUTE)));

        return cloudWatch;
    }

}
