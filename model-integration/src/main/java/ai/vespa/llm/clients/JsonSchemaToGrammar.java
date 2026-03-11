// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import com.yahoo.api.annotations.Beta;

/**
 * JSON Schema to a GBNF grammar converter used for LLM structured output.
 * Wraps C++ implementation from llama.cpp though JNI.
 * This wrapper only exists for backwards compatibility.
 *
 * @author glebashnik
 */
@Beta
class JsonSchemaToGrammar {
    public static String convert(String schema) {
        try {
            return de.kherud.llama.LlamaModel.jsonSchemaToGrammar(schema);
        } catch (Throwable e) {
            // Catch all throwables including native errors
            throw new RuntimeException("Failed to convert JSON schema to grammar: " + e.getMessage(), e);
        }
    }
}