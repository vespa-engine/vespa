// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DimensionRenamerTest {

    @Test
    public void testMnistRenaming() {
        DimensionRenamer renamer = new DimensionRenamer(new IntermediateGraph("test"));

        renamer.addDimension("first_dimension_of_x");
        renamer.addDimension("second_dimension_of_x");
        renamer.addDimension("first_dimension_of_w");
        renamer.addDimension("second_dimension_of_w");
        renamer.addDimension("first_dimension_of_b");

        // which dimension to join on matmul
        renamer.addConstraint("second_dimension_of_x", "first_dimension_of_w", DimensionRenamer.Constraint.equal(false), null);

        // other dimensions in matmul can't be equal
        renamer.addConstraint("first_dimension_of_x", "second_dimension_of_w", DimensionRenamer.Constraint.lessThan(false), null);

        // for efficiency, put dimension to join on innermost
        renamer.addConstraint("first_dimension_of_x", "second_dimension_of_x", DimensionRenamer.Constraint.lessThan(true), null);
        renamer.addConstraint("first_dimension_of_w", "second_dimension_of_w", DimensionRenamer.Constraint.greaterThan(true), null);

        // bias
        renamer.addConstraint("second_dimension_of_w", "first_dimension_of_b", DimensionRenamer.Constraint.equal(false), null);

        renamer.solve();

        String firstDimensionOfXName = renamer.dimensionNameOf("first_dimension_of_x").get();
        String secondDimensionOfXName = renamer.dimensionNameOf("second_dimension_of_x").get();
        String firstDimensionOfWName = renamer.dimensionNameOf("first_dimension_of_w").get();
        String secondDimensionOfWName = renamer.dimensionNameOf("second_dimension_of_w").get();
        String firstDimensionOfBName = renamer.dimensionNameOf("first_dimension_of_b").get();

        assertTrue(firstDimensionOfXName.compareTo(secondDimensionOfXName) < 0);
        assertTrue(firstDimensionOfWName.compareTo(secondDimensionOfWName) > 0);
        assertTrue(secondDimensionOfXName.compareTo(firstDimensionOfWName) == 0);
        assertTrue(firstDimensionOfXName.compareTo(secondDimensionOfWName) < 0);
        assertTrue(secondDimensionOfWName.compareTo(firstDimensionOfBName) == 0);
    }

}
