/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class TestUtil {

    public static String getContents(String filename) {
        InputStream in = TestUtil.class.getClassLoader().getResourceAsStream(filename);
        if (in == null) {
            throw new RuntimeException("File not found: " + filename);
        }
        return new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
    }
}
