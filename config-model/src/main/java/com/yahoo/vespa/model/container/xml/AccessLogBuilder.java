// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.AccessLogComponent.AccessLogType;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.collections.CollectionUtil.firstMatching;
import static com.yahoo.config.model.builder.xml.XmlHelper.getOptionalAttribute;

import static com.yahoo.config.model.builder.xml.XmlHelper.nullIfEmpty;

/**
 * @author tonytv
 */
public class AccessLogBuilder {
    /*
     * Valid values for the type attribute in services.xml
     * Must be kept in sync with containercluster.rnc:AccessLog
     */
    private enum AccessLogTypeLiteral {
        VESPA("vespa"),
        YAPACHE("yapache"),
        JSON("json"),
        DISABLED("disabled");

        final String attributeValue;

        AccessLogTypeLiteral(String attributeValue) {
            this.attributeValue = attributeValue;
        }

        static AccessLogTypeLiteral fromAttributeValue(String value) {
            return firstMatching(
                    AccessLogTypeLiteral.values(),
                    typeLiteral -> typeLiteral.attributeValue.equals(value)).get();
        }
    }

    private static class DomBuilder extends VespaDomBuilder.DomConfigProducerBuilder<AccessLogComponent> {
        private final AccessLogType accessLogType;
        private final boolean isHostedVespa;

        public DomBuilder(AccessLogType accessLogType, boolean isHostedVespa) {
            this.accessLogType = accessLogType;
            this.isHostedVespa = isHostedVespa;
        }

        @Override
        protected AccessLogComponent doBuild(AbstractConfigProducer ancestor, Element spec) {
            return new AccessLogComponent(
                    accessLogType,
                    fileNamePattern(spec),
                    rotationInterval(spec),
                    rotationScheme(spec),
                    compressOnRotation(spec),
                    isHostedVespa,
                    symlinkName(spec));
        }

        private String symlinkName(Element spec) {
            return nullIfEmpty(spec.getAttribute("symlinkName"));
        }

        private Boolean compressOnRotation(Element spec) {
            String compress = spec.getAttribute("compressOnRotation");
            return (compress.isEmpty() ? null : Boolean.parseBoolean(compress));
        }

        private AccessLogConfig.FileHandler.RotateScheme.Enum rotationScheme(Element spec) {
            return AccessLogComponent.rotateScheme(nullIfEmpty(spec.getAttribute("rotationScheme")));
        }

        private String rotationInterval(Element spec) {
            return nullIfEmpty(spec.getAttribute("rotationInterval"));
        }

        private String fileNamePattern(Element spec) {
            return nullIfEmpty(spec.getAttribute("fileNamePattern"));
        }
    }

    private static AccessLogType logTypeFor(AccessLogTypeLiteral typeLiteral) {
        switch (typeLiteral) {
            case DISABLED:
                return null;
            case VESPA:
                return AccessLogType.queryAccessLog;
            case YAPACHE:
                return AccessLogType.yApacheAccessLog;
            case JSON:
                return AccessLogType.jsonAccessLog;
            default:
                throw new InconsistentSchemaAndCodeError();
        }
    }

    public static Optional<AccessLogComponent> buildIfNotDisabled(ContainerCluster cluster, Element accessLogSpec) {
        AccessLogTypeLiteral typeLiteral =
                getOptionalAttribute(accessLogSpec, "type").
                        map(AccessLogTypeLiteral::fromAttributeValue).
                        orElse(AccessLogTypeLiteral.VESPA);
        AccessLogType logType = logTypeFor(typeLiteral);
        if (logType == null) {
            return Optional.empty();
        }
        boolean hosted = cluster.isHostedVespa();
        return Optional.of(new DomBuilder(logType, hosted).build(cluster, accessLogSpec));
    }
}
