package com.yahoo.tensor;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class IndexedTensorTestCase {

    private final int vSize = 1;
    private final int wSize = 2;
    private final int xSize = 3;
    private final int ySize = 4;
    private final int zSize = 5;

    @Test
    public void testEmpty() {
        Tensor empty = Tensor.Builder.of(TensorType.empty).build();
        assertTrue(empty instanceof IndexedTensor);
        assertTrue(empty.cells().isEmpty());
        assertEquals("{}", empty.toString());
        Tensor emptyFromString = Tensor.from(TensorType.empty, "{}");
        assertEquals("{}", Tensor.from(TensorType.empty, "{}").toString());
        assertTrue(emptyFromString.cells().isEmpty());
        assertTrue(emptyFromString instanceof IndexedTensor);
        assertEquals(empty, emptyFromString);
        
        Tensor singleValue = Tensor.Builder.of(TensorType.empty).cell(TensorAddress.empty, 3.5).build();
        assertEquals("{3.5}", singleValue.toString());
        assertEquals("{3.5}", Tensor.from(TensorType.empty, "{3.5}").toString());

        TensorType type = new TensorType.Builder().indexed("x").indexed("y").build();
        Tensor emptyWithDimensions = Tensor.Builder.of(type).build();
        assertTrue(emptyWithDimensions instanceof IndexedTensor);
        assertEquals("tensor(x[],y[]):{}", emptyWithDimensions.toString());
        assertEquals("tensor(x[],y[]):{}", Tensor.from("tensor(x[],y[]):{}").toString());
        IndexedTensor emptyWithDimensionsIndexed = (IndexedTensor)emptyWithDimensions;
        assertEquals(0, emptyWithDimensionsIndexed.length(0));
        assertEquals(0, emptyWithDimensionsIndexed.length(1));
    }

    @Test
    public void testBoundBuilding() {
        TensorType type = new TensorType.Builder().indexed("v", vSize)
                                                  .indexed("w", wSize)
                                                  .indexed("x", xSize)
                                                  .indexed("y", ySize)
                                                  .indexed("z", zSize)
                                                  .build();
        assertBuildingVWXYZ(type);
    }

    @Test
    public void testUnboundBuilding() {
        TensorType type = new TensorType.Builder().indexed("w")
                                                  .indexed("v")
                                                  .indexed("x")
                                                  .indexed("y")
                                                  .indexed("z").build();
        assertBuildingVWXYZ(type);
    }
    
    private void assertBuildingVWXYZ(TensorType type) {
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        // Build in scrambled order
        for (int v = 0; v < vSize; v++)
            for (int w = 0; w < wSize; w++)
                for (int y = 0; y < ySize; y++)
                    for (int x = xSize - 1; x >= 0; x--)
                        for (int z = 0; z < zSize; z++)
                            builder.cell(v * w + x + y * z, v, w, x, y, z);

        IndexedTensor tensor = builder.build();

        // Lookup by index arguments
        for (int v = 0; v < vSize; v++)
            for (int w = 0; w < wSize; w++)
                for (int y = 0; y < ySize; y++)
                    for (int x = xSize - 1; x >= 0; x--)
                        for (int z = 0; z < zSize; z++)
                            assertEquals(v * w + x + y * z, (int) tensor.get(v, w, x, y, z));


        // Lookup by TensorAddress argument
        for (int v = 0; v < vSize; v++)
            for (int w = 0; w < wSize; w++)
                for (int y = 0; y < ySize; y++)
                    for (int x = xSize - 1; x >= 0; x--)
                        for (int z = 0; z < zSize; z++)
                            assertEquals(v * w + x + y * z, (int) tensor.get(new TensorAddress(v, w, x, y, z)));
    }
    
}
