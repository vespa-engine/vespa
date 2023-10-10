// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationType;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public interface AnnotationReader {
    public void read(Annotation annotation);
}
