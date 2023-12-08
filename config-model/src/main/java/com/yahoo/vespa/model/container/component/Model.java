// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.xml.ModelIdResolver;
import org.w3c.dom.Element;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a model, e.g ONNX model for an embedder.
 *
 * @author bjorncs
 */
class Model {
    private final String paramName;
    private final String modelId;
    private final URI url;
    private final ApplicationFile file;
    private final ModelReference ref;

    private Model(DeployState ds, String paramName, String modelId, URI url, Path file) {
        this.paramName = Objects.requireNonNull(paramName);
        if (modelId == null && url == null && file == null)
            throw new IllegalArgumentException("At least one of 'model-id', 'url' or 'path' must be specified");
        this.modelId = modelId;
        this.url = url;
        this.file = file != null ? ds.getApplicationPackage().getFile(file) : null;
        this.ref = ModelIdResolver.resolveToModelReference(
                paramName, Optional.ofNullable(modelId), Optional.ofNullable(url).map(URI::toString),
                Optional.ofNullable(file).map(Path::toString), ds);
    }

    static Model fromParams(DeployState ds, String paramName, String modelId, URI url, Path file) {
        return new Model(ds, paramName, modelId, url, file);
    }

    static Optional<Model> fromXml(DeployState ds, Element parent, String name) {
        return XmlHelper.getOptionalChild(parent, name).map(e -> fromXml(ds, e));
    }

    static Model fromXml(DeployState ds, Element model) {
        var modelId = XmlHelper.getOptionalAttribute(model, "model-id").orElse(null);
        var url = XmlHelper.getOptionalAttribute(model, "url").map(URI::create).orElse(null);
        var path = XmlHelper.getOptionalAttribute(model, "path").map(Path::fromString).orElse(null);
        return new Model(ds, model.getTagName(), modelId, url, path);
    }

    void registerOnnxModelCost(ApplicationContainerCluster c, OnnxModelOptions onnxModelOptions) {
        var resolvedUrl = resolvedUrl().orElse(null);
        if (file != null) c.onnxModelCostCalculator().registerModel(file, onnxModelOptions);
        else if (resolvedUrl != null) c.onnxModelCostCalculator().registerModel(resolvedUrl, onnxModelOptions);
    }

    String name() { return paramName; }
    Optional<String> modelId() { return Optional.ofNullable(modelId); }
    Optional<URI> url() { return Optional.ofNullable(url); }
    Optional<URI> resolvedUrl() { return ref.url().map(u -> URI.create(u.value())); }
    Optional<ApplicationFile> file() { return Optional.ofNullable(file); }
    ModelReference modelReference() { return ref; }
}
