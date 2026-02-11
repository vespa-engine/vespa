// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.awt.image.Kernel;

/**
 * Input for class analysis tests.
 * @author Tony Vaagenes
 */
public interface Interface1 extends Interface2 {
    Image methodSignatureTest(Kernel kernel, BufferedImage image);
    int ignoreBasicTypes(float f) throws ImagingOpException;
    int[] ignoreArrayOfBasicTypes(int[][] l);
}
