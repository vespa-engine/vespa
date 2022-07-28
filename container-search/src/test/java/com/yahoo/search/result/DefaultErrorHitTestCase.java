// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Steinar Knutsen
 */
public class DefaultErrorHitTestCase {

    private static final String SOURCE = "nalle";
    DefaultErrorHit de;

    @BeforeEach
    public void setUp() throws Exception {
        de = new DefaultErrorHit(SOURCE, ErrorMessage.createUnspecifiedError("DefaultErrorHitTestCase"));
    }

    @Test
    void testSetSourceTakeTwo() {
        assertEquals(SOURCE, de.getSource());
        de.setSource(null);
        assertNull(de.getSource());
        de.setSource("bamse");
        assertEquals("bamse", de.getSource());
        de.addError(ErrorMessage.createBackendCommunicationError("blblbl"));
        final Iterator<ErrorMessage> errorIterator = de.errorIterator();
        assertEquals(SOURCE, errorIterator.next().getSource());
        assertEquals("bamse", errorIterator.next().getSource());
    }

    @Test
    void testToString() {
        assertEquals("Error: Source 'nalle': 5: Unspecified error: DefaultErrorHitTestCase", de.toString());
    }

    @Test
    void testSetMainError() {
        ErrorMessage e = ErrorMessage.createBackendCommunicationError("abc");
        assertNull(e.getSource());
        de.addError(e);
        assertEquals(SOURCE, e.getSource());
        boolean caught = false;
        try {
            new DefaultErrorHit(SOURCE, (ErrorMessage) null);
        } catch (NullPointerException ex) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            de.addError(null);
        } catch (NullPointerException ex) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    void testAddError() {
        ErrorMessage e = ErrorMessage.createBackendCommunicationError("ljkhlkjh");
        assertNull(e.getSource());
        de.addError(e);
        assertEquals(SOURCE, e.getSource());
        e = ErrorMessage.createBadRequest("kdjfhsdkfhj");
        de.addError(e);
        int i = 0;
        for (Iterator<ErrorMessage> errors = de.errorIterator(); errors.hasNext(); errors.next()) {
            ++i;
        }
        assertEquals(3, i);
    }

    @Test
    void testAddErrors() {
        DefaultErrorHit other = new DefaultErrorHit("abc", ErrorMessage.createBadRequest("sdasd"));
        de.addErrors(other);
        int i = 0;
        for (Iterator<ErrorMessage> errors = de.errorIterator(); errors.hasNext(); errors.next()) {
            ++i;
        }
        assertEquals(2, i);
        other = new DefaultErrorHit("abd", ErrorMessage.createEmptyDocsums("uiyoiuy"));
        other.addError(ErrorMessage.createNoAnswerWhenPingingNode("xzvczx"));
        de.addErrors(other);
        i = 0;
        for (Iterator<ErrorMessage> errors = de.errorIterator(); errors.hasNext(); errors.next()) {
            ++i;
        }
        assertEquals(4, i);
    }

    @Test
    void testHasOnlyErrorCode() {
        assertTrue(de.hasOnlyErrorCode(com.yahoo.container.protect.Error.UNSPECIFIED.code));
        assertFalse(de.hasOnlyErrorCode(com.yahoo.container.protect.Error.BACKEND_COMMUNICATION_ERROR.code));

        de.addError(ErrorMessage.createUnspecifiedError("dsfsdfs"));
        assertTrue(de.hasOnlyErrorCode(com.yahoo.container.protect.Error.UNSPECIFIED.code));
        assertEquals(com.yahoo.container.protect.Error.UNSPECIFIED.code, de.errors().iterator().next().getCode());

        de.addError(ErrorMessage.createBackendCommunicationError("dsfsdfsd"));
        assertFalse(de.hasOnlyErrorCode(com.yahoo.container.protect.Error.UNSPECIFIED.code));
    }

}
