// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.FileReference;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/** Allows us to provide canned tensor constants during import since file distribution does not work in tests */
public class RankProfilesConfigImporterWithMockedConstants extends RankProfilesConfigImporter {

    private static final Logger log = Logger.getLogger(RankProfilesConfigImporterWithMockedConstants.class.getName());

    private final Path constantsPath;

    public RankProfilesConfigImporterWithMockedConstants(Path constantsPath, FileAcquirer fileAcquirer) {
        super(fileAcquirer, new OnnxRuntime());
        this.constantsPath = constantsPath;
    }

    @Override
    protected Tensor readTensorFromFile(String name, TensorType type, FileReference fileReference) {
        try {
            return TypedBinaryFormat.decode(Optional.of(type),
                    GrowableByteBuffer.wrap(IOUtils.readFileBytes(constantsPath.append(name).toFile())));
        }
        catch (IOException e) {
            log.warning("Missing a mocked tensor constant for '" + name + "': " + e.getMessage() +
                    ". Returning an empty tensor");
            return Tensor.from(type, "{}");
        }
    }

    @Override
    protected RankingExpression readExpressionFromFile(String name, FileReference fileReference) throws ParseException {
        try {
            return new RankingExpression(name, readExpressionFromFile(constantsPath.append(fileReference.value()).toFile()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Missing expression file '" + fileReference.value() + "' for expression '" + name + "'.", e);
        }
    }
}
