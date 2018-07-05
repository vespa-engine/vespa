// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Kernel;

/**
 * Input for class analysis tests.
 * @author Tony Vaagenes
 */
public class Base implements Interface1, Interface2 {
    @Override
    public Image methodSignatureTest(Kernel kernel, BufferedImage image) {
        return null;
    }

    @Override
    public int ignoreBasicTypes(float f) {
        return 0;
    }

    @Override
    public int[] ignoreArrayOfBasicTypes(int[][] l) {
        return new int[0];
    }
}
