// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import ai.vespa.triton.TritonConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import org.w3c.dom.Element;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;

/**
 * Configuration builder for Triton ONNX runtime component.
 * Parses XML configuration from services.xml and produces TritonConfig.
 *
 * @author glebashnik
 */
public class TritonOnnxRuntime extends SimpleComponent implements TritonConfig.Producer {
    private static final String CLASS = "ai.vespa.triton.TritonOnnxRuntime";
    private static final String BUNDLE = INTEGRATION_BUNDLE_NAME;

    private final String grpcEndpoint;
    private final String modelRepository;
    private final String modelControlMode;

    public TritonOnnxRuntime(Element xml) {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
        
        grpcEndpoint = xml != null ? getChildValue(xml, "grpcEndpoint").orElse(null) : null;
        modelRepository = xml != null ? getChildValue(xml, "modelRepository").orElse(null) : null;
        modelControlMode = xml != null ? getChildValue(xml, "modelControlMode").orElse(null) : null;
    }

    public void getConfig(TritonConfig.Builder builder) {
        if (grpcEndpoint != null) {
            builder.grpcEndpoint(grpcEndpoint);
        }
        if (modelRepository != null) {
            builder.modelRepository(modelRepository);
        }
        if (modelControlMode != null) {
            builder.modelControlMode(
                    TritonConfig.ModelControlMode.Enum.valueOf(modelControlMode.toUpperCase()));
        }
    }
}
