// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.AccessLogComponent.AccessLogType;
import com.yahoo.vespa.model.container.component.AccessLogComponent.CompressionType;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.collections.CollectionUtil.firstMatching;
import static com.yahoo.config.model.builder.xml.XmlHelper.getOptionalAttribute;

import static com.yahoo.config.model.builder.xml.XmlHelper.nullIfEmpty;

/**
 * @author Tony Vaagenes
 */
public class AccessLogBuilder {
    /*
     * Valid values for the type attribute in services.xml
     * Must be kept in sync with containercluster.rnc:AccessLog
     */
    private enum AccessLogTypeLiteral {
        VESPA("vespa"),
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
        protected AccessLogComponent doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element spec) {
            return new AccessLogComponent(
                    (ContainerCluster<?>) ancestor,
                    accessLogType,
                    compressionType(spec, isHostedVespa),
                    fileNamePattern(spec),
                    rotationInterval(spec),
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

        private String rotationInterval(Element spec) {
            return nullIfEmpty(spec.getAttribute("rotationInterval"));
        }

        private String fileNamePattern(Element spec) {
            return nullIfEmpty(spec.getAttribute("fileNamePattern"));
        }

        private static CompressionType compressionType(Element spec, boolean isHostedVespa) {
            CompressionType fallback = isHostedVespa ? CompressionType.ZSTD : CompressionType.GZIP;
            return Optional.ofNullable(spec.getAttribute("compressionType"))
                    .filter(value -> !value.isBlank())
                    .map(value -> {
                        switch (value) {
                            case "gzip":
                                return CompressionType.GZIP;
                            case "zstd":
                                return CompressionType.ZSTD;
                            default:
                                throw new IllegalArgumentException("Unknown compression type: " + value);
                        }
                    })
                    .orElse(fallback);
        }
    }

    private static AccessLogType logTypeFor(AccessLogTypeLiteral typeLiteral) {
        switch (typeLiteral) {
            case DISABLED:
                return null;
            case VESPA:
                return AccessLogType.queryAccessLog;
            case JSON:
                return AccessLogType.jsonAccessLog;
            default:
                throw new InconsistentSchemaAndCodeError();
        }
    }

    public static Optional<AccessLogComponent> buildIfNotDisabled(DeployState deployState, ContainerCluster<?> cluster, Element accessLogSpec) {
        AccessLogTypeLiteral typeLiteral =
                getOptionalAttribute(accessLogSpec, "type").
                        map(AccessLogTypeLiteral::fromAttributeValue).
                        orElse(AccessLogTypeLiteral.JSON);
        AccessLogType logType = logTypeFor(typeLiteral);
        if (logType == null) {
            return Optional.empty();
        }
        boolean hosted = cluster.isHostedVespa();
        return Optional.of(new DomBuilder(logType, hosted).build(deployState, cluster, accessLogSpec));
    }
}
