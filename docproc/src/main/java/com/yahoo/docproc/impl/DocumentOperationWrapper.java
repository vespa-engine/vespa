// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.impl;

import com.yahoo.document.DocumentOperation;

/**
 * @author Einar M R Rosenvinge
 */
public interface DocumentOperationWrapper {

    DocumentOperation getWrappedDocumentOperation();

}
