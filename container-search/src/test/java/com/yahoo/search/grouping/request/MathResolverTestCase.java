// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class MathResolverTestCase {

    // --------------------------------------------------------------------------------
    //
    // Tests
    //
    // --------------------------------------------------------------------------------

    @Test
    void testOperators() {
        MathResolver resolver = new MathResolver();
        resolver.push(MathResolver.Type.ADD, new LongValue(1));
        resolver.push(MathResolver.Type.ADD, new LongValue(2));
        assertEquals("add(1, 2)",
                resolver.resolve().toString());

        resolver = new MathResolver();
        resolver.push(MathResolver.Type.ADD, new LongValue(1));
        resolver.push(MathResolver.Type.SUB, new LongValue(2));
        assertEquals("sub(1, 2)",
                resolver.resolve().toString());

        resolver = new MathResolver();
        resolver.push(MathResolver.Type.ADD, new LongValue(1));
        resolver.push(MathResolver.Type.DIV, new LongValue(2));
        assertEquals("div(1, 2)",
                resolver.resolve().toString());

        resolver = new MathResolver();
        resolver.push(MathResolver.Type.ADD, new LongValue(1));
        resolver.push(MathResolver.Type.MOD, new LongValue(2));
        assertEquals("mod(1, 2)",
                resolver.resolve().toString());

        resolver = new MathResolver();
        resolver.push(MathResolver.Type.ADD, new LongValue(1));
        resolver.push(MathResolver.Type.MUL, new LongValue(2));
        assertEquals("mul(1, 2)",
                resolver.resolve().toString());
    }

    @Test
    void testOperatorPrecedence() {
        assertResolve("add(add(1, 2), 3)", MathResolver.Type.ADD, MathResolver.Type.ADD);
        assertResolve("add(1, sub(2, 3))", MathResolver.Type.ADD, MathResolver.Type.SUB);
        assertResolve("add(1, div(2, 3))", MathResolver.Type.ADD, MathResolver.Type.DIV);
        assertResolve("add(1, mod(2, 3))", MathResolver.Type.ADD, MathResolver.Type.MOD);
        assertResolve("add(1, mul(2, 3))", MathResolver.Type.ADD, MathResolver.Type.MUL);

        assertResolve("add(sub(1, 2), 3)", MathResolver.Type.SUB, MathResolver.Type.ADD);
        assertResolve("sub(sub(1, 2), 3)", MathResolver.Type.SUB, MathResolver.Type.SUB);
        assertResolve("sub(1, div(2, 3))", MathResolver.Type.SUB, MathResolver.Type.DIV);
        assertResolve("sub(1, mod(2, 3))", MathResolver.Type.SUB, MathResolver.Type.MOD);
        assertResolve("sub(1, mul(2, 3))", MathResolver.Type.SUB, MathResolver.Type.MUL);

        assertResolve("add(div(1, 2), 3)", MathResolver.Type.DIV, MathResolver.Type.ADD);
        assertResolve("sub(div(1, 2), 3)", MathResolver.Type.DIV, MathResolver.Type.SUB);
        assertResolve("div(div(1, 2), 3)", MathResolver.Type.DIV, MathResolver.Type.DIV);
        assertResolve("div(1, mod(2, 3))", MathResolver.Type.DIV, MathResolver.Type.MOD);
        assertResolve("div(1, mul(2, 3))", MathResolver.Type.DIV, MathResolver.Type.MUL);

        assertResolve("add(mod(1, 2), 3)", MathResolver.Type.MOD, MathResolver.Type.ADD);
        assertResolve("sub(mod(1, 2), 3)", MathResolver.Type.MOD, MathResolver.Type.SUB);
        assertResolve("div(mod(1, 2), 3)", MathResolver.Type.MOD, MathResolver.Type.DIV);
        assertResolve("mod(mod(1, 2), 3)", MathResolver.Type.MOD, MathResolver.Type.MOD);
        assertResolve("mod(1, mul(2, 3))", MathResolver.Type.MOD, MathResolver.Type.MUL);

        assertResolve("add(mul(1, 2), 3)", MathResolver.Type.MUL, MathResolver.Type.ADD);
        assertResolve("sub(mul(1, 2), 3)", MathResolver.Type.MUL, MathResolver.Type.SUB);
        assertResolve("div(mul(1, 2), 3)", MathResolver.Type.MUL, MathResolver.Type.DIV);
        assertResolve("mod(mul(1, 2), 3)", MathResolver.Type.MUL, MathResolver.Type.MOD);
        assertResolve("mul(mul(1, 2), 3)", MathResolver.Type.MUL, MathResolver.Type.MUL);

        assertResolve("add(1, sub(div(2, mod(3, mul(4, 5))), 6))",
                MathResolver.Type.ADD, MathResolver.Type.DIV, MathResolver.Type.MOD,
                MathResolver.Type.MUL, MathResolver.Type.SUB);
        assertResolve("add(sub(1, div(mod(mul(2, 3), 4), 5)), 6)",
                MathResolver.Type.SUB, MathResolver.Type.MUL, MathResolver.Type.MOD,
                MathResolver.Type.DIV, MathResolver.Type.ADD);
        assertResolve("add(1, sub(2, div(3, mod(4, mul(5, 6)))))",
                MathResolver.Type.ADD, MathResolver.Type.SUB, MathResolver.Type.DIV,
                MathResolver.Type.MOD, MathResolver.Type.MUL);
        assertResolve("add(sub(div(mod(mul(1, 2), 3), 4), 5), 6)",
                MathResolver.Type.MUL, MathResolver.Type.MOD, MathResolver.Type.DIV,
                MathResolver.Type.SUB, MathResolver.Type.ADD);
    }

    @Test
    void testOperatorSupport() {
        MathResolver resolver = new MathResolver();
        for (MathResolver.Type type : MathResolver.Type.values()) {
            if (type == MathResolver.Type.ADD) {
                continue;
            }
            try {
                resolver.push(type, new AttributeValue("foo"));
            } catch (IllegalArgumentException e) {
                assertEquals("First item in an arithmetic operation must be an addition.", e.getMessage());
            }
        }
    }

    // --------------------------------------------------------------------------------
    //
    // Utilities
    //
    // --------------------------------------------------------------------------------

    private static void assertResolve(String expected, MathResolver.Type... types) {
        MathResolver resolver = new MathResolver();

        int val = 0;
        resolver.push(MathResolver.Type.ADD, new LongValue(++val));
        for (MathResolver.Type type : types) {
            resolver.push(type, new LongValue(++val));
        }

        GroupingExpression exp = resolver.resolve();
        assertNotNull(exp);
        assertEquals(expected, exp.toString());
    }
}
