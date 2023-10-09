// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.feedhandler.InputStreamRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class FileRequest extends InputStreamRequest {

    FileRequest(File f) throws FileNotFoundException {
        super(new FileInputStream(f));
    }

}
