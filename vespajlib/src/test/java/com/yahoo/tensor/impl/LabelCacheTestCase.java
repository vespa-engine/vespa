package com.yahoo.tensor.impl;

import com.yahoo.tensor.Label;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        var tensor2 = Tensor.Builder.of(tensorType2).cell()
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
    @Timeout(value = 2, unit = SECONDS)
    public void testStringLabelIsGarbageCollected() throws InterruptedException {
        var cache = new LabelCache(32, 1000);
        var xLabel1 = cache.getOrCreateLabel("x");
        var xNumeric1 = xLabel1.asNumeric();
        assertEquals(1, cache.size());
        
        // Garbage collect the label.
        xLabel1 = null;
        System.gc();        
        var yLabels = new Label[10];
        int i;
        
        for (i = 0; i < yLabels.length; i++) {
            // Every create removes garbage collected labels.
            // Labels are stored to avoid garbage collection.
            yLabels[i] = cache.getOrCreateLabel("y" + i);
            
            if (i + 1 == cache.size()) {
                break;
            }
            
            Thread.sleep(100);
        }
        
        assertEquals(i + 1, cache.size());
        
        // A new label with the same string as garbage collected one should have a different numeric value.
        var xLabel2 = cache.getOrCreateLabel("x");
        var xNumeric2 = xLabel2.asNumeric();
        assertNotEquals(xNumeric1, xNumeric2);
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
        
        // Positive numeric labels are created on the fly, not cached.
        // Still they should be equal based on the numeric value.
        assertNotSame(label1, label2); 
        assertEquals(label1, label2);
    }
}
