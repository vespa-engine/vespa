// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.yahoo.document.DocumentOperation;

/**
 * The result of JSON parsing a single document operation
 *
 * @param operation
 *         the parsed operation
 * @param fullyApplied
 *         true if all the JSON content could be applied,
 *         false if some (or all) of the fields were not poresent in this document and was ignored
 */
public record ParsedDocumentOperation(DocumentOperation operation, boolean fullyApplied) {
}
