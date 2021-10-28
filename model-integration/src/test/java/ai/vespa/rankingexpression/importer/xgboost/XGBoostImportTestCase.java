// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        assertEquals("if (f29 < -0.1234567, if (!(f56 >= -0.242398), 1.71218, -1.70044), if (f109 < 0.8723473, -1.94071, 1.85965)) + if (!(f60 >= -0.482947), if (f29 < -4.2387498, 0.784718, -0.96853), -6.23624)",
                expression.getRoot().toString());
        assertEquals(1, model.outputExpressions().size());
    }

}
