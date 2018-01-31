// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 *
 * @author bratseth
 */
public class TensorFlowImporter {

    /**
     * Imports a saved TensorFlow model from a directory.
     * The model should be saved as a .pbtxt or .pb file.
     * The name of the model is taken as the db/pbtxt file name (not including the file ending).
     *
     * @param modelDir the directory containing the TensorFlow model files to import
     */
    public TensorFlowModel importModel(String modelDir) {
        try (SavedModelBundle model = SavedModelBundle.load(modelDir, "serve")) {
            return importModel(model);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model from directory '" + modelDir + "'", e);
        }
    }

    public TensorFlowModel importModel(File modelDir) {
        return importModel(modelDir.toString());
    }

    /** Imports a TensorFlow model */
    public TensorFlowModel importModel(SavedModelBundle model) {
        try {
            return importGraph(MetaGraphDef.parseFrom(model.metaGraphDef()), model);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model '" + model + "'", e);
        }
    }

    private TensorFlowModel importGraph(MetaGraphDef graph, SavedModelBundle model) {
        TensorFlowModel result = new TensorFlowModel();
        for (Map.Entry<String, SignatureDef> signatureEntry : graph.getSignatureDefMap().entrySet()) {
            TensorFlowModel.Signature signature = result.signature(signatureEntry.getKey()); // Prefer key over "methodName"

            importInputs(signatureEntry.getValue().getInputsMap(), signature);
            for (Map.Entry<String, TensorInfo> output : signatureEntry.getValue().getOutputsMap().entrySet()) {
                String outputName = output.getKey();
                try {
                    NodeDef node = getNode(namePartOf(output.getValue().getName()), graph.getGraphDef());
                    Parameters params = createParameters(graph.getGraphDef(), model, result, signature, node, "");

                    // Commonly, there are multiple paths through a TensorFlow graph, for instance for
                    // training and testing/evaluation. Examples are dropout and batch norm. For Vespa
                    // we are not concerned with training paths, so we can ignore non-supported operations
                    // as long as they are on a path that will not be evaluated run time. Operations
                    // that fail import will not have a value present in the optionals. However, the
                    // final output node must have value present. It is an error if it does not.

                    Optional<TypedTensorFunction> outputFunction = importNode(params);
                    if (!outputFunction.isPresent()) {
                        throw new IllegalArgumentException(signature.importWarnings().stream().collect(Collectors.joining("\n")));
                    }
                    signature.output(outputName, namePartOf(output.getValue().getName()));
                }
                catch (IllegalArgumentException e) {
                    signature.skippedOutput(outputName, Exceptions.toMessageString(e));
                }
            }
        }
        return result;
    }

    private void importInputs(Map<String, TensorInfo> inputInfoMap, TensorFlowModel.Signature signature) {
        inputInfoMap.forEach((key, value) -> {
            String argumentName = namePartOf(value.getName());
            TensorType argumentType = AttrValueConverter.toVespaTensorType(value.getTensorShape());
            // Arguments are (Placeholder) nodes, so not local to the signature:
            signature.owner().argument(argumentName, argumentType);
            signature.input(key, argumentName);
        });
    }

    /** Recursively convert a graph of TensorFlow nodes into a Vespa tensor function expression tree */
    private Optional<TypedTensorFunction> importNode(Parameters params) {
        String nodeName = params.node().getName();
        if (params.imported().containsKey(nodeName)) {
            return Optional.of(params.imported().get(nodeName));
        }

        Optional<TypedTensorFunction> function = OperationMapper.map(params);
        if ( ! function.isPresent()) {
            return Optional.empty();
        }
        if ( ! controlDependenciesArePresent(params)) {
            return Optional.empty();
        }
        params.imported().put(nodeName, function.get());

        try {
            // We add all intermediate nodes imported as separate expressions. Only those referenced in a signature output
            // will be used. We parse the TensorFunction here to convert it to a RankingExpression tree
            params.result().expression(nodeName,
                    new RankingExpression(params.node().getName(), function.get().function().toString()));
            return function;
        }
        catch (ParseException e) {
            throw new RuntimeException("Tensorflow function " + function.get().function() +
                                       " cannot be parsed as a ranking expression", e);
        }
    }

    private boolean controlDependenciesArePresent(Parameters params) {
        return params.node().getInputList().stream()
                .filter(TensorFlowImporter::isControlDependency)
                .map(nodeName -> importNode(params.copy(getNode(namePartOf(nodeName), params.graph()), indexPartOf(nodeName))))
                .allMatch(Optional::isPresent);
    }

    private static boolean isControlDependency(String nodeName) {
        return nodeName.startsWith("^");
    }

    private List<Optional<TypedTensorFunction>> importArguments(Parameters params) {
        return params.node().getInputList().stream()
                .filter(nodeName -> !isControlDependency(nodeName))
                .map(nodeName -> importNode(params.copy(getNode(namePartOf(nodeName), params.graph()), indexPartOf(nodeName))))
                .collect(Collectors.toList());
    }

    private NodeDef getNode(String name, GraphDef graph) {
        return graph.getNodeList().stream()
                                  .filter(node -> node.getName().equals(name))
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalArgumentException("Could not find node '" + name + "'"));
    }

    /**
     * A method signature input and output has the form name:index.
     * This returns the name part without the index.
     */
    private static String namePartOf(String name) {
        name = name.startsWith("^") ? name.substring(1) : name;
        return name.split(":")[0];
    }

    /**
     * This return the index part. Indexes are used for nodes with
     * multiple outputs.
     */
    private static String indexPartOf(String name) {
        int i = name.indexOf(":");
        return i < 0 ? "" : name.substring(i + 1);
    }


    private Parameters createParameters(GraphDef graph,
                                        SavedModelBundle model,
                                        TensorFlowModel result,
                                        TensorFlowModel.Signature signature,
                                        NodeDef node,
                                        String port) {
        return new Parameters(this, graph, model, result, signature, new HashMap<>(), node, port);
    }

    /** Parameter object to hold important data while importing */
    static final class Parameters {

        private final TensorFlowImporter owner;
        private final GraphDef graph;
        private final SavedModelBundle model;
        private final TensorFlowModel result;
        private final TensorFlowModel.Signature signature;
        private final Map<String, TypedTensorFunction> imported;
        private final NodeDef node;
        private final String port;

        private Parameters(TensorFlowImporter owner,
                           GraphDef graph,
                           SavedModelBundle model,
                           TensorFlowModel result,
                           TensorFlowModel.Signature signature,
                           Map<String, TypedTensorFunction> imported,
                           NodeDef node,
                           String port) {
            this.owner = owner;
            this.graph = graph;
            this.model = model;
            this.result = result;
            this.signature = signature;
            this.imported = imported;
            this.node = node;
            this.port = port;
        }

        GraphDef graph() {
            return this.graph;
        }

        SavedModelBundle model() {
            return this.model;
        }

        TensorFlowModel result() {
            return this.result;
        }

        TensorFlowModel.Signature signature() {
            return this.signature;
        }

        Map<String, TypedTensorFunction> imported() {
            return this.imported;
        }

        NodeDef node() {
            return node;
        }

        String port() {
            return port;
        }

        Parameters copy(NodeDef node, String port) {
            return new Parameters(this.owner, this.graph, this.model, this.result, this.signature, this.imported, node, port);
        }

        List<Optional<TypedTensorFunction>> inputs() {
            return owner.importArguments(this);
        }
    }

}
