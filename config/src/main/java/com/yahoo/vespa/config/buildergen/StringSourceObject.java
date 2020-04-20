// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * Represents an in memory source object that can be compiled.
 *
 * @author Ulf Lilleengen
 */
class StringSourceObject extends SimpleJavaFileObject {
    private final String code;
    StringSourceObject(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
