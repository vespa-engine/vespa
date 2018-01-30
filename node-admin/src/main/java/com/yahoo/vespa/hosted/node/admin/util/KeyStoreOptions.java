// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import java.nio.file.Path;

public class KeyStoreOptions {
    public final Path path;
    public final char[] password;
    public final String type;

    public KeyStoreOptions(Path path, char[] password, String type) {
        this.path = path;
        this.password = password;
        this.type = type;
    }
}
