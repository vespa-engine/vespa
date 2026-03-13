// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.simple.SimpleLinguistics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Simon Thoresen Hult
 */
public class ExecutionContextTestCase {

    @Test
    public void requireThatValueCanBeSet() {
        ExecutionContext ctx = new ExecutionContext();
        FieldValue val = new StringFieldValue("foo");
        ctx.setCurrentValue(val);
        assertSame(val, ctx.getCurrentValue());
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
        ctx.setCurrentValue(new StringFieldValue("foo"));
        ctx.clear();
        assertNull(ctx.getCurrentValue());
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
        ctx.setCurrentValue(new StringFieldValue("\u3072\u3089\u304c\u306a"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(new SimpleLinguistics()));
        // Detected language is cached; clear() resets it for the next statement
        ctx.clear();
        ctx.setCurrentValue(new StringFieldValue("\ud55c\uae00\uacfc"));
        assertEquals(Language.KOREAN, ctx.resolveLanguage(new SimpleLinguistics()));
    }

    @Test
    public void requireThatDetectedLanguageIsCached() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setCurrentValue(new StringFieldValue("\u3072\u3089\u304c\u306a"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(new SimpleLinguistics()));
        // Second call within same statement returns cached result
        ctx.setCurrentValue(new StringFieldValue("\ud55c\uae00\uacfc"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(new SimpleLinguistics()));
    }

    @Test
    public void requireThatClearResetsDetectedLanguage() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setCurrentValue(new StringFieldValue("\u3072\u3089\u304c\u306a"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(new SimpleLinguistics()));
        ctx.clear();
        assertEquals(Language.UNKNOWN, ctx.getLanguage());
    }

    @Test
    public void requireThatDetectedLanguageIsCachedAcrossClear() {
        var detector = mock(Detector.class);
        when(detector.detect(anyString(), isNull())).thenReturn(new Detection(Language.JAPANESE, "UTF-8", false));
        var linguistics = mock(Linguistics.class);
        when(linguistics.getDetector()).thenReturn(detector);

        var ctx = new ExecutionContext();
        ctx.setCurrentValue(new StringFieldValue("text"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(linguistics));

        ctx.clear();
        ctx.setCurrentValue(new StringFieldValue("text"));
        assertEquals(Language.JAPANESE, ctx.resolveLanguage(linguistics));

        verify(detector, times(1)).detect(anyString(), isNull());
    }

    @Test
    public void requireThatExplicitLanguagePreventsDetection() {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setLanguage(Language.ARABIC);
        ctx.setCurrentValue(new StringFieldValue("\u3072\u3089\u304c\u306a"));
        assertEquals(Language.ARABIC, ctx.resolveLanguage(new SimpleLinguistics()));
        ctx.setCurrentValue(new StringFieldValue("\ud55c\uae00\uacfc"));
        assertEquals(Language.ARABIC, ctx.resolveLanguage(new SimpleLinguistics()));
    }
}
