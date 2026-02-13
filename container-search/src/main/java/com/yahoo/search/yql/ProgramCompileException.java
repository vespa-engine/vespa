// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.text.Text;


class ProgramCompileException extends IllegalInputException {

    public ProgramCompileException(String message) {
        super(message);
    }

    public ProgramCompileException(Location sourceLocation, String message, Object... args) {
        super(Text.format("%s %s", sourceLocation != null ? sourceLocation : "", args == null ? message : Text.format(message, args)));
    }

}
