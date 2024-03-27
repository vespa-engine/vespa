// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm;

import com.yahoo.api.annotations.Beta;

@Beta
public class LanguageModelException extends RuntimeException {

    private final int code;

    public LanguageModelException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
