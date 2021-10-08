// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

public class VisitorTestCase {

    @Test
    public void testVisitInvalid() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().get().field("invalid");
        inspector.accept(visitor);
        Mockito.verify(visitor).visitInvalid();
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitNix() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().get();
        inspector.accept(visitor);
        Mockito.verify(visitor).visitNix();
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitBool() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setBool(true);
        inspector.accept(visitor);
        Mockito.verify(visitor).visitBool(true);
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitLong() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setLong(123);
        inspector.accept(visitor);
        Mockito.verify(visitor).visitLong(123);
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitDouble() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setDouble(123.0);
        inspector.accept(visitor);
        Mockito.verify(visitor).visitDouble(123.0);
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitStringUtf16() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setString("abc");
        inspector.accept(visitor);
        Mockito.verify(visitor).visitString("abc");
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitStringUtf8() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setString(new byte[] {65,66,67});
        inspector.accept(visitor);
        Mockito.verify(visitor).visitString(new byte[] {65,66,67});
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitData() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setData(new byte[] {1,2,3});
        inspector.accept(visitor);
        Mockito.verify(visitor).visitData(new byte[] {1,2,3});
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitArray() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setArray();
        inspector.accept(visitor);
        Mockito.verify(visitor).visitArray(argThat(sameInstance(inspector)));
        Mockito.verifyNoMoreInteractions(visitor);
    }

    @Test
    public void testVisitObject() {
        Visitor visitor = Mockito.mock(Visitor.class);
        Inspector inspector = new Slime().setObject();
        inspector.accept(visitor);
        Mockito.verify(visitor).visitObject(argThat(sameInstance(inspector)));
        Mockito.verifyNoMoreInteractions(visitor);
    }
}
