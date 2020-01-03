// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import java.io.File;

public class CompressWhileDrop {
    public static void main(String [] args) {
        System.out.println("Start compressing file " + args[0]);
        LogFileHandler.runCompression(new File(args[0]));
    }
}
