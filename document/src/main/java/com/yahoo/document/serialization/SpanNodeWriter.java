// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.annotation.AlternateSpanList;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanNode;
import com.yahoo.vespa.objects.Serializer;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public interface SpanNodeWriter extends Serializer {
    public void write(SpanNode spanNode);
    public void write(Span span);
    public void write(SpanList spanList);
    public void write(AlternateSpanList altSpanList);
}
