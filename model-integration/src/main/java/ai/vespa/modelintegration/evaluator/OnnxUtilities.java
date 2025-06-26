// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import onnx.Onnx;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Misc tools for ONNX models.
 *
 * @author bjorncs
 */
public class OnnxUtilities {

    private OnnxUtilities() {}

    /**
     * @return set of relative file locations for the external data references in the provided ONNX model.
     */
    public static Set<Path> getExternalDataLocations(Path model) throws IOException {
        try (var in = new BufferedInputStream(Files.newInputStream(model))) {
            return getExternalDataLocations(Onnx.ModelProto.parseFrom(in));
        }
    }

    /**
     * @see #getExternalDataLocations(Path)
     */
    public static Set<Path> getExternalDataLocations(Onnx.ModelProto model) {
        var externalDataPaths = new HashSet<Path>();

        // Check initializers in the main graph
        addExternalDataPath(externalDataPaths, model.getGraph().getInitializerList());

        // Check tensors in nodes' attributes
        model.getGraph().getNodeList().stream()
                .flatMap(node -> node.getAttributeList().stream())
                .forEach(attr -> {
                    if (attr.getType() == Onnx.AttributeProto.AttributeType.TENSOR) {
                        addExternalDataPath(externalDataPaths, attr.getT());
                    } else if (attr.getType() == Onnx.AttributeProto.AttributeType.TENSORS) {
                        addExternalDataPath(externalDataPaths, attr.getTensorsList());
                    }
                });

        // Check training info if present
        for (var trainingInfo : model.getTrainingInfoList()) {
            addExternalDataPath(externalDataPaths, trainingInfo.getInitialization().getInitializerList());
            addExternalDataPath(externalDataPaths, trainingInfo.getAlgorithm().getInitializerList());
        }

        return Set.copyOf(externalDataPaths);
    }

    private static void addExternalDataPath(Set<Path> externalDataPaths, List<Onnx.TensorProto> tensors) {
        tensors.forEach(tensor -> addExternalDataPath(externalDataPaths, tensor));
    }

    private static void addExternalDataPath(Set<Path> externalDataPaths, Onnx.TensorProto tensor) {
        if (tensor.getDataLocation() == Onnx.TensorProto.DataLocation.EXTERNAL) {
            for (var entry : tensor.getExternalDataList()) {
                if ("location".equals(entry.getKey())) {
                    var rawPath = entry.getValue();
                    if (rawPath.contains(".."))
                        throw new IllegalArgumentException("External data path '" + rawPath + "' must not contain '..'");
                    externalDataPaths.add(Paths.get(rawPath));
                    return;
                }
            }
        }
    }
}
