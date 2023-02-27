// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.collections.Pair;
import com.yahoo.config.FileReference;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import net.jpountz.lz4.LZ4FrameInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts RankProfilesConfig instances to RankingExpressions for evaluation.
 * This class can be used by a single thread only.
 *
 * @author bratseth
 */
public class RankProfilesConfigImporter {

    private final FileAcquirer fileAcquirer;
    private final OnnxRuntime onnx;

    public RankProfilesConfigImporter(FileAcquirer fileAcquirer, OnnxRuntime onnx) {
        this.fileAcquirer = fileAcquirer;
        this.onnx = onnx;
    }

    /**
     * Returns a map of the models contained in this config, indexed on name.
     * The map is modifiable and owned by the caller.
     */
    public Map<String, Model> importFrom(RankProfilesConfig config,
                                         RankingConstantsConfig constantsConfig,
                                         RankingExpressionsConfig expressionsConfig,
                                         OnnxModelsConfig onnxModelsConfig) {
        try {
            Map<String, Model> models = new TreeMap<>();
            for (RankProfilesConfig.Rankprofile profile : config.rankprofile()) {
                Model model = importProfile(profile, constantsConfig, expressionsConfig, onnxModelsConfig);
                models.put(model.name(), model);
            }
            return models;
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Could not read rank profiles config - version mismatch?", e);
        }
    }

    private Model importProfile(RankProfilesConfig.Rankprofile profile,
                                RankingConstantsConfig constantsConfig,
                                RankingExpressionsConfig expressionsConfig,
                                OnnxModelsConfig onnxModelsConfig)
            throws ParseException {

        List<OnnxModel> onnxModels = readOnnxModelsConfig(onnxModelsConfig);
        List<Constant> constants = readLargeConstants(constantsConfig);
        Map<String, RankingExpression> largeExpressions = readLargeExpressions(expressionsConfig);

        Map<FunctionReference, ExpressionFunction> functions = new LinkedHashMap<>();
        Map<FunctionReference, ExpressionFunction> referencedFunctions = new LinkedHashMap<>();
        SmallConstantsInfo smallConstantsInfo = new SmallConstantsInfo();
        ExpressionFunction firstPhase = null;
        ExpressionFunction secondPhase = null;
        ExpressionFunction globalPhase = null;
        Map<String, TensorType> declaredTypes = new LinkedHashMap<>();
        for (RankProfilesConfig.Rankprofile.Fef.Property property : profile.fef().property()) {
            Optional<FunctionReference> reference = FunctionReference.fromSerial(property.name());
            Optional<FunctionReference> externalReference = FunctionReference.fromExternalSerial(property.name());
            Optional<Pair<FunctionReference, String>> argumentType = FunctionReference.fromTypeArgumentSerial(property.name());
            Optional<FunctionReference> returnType = FunctionReference.fromReturnTypeSerial(property.name());
            Optional<String> typeDeclaredFeature = fromTypeDeclarationSerial(property.name());
            if (externalReference.isPresent()) {
                RankingExpression expression = largeExpressions.get(property.value());
                ExpressionFunction function = new ExpressionFunction(externalReference.get().functionName(),
                        Collections.emptyList(),
                        expression);

                if (externalReference.get().isFree()) // make available in model under configured name
                    functions.put(externalReference.get(), function);
                // Make all functions, bound or not, available under the name they are referenced by in expressions
                referencedFunctions.put(externalReference.get(), function);
            }
            else if (reference.isPresent()) {
                RankingExpression expression = new RankingExpression(reference.get().functionName(), property.value());
                ExpressionFunction function = new ExpressionFunction(reference.get().functionName(),
                                                                     Collections.emptyList(),
                                                                     expression);

                if (reference.get().isFree()) // make available in model under configured name
                    functions.put(reference.get(), function);
                // Make all functions, bound or not, available under the name they are referenced by in expressions
                referencedFunctions.put(reference.get(), function);
            }
            else if (argumentType.isPresent()) { // Arguments always follows the function in properties
                FunctionReference argReference = argumentType.get().getFirst();
                ExpressionFunction function = referencedFunctions.get(argReference);
                function = function.withArgument(argumentType.get().getSecond(), TensorType.fromSpec(property.value()));
                if (argReference.isFree())
                    functions.put(argReference, function);
                referencedFunctions.put(argReference, function);
            }
            else if (returnType.isPresent()) { // Return type always follows the function in properties
                ExpressionFunction function = referencedFunctions.get(returnType.get());
                function = function.withReturnType(TensorType.fromSpec(property.value()));
                if (returnType.get().isFree())
                    functions.put(returnType.get(), function);
                referencedFunctions.put(returnType.get(), function);
            }
            else if (property.name().equals("vespa.rank.firstphase")) { // Include in addition to functions
                firstPhase = new ExpressionFunction("firstphase", new ArrayList<>(),
                                                    new RankingExpression("first-phase", property.value()));
            }
            else if (property.name().equals("vespa.rank.secondphase")) { // Include in addition to functions
                secondPhase = new ExpressionFunction("secondphase", new ArrayList<>(),
                                                     new RankingExpression("second-phase", property.value()));
            }
            else if (property.name().equals("vespa.rank.globalphase")) { // Include in addition to functions
                globalPhase = new ExpressionFunction("globalphase", new ArrayList<>(),
                                                     new RankingExpression("global-phase", property.value()));
            }
            else if (typeDeclaredFeature.isPresent()) {
                declaredTypes.put(typeDeclaredFeature.get(), TensorType.fromSpec(property.value()));
            }
            else {
                smallConstantsInfo.addIfSmallConstantInfo(property.name(), property.value());
            }
        }
        if (functionByName("firstphase", functions.values()) == null && firstPhase != null) // may be already included, depending on body
            functions.put(FunctionReference.fromName("firstphase"), firstPhase);
        if (functionByName("secondphase", functions.values()) == null && secondPhase != null) // may be already included, depending on body
            functions.put(FunctionReference.fromName("secondphase"), secondPhase);
        if (functionByName("globalphase", functions.values()) == null && globalPhase != null) // may be already included, depending on body
            functions.put(FunctionReference.fromName("globalphase"), globalPhase);

        constants.addAll(smallConstantsInfo.asConstants());

        try {
            return new Model(profile.name(), functions, referencedFunctions, declaredTypes, constants, onnxModels);
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not load model '" + profile.name() + "'", e);
        }
    }

    private ExpressionFunction functionByName(String name, Collection<ExpressionFunction> functions) {
        for (ExpressionFunction function : functions)
            if (function.getName().equals(name))
                return function;
        return null;
    }

    private List<OnnxModel> readOnnxModelsConfig(OnnxModelsConfig onnxModelsConfig) {
        List<OnnxModel> onnxModels = new ArrayList<>();
        if (onnxModelsConfig != null) {
            for (OnnxModelsConfig.Model onnxModelConfig : onnxModelsConfig.model()) {
                onnxModels.add(readOnnxModelConfig(onnxModelConfig));
            }
        }
        return onnxModels;
    }

    private OnnxModel readOnnxModelConfig(OnnxModelsConfig.Model onnxModelConfig) {
        try {
            String name = onnxModelConfig.name();
            File file = fileAcquirer.waitFor(onnxModelConfig.fileref(), 7, TimeUnit.DAYS);

            OnnxEvaluatorOptions options = new OnnxEvaluatorOptions();
            options.setExecutionMode(onnxModelConfig.stateless_execution_mode());
            options.setInterOpThreads(onnxModelConfig.stateless_interop_threads());
            options.setIntraOpThreads(onnxModelConfig.stateless_intraop_threads());
            options.setGpuDevice(onnxModelConfig.gpu_device(), onnxModelConfig.gpu_device_required());
            var m =  new OnnxModel(name, file, options, onnx);
            for (var spec : onnxModelConfig.input()) {
                m.addInputMapping(spec.name(), spec.source());
            }
            for (var spec : onnxModelConfig.output()) {
                m.addOutputMapping(spec.name(), spec.as());
            }
            return m;
        } catch (InterruptedException e) {
            throw new IllegalStateException("Gave up waiting for ONNX model " + onnxModelConfig.name());
        }
    }

    private List<Constant> readLargeConstants(RankingConstantsConfig constantsConfig) {
        List<Constant> constants = new ArrayList<>();

        for (RankingConstantsConfig.Constant constantConfig : constantsConfig.constant()) {
            constants.add(new Constant(constantConfig.name(),
                                       readTensorFromFile(constantConfig.name(),
                                                          TensorType.fromSpec(constantConfig.type()),
                                                          constantConfig.fileref())));
        }
        return constants;
    }

    private Map<String, RankingExpression> readLargeExpressions(RankingExpressionsConfig expressionsConfig) throws ParseException {
        Map<String, RankingExpression> expressions = new HashMap<>();

        for (RankingExpressionsConfig.Expression expression : expressionsConfig.expression()) {
            expressions.put(expression.name(), readExpressionFromFile(expression.name(), expression.fileref()));
        }
        return expressions;
    }

    protected final String readExpressionFromFile(File file) throws IOException {
        return (file.getName().endsWith(".lz4"))
            ? Utf8.toString(IOUtils.readBytes(new LZ4FrameInputStream(new FileInputStream(file)), 65536))
            : Utf8.toString(IOUtils.readFileBytes(file));
    }

    protected RankingExpression readExpressionFromFile(String name, FileReference fileReference) throws ParseException {
        try {
            File file = fileAcquirer.waitFor(fileReference, 7, TimeUnit.DAYS);
            return new RankingExpression(name, readExpressionFromFile(file));
        }
        catch (InterruptedException e) {
            throw new IllegalStateException("Gave up waiting for expression " + name);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected Tensor readTensorFromFile(String name, TensorType type, FileReference fileReference) {
        try {
            File file = fileAcquirer.waitFor(fileReference, 7, TimeUnit.DAYS);
            if (file.getName().endsWith(".tbf")) {
                return TypedBinaryFormat.decode(Optional.of(type),
                                                GrowableByteBuffer.wrap(IOUtils.readFileBytes(file)));
            } else if (file.getName().endsWith(".json")) {
                return com.yahoo.tensor.serialization.JsonFormat.decode(type, IOUtils.readFileBytes(file));
            } else {
                throw new IllegalArgumentException("Constant files on other formats than .tbf are not supported, got " +
                                                   file + " for constant " + name);
            }
            // TODO: Support json.lz4
        }
        catch (InterruptedException e) {
            throw new IllegalStateException("Gave up waiting for constant " + name);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Collected information about small constants */
    private static class SmallConstantsInfo {

        private static final Pattern valuePattern = Pattern.compile("constant\\(([a-zA-Z0-9_.]+)\\)\\.value");
        private static final Pattern  typePattern = Pattern.compile("constant\\(([a-zA-Z0-9_.]+)\\)\\.type");

        private final Map<String, TensorType> types = new HashMap<>();
        private final Map<String, String> values = new HashMap<>();

        void addIfSmallConstantInfo(String key, String value) {
            tryValue(key, value);
            tryType(key, value);
        }

        private void tryValue(String key, String value) {
            Matcher matcher = valuePattern.matcher(key);
            if (matcher.matches())
                values.put(matcher.group(1), value);
        }

        private void tryType(String key, String value) {
            Matcher matcher = typePattern.matcher(key);
            if (matcher.matches())
                types.put(matcher.group(1), TensorType.fromSpec(value));
        }

        List<Constant> asConstants() {
            List<Constant> constants = new ArrayList<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                TensorType type = types.get(entry.getKey());
                if (type == null) throw new IllegalStateException("Missing type of '" + entry.getKey() + "'"); // Won't happen
                constants.add(new Constant(entry.getKey(), Tensor.from(type, entry.getValue())));
            }
            return constants;
        }

    }

    private static final Pattern typeDeclarationPattern =
            Pattern.compile("vespa[.]type[.]([a-zA-Z0-9]+)[.](.+)");

    static Optional<String> fromTypeDeclarationSerial(String serialForm) {
        Matcher expressionMatcher = typeDeclarationPattern.matcher(serialForm);
        if ( ! expressionMatcher.matches()) return Optional.empty();
        String name = expressionMatcher.group(1);
        String argument = expressionMatcher.group(2);
        return Optional.of(name + "(" + argument + ")");
    }

}
