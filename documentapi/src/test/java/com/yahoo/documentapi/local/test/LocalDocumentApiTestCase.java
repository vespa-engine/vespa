// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local.test;

import com.yahoo.document.*;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.local.LocalDocumentAccess;
import com.yahoo.documentapi.test.AbstractDocumentApiTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Runs the superclass tests on this implementation
 *
 * @author bratseth
 */
public class LocalDocumentApiTestCase extends AbstractDocumentApiTestCase {

    protected DocumentAccess access;

    @Override
    protected DocumentAccess access() {
        return access;
    }

    @Before
    public void setUp() {
        DocumentAccessParams params = new DocumentAccessParams();
        params.setDocumentManagerConfigId("file:src/test/cfg/documentmanager.cfg");
        access = new LocalDocumentAccess(params);
    }

    @After
    public void shutdownAccess() {
        access.shutdown();
    }

    @Test
    public void testNoExceptionFromAsync() {
        AsyncSession session = access.createAsyncSession(new AsyncParameters());

        DocumentType type = access.getDocumentTypeManager().getDocumentType("music");
        DocumentUpdate docUp = new DocumentUpdate(type, new DocumentId("doc:music:2"));

        Result result = session.update(docUp);
        assertTrue(result.isSuccess());
        Response response = session.getNext();
        assertEquals(result.getRequestId(), response.getRequestId());
        assertFalse(response.isSuccess());
        session.destroy();
    }
}
