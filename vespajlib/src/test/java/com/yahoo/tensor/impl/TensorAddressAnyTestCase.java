package com.yahoo.tensor.impl;

import static com.yahoo.tensor.impl.TensorAddressAny.of;
import static com.yahoo.tensor.TensorAddressTestCase.equal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author baldersheim
 */
public class TensorAddressAnyTestCase {

    @Test
    void testSize() {
        for (int i = 0; i < 10; i++) {
            int[] indexes = new int[i];
            assertEquals(i, of(indexes).size());
        }
    }

    @Test
    void testNumericStringEquality() {
        for (int i = 0; i < 10; i++) {
            int[] numericIndexes = new int[i];
            String[] stringIndexes = new String[i];
            for (int j = 0; j < i; j++) {
                numericIndexes[j] = j;
                stringIndexes[j] = String.valueOf(j);
            }
            equal(of(stringIndexes), of(numericIndexes));
        }
    }

}
