// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts RankProfilesConfig instances to RankingExpressions for evaluation.
 * This class can be used by a single thread only.
 *
 * @author bratseth
 */
class RankProfilesConfigImporter {

    /**
     * Constants already imported in this while reading some expression.
     * This is to avoid re-reading constants referenced
     * multiple places, as that is potentially costly.
     */
    private Map<String, Constant> globalImportedConstants = new HashMap<>();

    /**
     * Returns a map of the models contained in this config, indexed on name.
     * The map is modifiable and owned by the caller.
     */
    Map<String, Model> importFrom(RankProfilesConfig config, RankingConstantsConfig constantsConfig) {
        globalImportedConstants.clear();
        try {
            Map<String, Model> models = new HashMap<>();
            for (RankProfilesConfig.Rankprofile profile : config.rankprofile()) {
                Model model = importProfile(profile, constantsConfig);
                models.put(model.name(), model);
            }
            return models;
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Could not read rank profiles config - version mismatch?", e);
        }
    }

    private Model importProfile(RankProfilesConfig.Rankprofile profile, RankingConstantsConfig constantsConfig) throws ParseException {
        List<ExpressionFunction> functions = new ArrayList<>();
        Map<FunctionReference, ExpressionFunction> referencedFunctions = new HashMap<>();
        ExpressionFunction firstPhase = null;
        ExpressionFunction secondPhase = null;

        List<Constant> constants = readConstants(constantsConfig);

        for (RankProfilesConfig.Rankprofile.Fef.Property property : profile.fef().property()) {
            Optional<FunctionReference> reference = FunctionReference.fromSerial(property.name());
            if ( reference.isPresent()) {
                List<String> arguments = new ArrayList<>(); // TODO: Arguments?
                RankingExpression expression = new RankingExpression(reference.get().functionName(), property.value());

                if (reference.get().isFree()) // make available in model under configured name
                    functions.add(new ExpressionFunction(reference.get().functionName(), arguments, expression)); //

                // Make all functions, bound or not available under the name they are referenced by in expressions
                referencedFunctions.put(reference.get(), new ExpressionFunction(reference.get().serialForm(), arguments, expression));
            }
            else if (property.name().equals("vespa.rank.firstphase")) { // Include in addition to macros
                firstPhase = new ExpressionFunction("firstphase", new ArrayList<>(),
                                                    new RankingExpression("first-phase", property.value()));
            }
            else if (property.name().equals("vespa.rank.secondphase")) { // Include in addition to macros
                secondPhase = new ExpressionFunction("secondphase", new ArrayList<>(),
                                                     new RankingExpression("second-phase", property.value()));
            }
        }
        if (functionByName("firstphase", functions) == null && firstPhase != null) // may be already included, depending on body
            functions.add(firstPhase);
        if (functionByName("secondphase", functions) == null && secondPhase != null) // may be already included, depending on body
            functions.add(secondPhase);

        try {
            return new Model(profile.name(), functions, referencedFunctions, constants);
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not load model '" + profile.name() + "'", e);
        }
    }

    private ExpressionFunction functionByName(String name, List<ExpressionFunction> functions) {
        for (ExpressionFunction function : functions)
            if (function.getName().equals(name))
                return function;
        return null;
    }

    private List<Constant> readConstants(RankingConstantsConfig constantsConfig) {
        List<Constant> constants = new ArrayList<>();
        for (RankingConstantsConfig.Constant constantConfig : constantsConfig.constant()) {
            constants.add(new Constant(constantConfig.name(),
                                       readTensorFromFile(TensorType.fromSpec(constantConfig.type()),
                                                          constantConfig.fileref().value())));
        }
        return constants;
    }

    private Tensor readTensorFromFile(TensorType type, String fileName) {
        try {
            if (fileName.endsWith(".tbf"))
                return TypedBinaryFormat.decode(Optional.of(type),
                                                GrowableByteBuffer.wrap(IOUtils.readFileBytes(new File(fileName))));
            // TODO: Support json and json.lz4

            if (fileName.isEmpty()) // this is the case in unit tests
                return Tensor.from(type, "{}");
            throw new IllegalArgumentException("Unknown tensor file format (determined by file ending): " + fileName);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
