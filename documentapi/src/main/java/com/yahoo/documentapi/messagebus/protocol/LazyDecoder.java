// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.messagebus.Routable;

public interface LazyDecoder {

    public void decode(Routable obj, DocumentDeserializer buf);

}
