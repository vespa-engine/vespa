// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ExecutionContextTestCase {

    @Test
    public void requireThatValueCanBeSet() {
        ExecutionContext ctx = new ExecutionContext();
        FieldValue val = new StringFieldValue("foo");
        ctx.setValue(val);
        assertSame(val, ctx.getValue());
    }

    @Test
    public void requireThatVariablesCanBeSet() {
        ExecutionContext ctx = new ExecutionContext();
        FieldValue val = new StringFieldValue("foo");
        ctx.setVariable("foo", val);
        assertSame(val, ctx.getVariable("foo"));
    }

    @Test
    public void requireThatLanguageCanBeSet() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setLanguage(Language.ARABIC);
        assertEquals(Language.ARABIC, ctx.getLanguage());
    }

    @Test
    public void requireThatNullLanguageThrowsException() {
        ExecutionContext ctx = new ExecutionContext();
        try {
            ctx.setLanguage(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatClearRemovesValue() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setValue(new StringFieldValue("foo"));
        ctx.clear();
        assertNull(ctx.getValue());
    }

    @Test
    public void requireThatClearRemovesVariables() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setVariable("foo", new StringFieldValue("foo"));
        ctx.clear();
        assertNull(ctx.getVariable("foo"));
    }

    @Test
    public void requireThatClearDoesNotClearLanguage() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setLanguage(Language.ARABIC);
        ctx.clear();
        assertEquals(Language.ARABIC, ctx.getLanguage());
    }

    @Test
    public void requireThatResolveLanguageDefaultsToEnglishWithoutLinguistics() {
        ExecutionContext ctx = new ExecutionContext();
        assertEquals(Language.ENGLISH, ctx.resolveLanguage(null));
    }

    @Test
    public void requireThatResolveLanguageDefaultsToEnglishWithoutValue() {
        ExecutionContext ctx = new ExecutionContext();
        assertEquals(Language.ENGLISH, ctx.resolveLanguage(new SimpleLinguistics()));
    }

    @Test
    public void requireThatLanguageCanBeResolved() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setValue(new StringFieldValue("\u3072\u3089\u304c\u306a"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(new SimpleLinguistics()));
        ctx.setValue(new StringFieldValue("\ud55c\uae00\uacfc"));
        assertEquals(Language.KOREAN, ctx.resolveLanguage(new SimpleLinguistics()));
    }

    @Test
    public void requireThatExplicitLanguagePreventsDetection() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setLanguage(Language.ARABIC);
        ctx.setValue(new StringFieldValue("\u3072\u3089\u304c\u306a"));
        assertEquals(Language.ARABIC, ctx.resolveLanguage(new SimpleLinguistics()));
        ctx.setValue(new StringFieldValue("\ud55c\uae00\uacfc"));
        assertEquals(Language.ARABIC, ctx.resolveLanguage(new SimpleLinguistics()));
    }
}
