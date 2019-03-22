/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Utilities for tests in this module.
 *
 * @author hmusum
 */
public class TestUtil {

    public static String getContents(File aFile) {
        //...checks on aFile are elided
        StringBuilder contents = new StringBuilder();

        try (BufferedReader input = new BufferedReader(new FileReader(aFile))) {
            String line;
            while ((line = input.readLine()) != null) {
                contents.append(line);
                contents.append(System.getProperty("line.separator"));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return contents.toString();
    }

}
