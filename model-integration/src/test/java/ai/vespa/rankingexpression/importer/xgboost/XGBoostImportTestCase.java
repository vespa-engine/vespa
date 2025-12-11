// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import ai.vespa.rankingexpression.importer.ImportedModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
                ubjExprStr.contains("log(0.6274165)"));

        // UBJ expression should start with the same tree expressions as JSON
        assertTrue("UBJ should contain tree expressions",
                ubjExprStr.startsWith(jsonExprStr));
    }

}
