// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * A real world mlr ranking model, useful to benchmark memory and cpu usage.
 * 
 * @author bratseth
 */
public class NuwaTestCase extends AbstractExportingTestCase {

    @Test
    @Disabled
    void testNuwa() throws IOException, ParseException {
        System.gc();
        long freeBytesBefore = Runtime.getRuntime().freeMemory();
        long totalBytesBefore = Runtime.getRuntime().totalMemory();

        DerivedConfiguration configuration = assertCorrectDeriving("nuwa");

        System.gc();
        long freeBytesAfter = Runtime.getRuntime().freeMemory();
        long totalBytesAfter = Runtime.getRuntime().totalMemory();
        long additionalAllocated = totalBytesAfter - totalBytesBefore;
        System.out.println("Consumed " + ((freeBytesBefore - (freeBytesAfter - additionalAllocated)) / 1000000) + " Mb");
    }

}
