package com.yahoo.container.jdisc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.container.jdisc.ConfiguredApplication.ordered;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfiguredApplicationTest {

    @Test
    void testSorting() {
        class A { @Override public String toString() { return getClass().getSimpleName(); } }
        class B extends A { }
        class C extends B { }
        class D extends B { }

        A a = new A(), b = new B(), c = new C(), d = new D(), e = new D() { @Override public String toString() { return "E"; } };
        List<A> s = List.of(a, b, c, d, e);
        assertEquals(List.of(a, b, c, d, e), ordered(s, A.class, B.class, C.class, D.class));
        assertEquals(List.of(d, e, c, b, a), ordered(s, D.class, C.class, B.class, A.class));
        assertEquals(List.of(e, c, a, b, d), ordered(s, e.getClass(), C.class, A.class));
        assertEquals(List.of(d, e, b, c, a), ordered(s, D.class, B.class));
    }

}
