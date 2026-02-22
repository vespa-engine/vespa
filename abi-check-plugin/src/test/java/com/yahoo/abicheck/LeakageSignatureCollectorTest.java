// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck;

import com.yahoo.abicheck.collector.LeakageSignatureCollector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

public class LeakageSignatureCollectorTest {

    @Test
    public void collects_types_from_methods_fields_and_hierarchy() throws IOException {
        Set<String> types = collectTypes(BasicClass.class);

        // Superclass
        assertThat(types, hasItem("java.lang.Object"));
        // Interfaces
        assertThat(types, hasItem("java.lang.Runnable"));
        assertThat(types, hasItem("java.io.Serializable"));
        // Public method return and parameter types
        assertThat(types, hasItem("java.lang.String"));
        assertThat(types, hasItem("java.lang.Integer"));
        // Public field type
        assertThat(types, hasItem("java.util.List"));
        // Exception type
        assertThat(types, hasItem("java.io.IOException"));
    }

    @Test
    public void collects_generic_type_arguments() throws IOException {
        Set<String> types = collectTypes(GenericClass.class);

        // Generic superclass type argument
        assertThat(types, hasItem("java.lang.String"));
        assertThat(types, hasItem("java.lang.Integer"));
        // Generic field type arguments
        assertThat(types, hasItem("java.util.Map"));
        assertThat(types, hasItem("java.util.List"));
        // Generic method return type argument
        assertThat(types, hasItem("java.lang.Void"));
        // Generic method parameter type argument
        assertThat(types, hasItem("java.util.Set"));
    }

    @Test
    public void excludes_private_and_package_private_members() throws IOException {
        Set<String> types = collectTypes(VisibilityClass.class);

        // Public method type is collected
        assertThat(types, hasItem("java.lang.String"));
        // Protected method type is collected (class is not final)
        assertThat(types, hasItem("java.lang.Integer"));
        // Private method type is NOT collected
        assertThat(types, not(hasItem("java.lang.Double")));
        // Package-private method type is NOT collected
        assertThat(types, not(hasItem("java.lang.Float")));
    }

    @Test
    public void excludes_protected_members_of_final_class() throws IOException {
        Set<String> types = collectTypes(FinalClass.class);

        // Public method type is collected
        assertThat(types, hasItem("java.lang.String"));
        // Protected method type is NOT collected (class is final)
        assertThat(types, not(hasItem("java.lang.Integer")));
    }

    @Test
    public void collects_array_element_types() throws IOException {
        Set<String> types = collectTypes(ArrayClass.class);

        // Array element type, not the array itself
        assertThat(types, hasItem("java.lang.String"));
    }

    @Test
    public void does_not_collect_non_public_classes() throws IOException {
        String className = NonPublicClass.class.getName();
        ClassReader r = new ClassReader(className);
        LeakageSignatureCollector collector = new LeakageSignatureCollector();
        r.accept(collector, 0);

        assertThat(collector.getReferencedTypes().containsKey(className), equalTo(false));
    }

    @Test
    public void accumulates_across_multiple_classes() throws IOException {
        LeakageSignatureCollector collector = new LeakageSignatureCollector();
        new ClassReader(BasicClass.class.getName()).accept(collector, 0);
        new ClassReader(ArrayClass.class.getName()).accept(collector, 0);

        assertThat(collector.getReferencedTypes().size(), equalTo(2));
    }

    private static Set<String> collectTypes(Class<?> clazz) throws IOException {
        ClassReader r = new ClassReader(clazz.getName());
        LeakageSignatureCollector collector = new LeakageSignatureCollector();
        r.accept(collector, 0);
        return collector.getReferencedTypes().get(clazz.getName());
    }

    // --- Test fixture classes ---

    public static class BasicClass implements Runnable, Serializable {
        public List<Object> publicField;

        public String publicMethod(Integer param) { return null; }
        public void methodWithException() throws IOException {}
        @Override public void run() {}
    }

    public static class GenericClass extends java.util.AbstractMap<String, Integer> {
        public Map<String, List<Integer>> genericField;

        public List<Void> genericMethod(Set<String> param) { return null; }

        @Override public Set<Map.Entry<String, Integer>> entrySet() { return null; }
    }

    public static class VisibilityClass {
        public String publicMethod() { return null; }
        protected Integer protectedMethod() { return null; }
        private Double privateMethod() { return null; }
        Float packagePrivateMethod() { return null; }
    }

    public static final class FinalClass {
        public String publicMethod() { return null; }
        protected Integer protectedMethod() { return null; }
    }

    public static class ArrayClass {
        public String[] arrayField;
    }

    static class NonPublicClass {
        public String publicMethod() { return null; }
    }
}
