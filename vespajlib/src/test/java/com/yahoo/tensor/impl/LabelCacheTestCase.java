package com.yahoo.tensor.impl;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelCacheTestCase {
    @Test
    public void testSameStringsGetSameLabel() {
        var cache = new LabelCache(32, 1000);
        var label1 = cache.getOrCreateLabel("l1");
        var label2 = cache.getOrCreateLabel("l1");
        assertEquals(label1, label2);
    }
    
    @Test
    public void testDifferentStringsGetDifferentLabel() {
        var cache = new LabelCache(32, 1000);
        var label1 = cache.getOrCreateLabel("l1");
        var label2 = cache.getOrCreateLabel("l2");
        assertNotEquals(label1, label2);
    }
    
    @Test
    public void testSameStringLabelsInDifferentCells() {
        var tensorType = new TensorType.Builder().mapped("d1").mapped("d2").build();
        var tensor = Tensor.Builder.of(tensorType)
                .cell().label("d1", "l1").label("d2", "l1").value(1)
                .cell().label("d1", "l1").label("d2", "l2").value(2)
                .build();
        
        var cellIter = tensor.cellIterator();
        
        var cell1 = cellIter.next(); 
        var label1 = cell1.getKey().objectLabel(0);
        
        var cell2 = cellIter.next();
        var label2 = cell2.getKey().objectLabel(0);
        
        assertNotEquals(cell1, cell2);
        assertEquals(label1, label2);
    }

    @Test
    public void testSameStringLabelsInDifferentTensors() {
        var tensorType1 = new TensorType.Builder().mapped("d1").mapped("d2").build();
        var tensor1 = Tensor.Builder.of(tensorType1)
                .cell().label("d1", "l1").label("d2", "l1").value(1)
                .cell().label("d1", "l1").label("d2", "l2").value(2)
                .build();

        var tensorType2 = new TensorType.Builder().mapped("d1").mapped("d2").mapped("d3").build();
        var tensor2 = Tensor.Builder.of(tensorType1).cell()
                .label("d1", "l1").label("d2", "l2").label("d3", "l3")
                .value(1)
                .build();

        var cellIter1 = tensor1.cellIterator();
        var cell1 = cellIter1.next();
        var label1 = cell1.getKey().objectLabel(0);

        var cellIter2 = tensor2.cellIterator();
        var cell2 = cellIter2.next();
        var label2 = cell2.getKey().objectLabel(0);

        assertNotEquals(cell1, cell2);
        assertEquals(label1, label2);
    }
    
    @Test
    public void testStringLabelIsGarbageCollected() throws InterruptedException {
        var cache = new LabelCache(32, 1000);
        var label1 = cache.getOrCreateLabel("l1");
        var numeric1 = label1.toNumeric();
        assertEquals(1, cache.size());
        
        label1 = null;
        System.gc();
        Thread.sleep(1000);

        cache.getOrCreateLabel("l2");
        
        // Need to wait for garbage collector, 20 attempts with half a second wait between.
        for (int i = 0; i < 20; i++) {
            if (cache.size() == 1)
                break;
            
            Thread.sleep(500);
            System.gc();
        }
        
        assertEquals(1, cache.size());
        
        var label2 = cache.getOrCreateLabel("l1");
        var numeric2 = label2.toNumeric();
        assertEquals(2, cache.size());
        assertNotEquals(numeric1, numeric2);
    }
    
    @Test
    public void testPositiveNumericLabelNotCached() {
        var cache = new LabelCache(1, 10);
        cache.getOrCreateLabel(10);
        cache.getOrCreateLabel(20);
        assertEquals(0, cache.size());
    }

    @Test
    public void testNegativeNumericLabelIllegal() {
        var cache = new LabelCache(1, 10);
        assertThrows(IllegalArgumentException.class, () -> cache.getOrCreateLabel(-10));
    }
    
    @Test
    public void testPositiveSameNumericDifferentLabel() {
        var cache = new LabelCache(1, 10);
        var label1 = cache.getOrCreateLabel(10);
        var label2 = cache.getOrCreateLabel(10);
        assertTrue(label1 != label2);
        assertEquals(label1, label2);
    }

}
