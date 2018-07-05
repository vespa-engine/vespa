// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.component.Version;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class DocumentProtocolTest {

    private final DocumentTypeManager manager = new DocumentTypeManager();

    @Before
    public void setUp() {
        DocumentTypeManagerConfigurer.configure(manager, "file:./test/cfg/testdoc.cfg");
    }

    @SuppressWarnings("deprecation")
    @Test
    public void requireThat50SerializationPrecedes5xSerialization() {
        DocumentProtocol protocol = new DocumentProtocol(manager);
        GetDocumentMessage prev = new GetDocumentMessage(new DocumentId("doc:scheme:"), "foo");
        byte[] buf = protocol.encode(new Version(5, 0), prev);

        GetDocumentMessage next = (GetDocumentMessage)protocol.decode(new Version(5, 0), buf);
        assertEquals(GetDocumentMessage.DEFAULT_FIELD_SET, next.getFieldSet());
    }

}
