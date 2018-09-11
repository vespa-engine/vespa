// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ApplicationPackageTester;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.yolean.Exceptions;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class RankingExpressionWithTensorFlowTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/tensorflow/");

    /** The model name */
    private final String name = "mnist_softmax_saved";

    private final String vespaExpression = "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(" + name + "_layer_Variable_read), f(a,b)(a * b)), sum, d2), constant(" + name + "_layer_Variable_1_read), f(a,b)(a + b))";

    @After
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testGlobalTensorFlowModel() throws SAXException, IOException {
        ApplicationPackageTester tester = ApplicationPackageTester.create(applicationDir.toString());
        VespaModel model = new VespaModel(tester.app());
        assertLargeConstant(name + "_layer_Variable_1_read", model, Optional.of(10L));
        assertLargeConstant(name + "_layer_Variable_read", model, Optional.of(7840L));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedAppDir = applicationDir.append("copy");
        try {
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(applicationDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            ApplicationPackageTester storedTester = ApplicationPackageTester.create(storedAppDir.toString());
            VespaModel storedModel = new VespaModel(storedTester.app());
            assertLargeConstant(name + "_layer_Variable_1_read", storedModel, Optional.of(10L));
            assertLargeConstant(name + "_layer_Variable_read", storedModel, Optional.of(7840L));
        }
        finally {
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    @Test
    public void testTensorFlowReference() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testTensorFlowReferenceWithConstantFeature() {
        RankProfileSearchFixture search = fixtureWith("constant(mytensor)",
                                                      "tensorflow('mnist_softmax/saved')",
                                                      "constant mytensor { file: ignored\ntype: tensor(d0[7],d1[784]) }",
                                                      null);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
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
    }

    @Test
    public void testNestedTensorFlowReference() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "5 + sum(tensorflow('mnist_softmax/saved'))");
        search.assertFirstPhaseExpression("5 + reduce(" + vespaExpression + ", sum)", "my_profile");
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
            search.compileRankProfile("my_profile", applicationDir.append("models"));
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from " +
                         "tensorflow('mnist_softmax/saved'): " +
                         "Model refers input 'Placeholder' of type tensor(d0[],d1[784]) but this macro is " +
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
                         "Model refers input 'Placeholder'. The required type of this is tensor(d0[],d1[784]), " +
                         "but this macro returns tensor(d0[2],d5[10])",
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
                         "No expressions named 'serving_defaultz' in model 'mnist_softmax/saved'. "+
                         "Available expressions: serving_default.y",
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
                         "No expression 'serving_default.x' in model 'mnist_softmax/saved'. " +
                         "Available expressions: serving_default.y",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testImportingFromStoredExpressions() throws IOException {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");

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
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    @Test
    public void testImportingFromStoredExpressionsWithMacroOverridingConstantAndInheritance() throws IOException {
        String rankProfiles =
                "  rank-profile my_profile {\n" +
                "    macro Placeholder() {\n" +
                "      expression: tensor(d0[2],d1[784])(0.0)\n" +
                "    }\n" +
                "    macro " + name + "_layer_Variable_read() {\n" +
                "      expression: tensor(d1[10],d2[784])(0.0)\n" +
                "    }\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('mnist_softmax/saved')" +
                "    }\n" +
                "  }" +
                "  rank-profile my_profile_child inherits my_profile {\n" +
                "  }";

        String vespaExpressionWithoutConstant =
                "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), " + name + "_layer_Variable_read, f(a,b)(a * b)), sum, d2), constant(" + name + "_layer_Variable_1_read), f(a,b)(a + b))";
        RankProfileSearchFixture search = fixtureWithUncompiled(rankProfiles, new StoringApplicationPackage(applicationDir));
        search.compileRankProfile("my_profile", applicationDir.append("models"));
        search.compileRankProfile("my_profile_child", applicationDir.append("models"));
        search.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");
        search.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile_child");

        assertNull("Constant overridden by macro is not added",
                   search.search().rankingConstants().get("mnist_softmax_saved_layer_Variable_read"));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWithUncompiled(rankProfiles, storedApplication);
            searchFromStored.compileRankProfile("my_profile", applicationDir.append("models"));
            searchFromStored.compileRankProfile("my_profile_child", applicationDir.append("models"));
            searchFromStored.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");
            searchFromStored.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile_child");
            assertNull("Constant overridden by macro is not added",
                       searchFromStored.search().rankingConstants().get("mnist_softmax_saved_layer_Variable_read"));
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    @Test
    public void testTensorFlowReduceBatchDimension() {
        final String expression = "join(join(reduce(join(reduce(rename(Placeholder, (d0, d1), (d0, d2)), sum, d0), constant(" + name + "_layer_Variable_read), f(a,b)(a * b)), sum, d2), constant(" + name + "_layer_Variable_1_read), f(a,b)(a + b)), tensor(d0[1])(1.0), f(a,b)(a * b))";
        RankProfileSearchFixture search = fixtureWith("tensor(d0[1],d1[784])(0.0)",
                                                      "tensorflow('mnist_softmax/saved')");
        search.assertFirstPhaseExpression(expression, "my_profile");
    }

    @Test
    public void testMacroGeneration() {
        final String name = "mnist_saved";
        final String expression = "join(join(reduce(join(join(join(imported_ml_macro_" + name + "_dnn_hidden2_add, reduce(constant(" + name + "_dnn_hidden2_Const), sum, d2), f(a,b)(a * b)), imported_ml_macro_" + name + "_dnn_hidden2_add, f(a,b)(max(a,b))), constant(" + name + "_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(" + name + "_dnn_outputs_bias_read), f(a,b)(a + b)), tensor(d0[1])(1.0), f(a,b)(a * b))";
        final String macroExpression1 = "join(reduce(join(reduce(rename(input, (d0, d1), (d0, d4)), sum, d0), constant(" + name + "_dnn_hidden1_weights_read), f(a,b)(a * b)), sum, d4), constant(" + name + "_dnn_hidden1_bias_read), f(a,b)(a + b))";
        final String macroExpression2 = "join(reduce(join(join(join(imported_ml_macro_" + name + "_dnn_hidden1_add, 0.009999999776482582, f(a,b)(a * b)), imported_ml_macro_" + name + "_dnn_hidden1_add, f(a,b)(max(a,b))), constant(" + name + "_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(" + name + "_dnn_hidden2_bias_read), f(a,b)(a + b))";

        RankProfileSearchFixture search = fixtureWith("tensor(d0[1],d1[784])(0.0)",
                                    "tensorflow('mnist/saved')",
                                                      null,
                                                      null,
                                                      "input",
                                                      new StoringApplicationPackage(applicationDir));
        search.assertFirstPhaseExpression(expression, "my_profile");
        search.assertMacro(macroExpression1, "imported_ml_macro_" + name + "_dnn_hidden1_add", "my_profile");
        search.assertMacro(macroExpression2, "imported_ml_macro_" + name + "_dnn_hidden2_add", "my_profile");
    }

    @Test
    public void testImportingFromStoredExpressionsWithSmallConstantsAndInheritance() throws IOException {
        final String name = "mnist_saved";
        final String rankProfiles =
                "  rank-profile my_profile {\n" +
                "    macro input() {\n" +
                "      expression: tensor(d0[1],d1[784])(0.0)\n" +
                "    }\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('mnist/saved')" +
                "    }\n" +
                "  }" +
                "  rank-profile my_profile_child inherits my_profile {\n" +
                "  }";

        final String expression = "join(join(reduce(join(join(join(imported_ml_macro_" + name + "_dnn_hidden2_add, reduce(constant(" + name + "_dnn_hidden2_Const), sum, d2), f(a,b)(a * b)), imported_ml_macro_" + name + "_dnn_hidden2_add, f(a,b)(max(a,b))), constant(" + name + "_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(" + name + "_dnn_outputs_bias_read), f(a,b)(a + b)), tensor(d0[1])(1.0), f(a,b)(a * b))";
        final String macroExpression1 = "join(reduce(join(reduce(rename(input, (d0, d1), (d0, d4)), sum, d0), constant(" + name + "_dnn_hidden1_weights_read), f(a,b)(a * b)), sum, d4), constant(" + name + "_dnn_hidden1_bias_read), f(a,b)(a + b))";
        final String macroExpression2 = "join(reduce(join(join(join(imported_ml_macro_" + name + "_dnn_hidden1_add, 0.009999999776482582, f(a,b)(a * b)), imported_ml_macro_" + name + "_dnn_hidden1_add, f(a,b)(max(a,b))), constant(" + name + "_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(" + name + "_dnn_hidden2_bias_read), f(a,b)(a + b))";

        RankProfileSearchFixture search = fixtureWithUncompiled(rankProfiles, new StoringApplicationPackage(applicationDir));
        search.compileRankProfile("my_profile", applicationDir.append("models"));
        search.compileRankProfile("my_profile_child", applicationDir.append("models"));
        search.assertFirstPhaseExpression(expression, "my_profile");
        search.assertFirstPhaseExpression(expression, "my_profile_child");
        assertSmallConstant(name + "_dnn_hidden1_mul_x", TensorType.fromSpec("tensor()"), search);
        search.assertMacro(macroExpression1, "imported_ml_macro_" + name + "_dnn_hidden1_add", "my_profile");
        search.assertMacro(macroExpression1, "imported_ml_macro_" + name + "_dnn_hidden1_add", "my_profile_child");
        search.assertMacro(macroExpression2, "imported_ml_macro_" + name + "_dnn_hidden2_add", "my_profile");
        search.assertMacro(macroExpression2, "imported_ml_macro_" + name + "_dnn_hidden2_add", "my_profile_child");

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWithUncompiled(rankProfiles, storedApplication);
            searchFromStored.compileRankProfile("my_profile", applicationDir.append("models"));
            searchFromStored.compileRankProfile("my_profile_child", applicationDir.append("models"));
            searchFromStored.assertFirstPhaseExpression(expression, "my_profile");
            searchFromStored.assertFirstPhaseExpression(expression, "my_profile_child");
            assertSmallConstant(name + "_dnn_hidden1_mul_x", TensorType.fromSpec("tensor()"), search);
            searchFromStored.assertMacro(macroExpression1, "imported_ml_macro_" + name + "_dnn_hidden1_add", "my_profile");
            searchFromStored.assertMacro(macroExpression1, "imported_ml_macro_" + name + "_dnn_hidden1_add", "my_profile_child");
            searchFromStored.assertMacro(macroExpression2, "imported_ml_macro_" + name + "_dnn_hidden2_add", "my_profile");
            searchFromStored.assertMacro(macroExpression2, "imported_ml_macro_" + name + "_dnn_hidden2_add", "my_profile_child");
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    private void assertSmallConstant(String name, TensorType type, RankProfileSearchFixture search) {
        Value value = search.compiledRankProfile("my_profile").getConstants().get(name);
        assertNotNull(value);
        assertEquals(type, value.type());
    }

    /**
     * Verifies that the constant with the given name exists, and - only if an expected size is given -
     * that the content of the constant is available and has the expected size.
     */
    private void assertLargeConstant(String constantName, VespaModel model, Optional<Long> expectedSize) {
        try {
            Path constantApplicationPackagePath = Path.fromString("models.generated/" + name + "/constants").append(constantName + ".tbf");
            RankingConstant rankingConstant = model.rankingConstants().get(constantName);
            assertEquals(constantName, rankingConstant.getName());
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
            RankProfileSearchFixture fixture = new RankProfileSearchFixture(
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
            fixture.compileRankProfile("my_profile", applicationDir.append("models"));
            return fixture;
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private RankProfileSearchFixture fixtureWithUncompiled(String rankProfile, StoringApplicationPackage application) {
        try {
            return new RankProfileSearchFixture(application, application.getQueryProfiles(),
                                                rankProfile, null, null);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static class StoringApplicationPackage extends MockApplicationPackage {

        StoringApplicationPackage(Path applicationPackageWritableRoot) {
            this(applicationPackageWritableRoot, null, null);
        }

        StoringApplicationPackage(Path applicationPackageWritableRoot, String queryProfile, String queryProfileType) {
            super(new File(applicationPackageWritableRoot.toString()),
                  null, null, Collections.emptyList(), null,
                  null, null, false, queryProfile, queryProfileType);
        }

        @Override
        public ApplicationFile getFile(Path file) {
            return new MockApplicationFile(file, Path.fromString(root().toString()));
        }

        @Override
        public List<NamedReader> getFiles(Path path, String suffix) {
            List<NamedReader> readers = new ArrayList<>();
            for (File file : getFileReference(path).listFiles()) {
                if ( ! file.getName().endsWith(suffix)) continue;
                try {
                    readers.add(new NamedReader(file.getName(), new FileReader(file)));
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return readers;
        }

    }

}
