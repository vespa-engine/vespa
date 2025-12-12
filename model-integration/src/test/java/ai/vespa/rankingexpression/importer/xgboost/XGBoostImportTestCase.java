// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import ai.vespa.rankingexpression.importer.ImportedModel;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class XGBoostImportTestCase {

    @Test
    public void testXGBoost() {
        ImportedModel model = new XGBoostImporter().importModel("test", "src/test/models/xgboost/xgboost.2.2.json");
        assertTrue("All inputs are scalar", model.inputs().isEmpty());
        assertEquals(1, model.expressions().size());
        RankingExpression expression = model.expressions().get("test");
        assertNotNull(expression);
        assertEquals("if (f29 < -0.12345670163631439, if (!(f56 >= -0.2423979938030243), 1.71218, -1.70044), if (f109 < 0.8723472952842712, -1.94071, 1.85965)) + if (!(f60 >= -0.4829469919204712), if (f29 < -4.238749980926514, 0.784718, -0.96853), -6.23624)",
                expression.getRoot().toString());
        assertEquals(1, model.outputExpressions().size());
    }

    @Test
    public void testXGBoostUBJ() {
        // Test that UBJ format imports successfully and includes base_score adjustment
        XGBoostImporter importer = new XGBoostImporter();
        ImportedModel jsonModel = importer.importModel("test", "src/test/models/xgboost/binary_breast_cancer.json");
        ImportedModel ubjModel = importer.importModel("test", "src/test/models/xgboost/binary_breast_cancer.ubj");

        assertNotNull("JSON model should be imported", jsonModel);
        assertNotNull("UBJ model should be imported", ubjModel);

        RankingExpression jsonExpression = jsonModel.expressions().get("test");
        RankingExpression ubjExpression = ubjModel.expressions().get("test");

        assertNotNull("JSON expression should exist", jsonExpression);
        assertNotNull("UBJ expression should exist", ubjExpression);

        String jsonExprStr = jsonExpression.getRoot().toString();
        String ubjExprStr = ubjExpression.getRoot().toString();

        // UBJ should include the base_score logit transformation
        assertTrue("UBJ expression should contain base_score adjustment",
                ubjExprStr.contains(" 0.52114942"));

        // Both formats should use xgboost_input_X format
        assertTrue("UBJ should use xgboost_input_ format",
                ubjExprStr.contains("xgboost_input_"));
        assertTrue("JSON should use xgboost_input_ format",
                jsonExprStr.contains("xgboost_input_"));

        // UBJ expression should start with the same tree expressions as JSON
        assertTrue("UBJ should contain tree expressions matching JSON",
                ubjExprStr.startsWith(jsonExprStr));
    }

    @Test
    public void testXGBoostUBJWithFeatureNames() throws IOException {
        XGBoostUbjParser parser = new XGBoostUbjParser("src/test/models/xgboost/binary_breast_cancer.ubj");

        // Create feature names list (30 features for breast cancer dataset)
        List<String> featureNames = Arrays.asList(
            "mean_radius", "mean_texture", "mean_perimeter", "mean_area",
            "mean_smoothness", "mean_compactness", "mean_concavity",
            "mean_concave_points", "mean_symmetry", "mean_fractal_dimension",
            "radius_error", "texture_error", "perimeter_error", "area_error",
            "smoothness_error", "compactness_error", "concavity_error",
            "concave_points_error", "symmetry_error", "fractal_dimension_error",
            "worst_radius", "worst_texture", "worst_perimeter", "worst_area",
            "worst_smoothness", "worst_compactness", "worst_concavity",
            "worst_concave_points", "worst_symmetry", "worst_fractal_dimension"
        );

        String expression = parser.toRankingExpression(featureNames);
        assertNotNull(expression);
        assertTrue("Expression should contain custom feature name", expression.contains("mean_radius"));
        assertTrue("Expression should contain custom feature name", expression.contains("mean_texture"));
        assertFalse("Expression should not contain indexed format", expression.contains("xgboost_input_"));
    }

    @Test
    public void testXGBoostUBJWithInsufficientFeatureNames() throws IOException {
        XGBoostUbjParser parser = new XGBoostUbjParser("src/test/models/xgboost/binary_breast_cancer.ubj");

        // Only provide 5 feature names when model needs 30
        List<String> featureNames = Arrays.asList("f0", "f1", "f2", "f3", "f4");

        assertThrows(IllegalArgumentException.class, () -> {
            parser.toRankingExpression(featureNames);
        });
    }

    @Test
    public void testXGBoostUBJWithTooManyFeatureNames() throws IOException {
        XGBoostUbjParser parser = new XGBoostUbjParser("src/test/models/xgboost/binary_breast_cancer.ubj");

        // Provide 35 feature names when model needs exactly 30
        List<String> featureNames = Arrays.asList(
            "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9",
            "f10", "f11", "f12", "f13", "f14", "f15", "f16", "f17", "f18", "f19",
            "f20", "f21", "f22", "f23", "f24", "f25", "f26", "f27", "f28", "f29",
            "f30", "f31", "f32", "f33", "f34"
        );

        assertThrows(IllegalArgumentException.class, () -> {
            parser.toRankingExpression(featureNames);
        });
    }

}
