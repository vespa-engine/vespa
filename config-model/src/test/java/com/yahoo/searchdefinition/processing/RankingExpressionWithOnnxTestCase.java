// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.yolean.Exceptions;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.yahoo.searchdefinition.processing.RankingExpressionWithTensorFlowTestCase.StoringApplicationPackage;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class RankingExpressionWithOnnxTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/onnx/");
    private final static String vespaExpression = "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_onnx_Variable), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_onnx_Variable_1), f(a,b)(a + b))";

    @After
    public void removeGeneratedConstantTensorFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testOnnxReferenceWithConstantFeature() {
        RankProfileSearchFixture search = fixtureWith("constant(mytensor)",
                "onnx('mnist_softmax.onnx')",
                "constant mytensor { file: ignored\ntype: tensor(d0[7],d1[784]) }",
                null);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_onnx_Variable", search, Optional.of(7840L));
    }

    @Test
    public void testOnnxReferenceWithQueryFeature() {
        String queryProfile = "<query-profile id='default' type='root'/>";
        String queryProfileType = "<query-profile-type id='root'>" +
                "  <field name='query(mytensor)' type='tensor(d0[3],d1[784])'/>" +
                "</query-profile-type>";
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir,
                queryProfile,
                queryProfileType);
        RankProfileSearchFixture search = fixtureWith("query(mytensor)",
                "onnx('mnist_softmax.onnx')",
                null,
                null,
                "Placeholder",
                application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_onnx_Variable", search, Optional.of(7840L));
    }

    @Test
    public void testOnnxReferenceWithDocumentFeature() {
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir);
        RankProfileSearchFixture search = fixtureWith("attribute(mytensor)",
                "onnx('mnist_softmax.onnx')",
                null,
                "field mytensor type tensor(d0[],d1[784]) { indexing: attribute }",
                "Placeholder",
                application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_onnx_Variable", search, Optional.of(7840L));
    }


    @Test
    public void testOnnxReferenceWithFeatureCombination() {
        String queryProfile = "<query-profile id='default' type='root'/>";
        String queryProfileType = "<query-profile-type id='root'>" +
                "  <field name='query(mytensor)' type='tensor(d0[3],d1[784],d2[10])'/>" +
                "</query-profile-type>";
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir,
                queryProfile,
                queryProfileType);
        RankProfileSearchFixture search = fixtureWith("sum(query(mytensor) * attribute(mytensor) * constant(mytensor),d2)",
                "onnx('mnist_softmax.onnx')",
                "constant mytensor { file: ignored\ntype: tensor(d0[7],d1[784]) }",
                "field mytensor type tensor(d0[],d1[784]) { indexing: attribute }",
                "Placeholder",
                application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_onnx_Variable", search, Optional.of(7840L));
    }


    @Test
    public void testNestedOnnxReference() {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                "5 + sum(onnx('mnist_softmax.onnx'))");
        search.assertFirstPhaseExpression("5 + reduce(" + vespaExpression + ", sum)", "my_profile");
        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_onnx_Variable", search, Optional.of(7840L));
    }

    @Test
    public void testOnnxReferenceMissingMacro() throws ParseException {
        try {
            RankProfileSearchFixture search = new RankProfileSearchFixture(
                    new StoringApplicationPackage(applicationDir),
                    new QueryProfileRegistry(),
                    "  rank-profile my_profile {\n" +
                            "    first-phase {\n" +
                            "      expression: onnx('mnist_softmax.onnx')" +
                            "    }\n" +
                            "  }");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use Onnx model from " +
                            "onnx('mnist_softmax.onnx'): " +
                            "Model refers input 'Placeholder' of type tensor(d0[],d1[784]) but this macro is " +
                            "not present in rank profile 'my_profile'",
                    Exceptions.toMessageString(expected));
        }
    }


    @Test
    public void testOnnxReferenceWithWrongMacroType() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d5[10])(0.0)",
                    "onnx('mnist_softmax.onnx')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use Onnx model from " +
                            "onnx('mnist_softmax.onnx'): " +
                            "Model refers input 'Placeholder'. The required type of this is tensor(d0[],d1[784]), " +
                            "but this macro returns tensor(d0[2],d5[10])",
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testOnnxReferenceSpecifyingNonExistingOutput() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                    "onnx('mnist_softmax.onnx', 'y')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use Onnx model from " +
                            "onnx('mnist_softmax.onnx','y'): " +
                            "Model does not have the specified output 'y'",
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testImportingFromStoredExpressions() throws IOException {
        RankProfileSearchFixture search = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                "onnx('mnist_softmax.onnx')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");

        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));
        assertLargeConstant("mnist_softmax_onnx_Variable", search, Optional.of(7840L));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith("tensor(d0[2],d1[784])(0.0)",
                    "onnx('mnist_softmax.onnx')",
                    null,
                    null,
                    "Placeholder",
                    storedApplication);
            searchFromStored.assertFirstPhaseExpression(vespaExpression, "my_profile");
            // Verify that the constants exists, but don't verify the content as we are not
            // simulating file distribution in this test
            assertLargeConstant("mnist_softmax_onnx_Variable_1", searchFromStored, Optional.empty());
            assertLargeConstant("mnist_softmax_onnx_Variable", searchFromStored, Optional.empty());
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
                        "    macro mnist_softmax_onnx_Variable() {\n" +
                        "      expression: tensor(d1[10],d2[784])(0.0)\n" +
                        "    }\n" +
                        "    first-phase {\n" +
                        "      expression: onnx('mnist_softmax.onnx')" +
                        "    }\n" +
                        "  }";


        String vespaExpressionWithoutConstant =
                "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), mnist_softmax_onnx_Variable, f(a,b)(a * b)), sum, d2), constant(mnist_softmax_onnx_Variable_1), f(a,b)(a + b))";
        RankProfileSearchFixture search = fixtureWith(rankProfile, new StoringApplicationPackage(applicationDir));
        search.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");

        assertNull("Constant overridden by macro is not added",
                search.search().getRankingConstants().get("mnist_softmax_onnx_Variable"));
        assertLargeConstant("mnist_softmax_onnx_Variable_1", search, Optional.of(10L));

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
                    searchFromStored.search().getRankingConstants().get("mnist_softmax_onnx_Variable"));
            assertLargeConstant("mnist_softmax_onnx_Variable_1", searchFromStored, Optional.of(10L));
        } finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    /**
     * Verifies that the constant with the given name exists, and - only if an expected size is given -
     * that the content of the constant is available and has the expected size.
     */
    private void assertLargeConstant(String name, RankProfileSearchFixture search, Optional<Long> expectedSize) {
        try {
            Path constantApplicationPackagePath = Path.fromString("models.generated/mnist_softmax.onnx/constants").append(name + ".tbf");
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

    private RankProfileSearchFixture fixtureWith(String rankProfile, StoringApplicationPackage application) {
        try {
            return new RankProfileSearchFixture(application, application.getQueryProfiles(),
                    rankProfile, null, null);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
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

}
