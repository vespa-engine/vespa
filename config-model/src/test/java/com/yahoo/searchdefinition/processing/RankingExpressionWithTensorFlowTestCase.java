// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.yolean.Exceptions;
import org.junit.After;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class RankingExpressionWithTensorFlowTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/tensorflow/");
    private final String vespaExpression = "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_saved_layer_Variable_read), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_saved_layer_Variable_1_read), f(a,b)(a + b))";

    @After
    public void removeGeneratedConstantTensorFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testTensorFlowReference() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testTensorFlowReferenceWithConstantFeature() {
        RankProfileSearchFixture search = fixtureWith("constant(mytensor)",
                                                      "tensorflow('mnist_softmax/saved')",
                                                      "constant mytensor { file: ignored\ntype: tensor(d0[7],d1[784]) }",
                                                      null);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testTensorFlowReferenceWithQueryFeature() {
        String queryProfile = "<query-profile id='default' type='root'/>";
        String queryProfileType = "<query-profile-type id='root'>" +
                                  "  <field name='query(mytensor)' type='tensor(d0[3],d1[784])'/>" +
                                  "</query-profile-type>";
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir,
                                                                              queryProfile,
                                                                              queryProfileType);
        RankProfileSearchFixture search = fixtureWith("query(mytensor)",
                                                      "tensorflow('mnist_softmax/saved')",
                                                      null,
                                                      null,
                                                      "Placeholder",
                                                      application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testTensorFlowReferenceWithDocumentFeature() {
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir);
        RankProfileSearchFixture search = fixtureWith("attribute(mytensor)",
                                                      "tensorflow('mnist_softmax/saved')",
                                                      null,
                                                      "field mytensor type tensor(d0[],d1[784]) { indexing: attribute }",
                                                      "Placeholder",
                                                      application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testTensorFlowReferenceWithFeatureCombination() {
        String queryProfile = "<query-profile id='default' type='root'/>";
        String queryProfileType = "<query-profile-type id='root'>" +
                                  "  <field name='query(mytensor)' type='tensor(d0[3],d1[784],d2[10])'/>" +
                                  "</query-profile-type>";
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir,
                                                                              queryProfile,
                                                                              queryProfileType);
        RankProfileSearchFixture search = fixtureWith("sum(query(mytensor) * attribute(mytensor) * constant(mytensor),d2)",
                                                      "tensorflow('mnist_softmax/saved')",
                                                      "constant mytensor { file: ignored\ntype: tensor(d0[7],d1[784]) }",
                                                      "field mytensor type tensor(d0[],d1[784]) { indexing: attribute }",
                                                      "Placeholder",
                                                      application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testNestedTensorFlowReference() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "5 + sum(tensorflow('mnist_softmax/saved'))");
        search.assertFirstPhaseExpression("5 + reduce(" + vespaExpression + ", sum)", "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testTensorFlowReferenceSpecifyingSignature() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved', 'serving_default')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testTensorFlowReferenceSpecifyingSignatureAndOutput() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved', 'serving_default', 'y')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testTensorFlowReferenceMissingMacro() throws ParseException {
        try {
            RankProfileSearchFixture search = new RankProfileSearchFixture(
                    new StoringApplicationPackage(applicationDir),
                    new QueryProfileRegistry(),
                    "  rank-profile my_profile {\n" +
                    "    first-phase {\n" +
                    "      expression: tensorflow('mnist_softmax/saved')" +
                    "    }\n" +
                    "  }");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from " +
                         "tensorflow('mnist_softmax/saved'): " +
                         "Model refers Placeholder 'Placeholder' of type tensor(d0[],d1[784]) but this macro is " +
                         "not present in rank profile 'my_profile'",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testTensorFlowReferenceWithWrongMacroType() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d5[10])(0.0)",
                                                          "tensorflow('mnist_softmax/saved')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from " +
                         "tensorflow('mnist_softmax/saved'): " +
                         "Model refers Placeholder 'Placeholder' of type tensor(d0[],d1[784]) which must be produced " +
                         "by a macro in the rank profile, but this macro produces type tensor(d0[2],d5[10])",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testTensorFlowReferenceSpecifyingNonExistingSignature() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                          "tensorflow('mnist_softmax/saved', 'serving_defaultz')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from " +
                         "tensorflow('mnist_softmax/saved','serving_defaultz'): " +
                         "Model does not have the specified signature 'serving_defaultz'",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testTensorFlowReferenceSpecifyingNonExistingOutput() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                          "tensorflow('mnist_softmax/saved', 'serving_default', 'x')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from " +
                         "tensorflow('mnist_softmax/saved','serving_default','x'): " +
                         "Model does not have the specified output 'x'",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testImportingFromStoredExpressions() throws IOException {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");

        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                                    "tensorflow('mnist_softmax/saved')",
                                                                    null,
                                                                    null,
                                                                    "Placeholder",
                                                                    storedApplication);
            searchFromStored.assertFirstPhaseExpression(vespaExpression, "my_profile");
            // Verify that the constants exists, but don't verify the content as we are not
            // simulating file distribution in this test
            assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", searchFromStored, Optional.empty());
            assertLargeConstant("mnist_softmax_saved_layer_Variable_read", searchFromStored, Optional.empty());
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    @Test
    public void testImportingFromStoredExpressionsWithMacroOverridingConstant() throws IOException {
        String rankProfile =
                "  rank-profile my_profile {\n" +
                "    macro Placeholder() {\n" +
                "      expression: tensor(d0[2],d1[784])(0.0)\n" +
                "    }\n" +
                "    macro mnist_softmax_saved_layer_Variable_read() {\n" +
                "      expression: tensor(d1[10],d2[784])(0.0)\n" +
                "    }\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('mnist_softmax/saved')" +
                "    }\n" +
                "  }";


        String vespaExpressionWithoutConstant =
                "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), mnist_softmax_saved_layer_Variable_read, f(a,b)(a * b)), sum, d2), constant(mnist_softmax_saved_layer_Variable_1_read), f(a,b)(a + b))";
        RankProfileSearchFixture search = fixtureWith(rankProfile, new StoringApplicationPackage(applicationDir));
        search.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");

        assertNull("Constant overridden by macro is not added",
                   search.search().getRankingConstants().get("mnist_softmax_saved_layer_Variable_read"));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith(rankProfile, storedApplication);
            searchFromStored.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");
            assertNull("Constant overridden by macro is not added",
                       searchFromStored.search().getRankingConstants().get("mnist_softmax_saved_layer_Variable_read"));
            assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", searchFromStored, Optional.of(10L));
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    @Test
    public void testTensorFlowReduceBatchDimension() {
        final String expression = "join(join(reduce(join(reduce(rename(Placeholder, (d0, d1), (d0, d2)), sum, d0), constant(mnist_softmax_saved_layer_Variable_read), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_saved_layer_Variable_1_read), f(a,b)(a + b)), tensor(d0[1])(1.0), f(a,b)(a * b))";
        RankProfileSearchFixture search = fixtureWith("tensor(d0[1],d1[784])(0.0)",
                "tensorflow('mnist_softmax/saved')");
        search.assertFirstPhaseExpression(expression, "my_profile");
        assertLargeConstant("mnist_softmax_saved_layer_Variable_1_read", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_saved_layer_Variable_read", search, Optional.of(7840L));
    }

    @Test
    public void testMacroGeneration() {
        final String expression = "join(join(reduce(join(join(join(tf_macro_mnist_saved_dnn_hidden2_add, reduce(constant(mnist_saved_dnn_hidden2_Const), sum, d2), f(a,b)(a * b)), tf_macro_mnist_saved_dnn_hidden2_add, f(a,b)(max(a,b))), constant(mnist_saved_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(mnist_saved_dnn_outputs_bias_read), f(a,b)(a + b)), tensor(d0[1])(1.0), f(a,b)(a * b))";
        final String macroExpression1 = "join(reduce(join(reduce(rename(input, (d0, d1), (d0, d4)), sum, d0), constant(mnist_saved_dnn_hidden1_weights_read), f(a,b)(a * b)), sum, d4), constant(mnist_saved_dnn_hidden1_bias_read), f(a,b)(a + b))";
        final String macroExpression2 = "join(reduce(join(join(join(tf_macro_mnist_saved_dnn_hidden1_add, 0.009999999776482582, f(a,b)(a * b)), tf_macro_mnist_saved_dnn_hidden1_add, f(a,b)(max(a,b))), constant(mnist_saved_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(mnist_saved_dnn_hidden2_bias_read), f(a,b)(a + b))";

        RankProfileSearchFixture search = fixtureWith("tensor(d0[1],d1[784])(0.0)",
                                    "tensorflow('mnist/saved')",
                                                      null,
                                                      null,
                                                      "input",
                                                      new StoringApplicationPackage(applicationDir));
        search.assertFirstPhaseExpression(expression, "my_profile");
        search.assertMacro(macroExpression1, "tf_macro_mnist_saved_dnn_hidden1_add", "my_profile");
        search.assertMacro(macroExpression2, "tf_macro_mnist_saved_dnn_hidden2_add", "my_profile");
    }

    @Test
    public void testImportingFromStoredExpressionsWithSmallConstants() throws IOException {
        final String expression = "join(join(reduce(join(join(join(tf_macro_mnist_saved_dnn_hidden2_add, reduce(constant(mnist_saved_dnn_hidden2_Const), sum, d2), f(a,b)(a * b)), tf_macro_mnist_saved_dnn_hidden2_add, f(a,b)(max(a,b))), constant(mnist_saved_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(mnist_saved_dnn_outputs_bias_read), f(a,b)(a + b)), tensor(d0[1])(1.0), f(a,b)(a * b))";
        final String macroExpression1 = "join(reduce(join(reduce(rename(input, (d0, d1), (d0, d4)), sum, d0), constant(mnist_saved_dnn_hidden1_weights_read), f(a,b)(a * b)), sum, d4), constant(mnist_saved_dnn_hidden1_bias_read), f(a,b)(a + b))";
        final String macroExpression2 = "join(reduce(join(join(join(tf_macro_mnist_saved_dnn_hidden1_add, 0.009999999776482582, f(a,b)(a * b)), tf_macro_mnist_saved_dnn_hidden1_add, f(a,b)(max(a,b))), constant(mnist_saved_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(mnist_saved_dnn_hidden2_bias_read), f(a,b)(a + b))";

        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir);
        RankProfileSearchFixture search = fixtureWith("tensor(d0[1],d1[784])(0.0)",
                "tensorflow('mnist/saved')",
                null,
                null,
                "input",
                application);
        search.assertFirstPhaseExpression(expression, "my_profile");
        assertSmallConstant("mnist_saved_dnn_hidden1_mul_x", TensorType.fromSpec("tensor()"), search);
        search.assertMacro(macroExpression1, "tf_macro_mnist_saved_dnn_hidden1_add", "my_profile");
        search.assertMacro(macroExpression2, "tf_macro_mnist_saved_dnn_hidden2_add", "my_profile");

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith("tensor(d0[1],d1[784])(0.0)",
                    "tensorflow('mnist/saved')",
                    null,
                    null,
                    "input",
                    storedApplication);
            searchFromStored.assertFirstPhaseExpression(expression, "my_profile");
            assertSmallConstant("mnist_saved_dnn_hidden1_mul_x", TensorType.fromSpec("tensor()"), search);
            searchFromStored.assertMacro(macroExpression1, "tf_macro_mnist_saved_dnn_hidden1_add", "my_profile");
            searchFromStored.assertMacro(macroExpression2, "tf_macro_mnist_saved_dnn_hidden2_add", "my_profile");
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    private void assertSmallConstant(String name, TensorType type, RankProfileSearchFixture search) {
        Value value = search.rankProfile("my_profile").getConstants().get(name);
        assertNotNull(value);
        assertEquals(type, value.type());
    }

    /**
     * Verifies that the constant with the given name exists, and - only if an expected size is given -
     * that the content of the constant is available and has the expected size.
     */
    private void assertLargeConstant(String name, RankProfileSearchFixture search, Optional<Long> expectedSize) {
        try {
            Path constantApplicationPackagePath = Path.fromString("models.generated/mnist_softmax/saved/constants").append(name + ".tbf");
            RankingConstant rankingConstant = search.search().getRankingConstants().get(name);
            assertEquals(name, rankingConstant.getName());
            assertTrue(rankingConstant.getFileName().endsWith(constantApplicationPackagePath.toString()));

            if (expectedSize.isPresent()) {
                Path constantPath = applicationDir.append(constantApplicationPackagePath);
                assertTrue("Constant file '" + constantPath + "' has been written",
                           constantPath.toFile().exists());
                Tensor deserializedConstant = TypedBinaryFormat.decode(Optional.empty(),
                                                                       GrowableByteBuffer.wrap(IOUtils.readFileBytes(constantPath.toFile())));
                assertEquals(expectedSize.get().longValue(), deserializedConstant.size());
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RankProfileSearchFixture fixtureWith(String placeholderExpression, String firstPhaseExpression) {
        return fixtureWith(placeholderExpression, firstPhaseExpression, null, null, "Placeholder",
                           new StoringApplicationPackage(applicationDir));
    }

    private RankProfileSearchFixture fixtureWith(String placeholderExpression, String firstPhaseExpression,
                                                 String constant, String field) {
        return fixtureWith(placeholderExpression, firstPhaseExpression, constant, field, "Placeholder",
                           new StoringApplicationPackage(applicationDir));
    }

    private RankProfileSearchFixture fixtureWith(String macroExpression,
                                                 String firstPhaseExpression,
                                                 String constant,
                                                 String field,
                                                 String macroName,
                                                 StoringApplicationPackage application) {
        try {
            return new RankProfileSearchFixture(
                    application,
                    application.getQueryProfiles(),
                    "  rank-profile my_profile {\n" +
                    "    macro " + macroName + "() {\n" +
                    "      expression: " + macroExpression +
                    "    }\n" +
                    "    first-phase {\n" +
                    "      expression: " + firstPhaseExpression +
                    "    }\n" +
                    "  }",
                    constant,
                    field);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private RankProfileSearchFixture fixtureWith(String rankProfile, StoringApplicationPackage application) {
        try {
            return new RankProfileSearchFixture(application, application.getQueryProfiles(),
                                                rankProfile, null, null);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static class StoringApplicationPackage extends MockApplicationPackage {

        private final File root;

        StoringApplicationPackage(Path applicationPackageWritableRoot) {
            this(applicationPackageWritableRoot, null, null);
        }

        StoringApplicationPackage(Path applicationPackageWritableRoot, String queryProfile, String queryProfileType) {
            super(null, null, Collections.emptyList(), null,
                  null, null, false, queryProfile, queryProfileType);
            this.root = new File(applicationPackageWritableRoot.toString());
        }

        @Override
        public File getFileReference(Path path) {
            return Path.fromString(root.toString()).append(path).toFile();
        }

        @Override
        public ApplicationFile getFile(Path file) {
            return new StoringApplicationPackageFile(file, Path.fromString(root.toString()));
        }

    }

    private static class StoringApplicationPackageFile extends ApplicationFile {

        /** The path to the application package root */
        private final Path root;

        /** The File pointing to the actual file represented by this */
        private final File file;

        StoringApplicationPackageFile(Path filePath, Path applicationPackagePath) {
            super(filePath);
            this.root = applicationPackagePath;
            file = applicationPackagePath.append(filePath).toFile();
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public boolean exists() {
            return file.exists();
        }

        @Override
        public Reader createReader() throws FileNotFoundException {
            try {
                if ( ! exists()) throw new FileNotFoundException("File '" + file + "' does not exist");
                return IOUtils.createReader(file, "UTF-8");
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream createInputStream() throws FileNotFoundException {
            try {
                if ( ! exists()) throw new FileNotFoundException("File '" + file + "' does not exist");
                return new BufferedInputStream(new FileInputStream(file));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile createDirectory() {
            file.mkdirs();
            return this;
        }

        @Override
        public ApplicationFile writeFile(Reader input) {
            try {
                IOUtils.writeFile(file, IOUtils.readAll(input), false);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile appendFile(String value) {
            try {
                IOUtils.writeFile(file, value, true);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public List<ApplicationFile> listFiles(PathFilter filter) {
            if ( ! isDirectory()) return Collections.emptyList();
            return Arrays.stream(file.listFiles()).filter(f -> filter.accept(Path.fromString(f.toString())))
                                                  .map(f -> new StoringApplicationPackageFile(asApplicationRelativePath(f),
                                                                                              root))
                                                  .collect(Collectors.toList());
        }

        @Override
        public ApplicationFile delete() {
            file.delete();
            return this;
        }

        @Override
        public MetaData getMetaData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(ApplicationFile other) {
            return this.getPath().getName().compareTo((other).getPath().getName());
        }

        /** Strips the application package root path prefix from the path of the given file */
        private Path asApplicationRelativePath(File file) {
            Path path = Path.fromString(file.toString());

            Iterator<String> pathIterator = path.iterator();
            // Skip the path elements this shares with the root
            for (Iterator<String> rootIterator = root.iterator(); rootIterator.hasNext(); ) {
                String rootElement = rootIterator.next();
                String pathElement = pathIterator.next();
                if ( ! rootElement.equals(pathElement)) throw new RuntimeException("Assumption broken");
            }
            // Build a path from the remaining
            Path relative = Path.fromString("");
            while (pathIterator.hasNext())
                relative = relative.append(pathIterator.next());
            return relative;
        }

    }

}
