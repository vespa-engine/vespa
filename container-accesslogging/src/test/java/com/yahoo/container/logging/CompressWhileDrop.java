package com.yahoo.container.logging;

import java.io.File;

public class CompressWhileDrop {
    public static void main(String [] args) {
        System.out.println("Start compressing file " + args[0]);
        LogFileHandler.runCompression(new File(args[0]));
    }
}
