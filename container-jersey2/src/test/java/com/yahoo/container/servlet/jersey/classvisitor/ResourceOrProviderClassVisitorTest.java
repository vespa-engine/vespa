// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey.classvisitor;

import com.yahoo.container.servlet.jersey.ResourceOrProviderClassVisitor;
import org.junit.Test;
import org.objectweb.asm.ClassReader;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ResourceOrProviderClassVisitorTest {
    @Test
    public void resource_is_detected() throws Exception {
        assert_is_accepted(com.yahoo.container.servlet.jersey.classvisitor.Resource.class);
    }

    @Test
    public void provider_is_detected() throws Exception {
        assert_is_accepted(com.yahoo.container.servlet.jersey.classvisitor.Provider.class);
    }

    @Test
    public void inner_class_is_ignored() throws Exception {
        assert_is_ignored(com.yahoo.container.servlet.jersey.classvisitor.InnerClass.Inner.class);
    }

    @Test
    public void nested_public_class_is_detected() throws Exception {
        assert_is_accepted(com.yahoo.container.servlet.jersey.classvisitor.NestedClass.Nested.class);
    }

    @Test
    public void nested_non_public_class_is_ignored() throws Exception {
        assert_is_ignored(com.yahoo.container.servlet.jersey.classvisitor.NonPublicNestedClass.Nested.class);
    }

    @Test
    public void resource_with_multiple_annotations_is_detected() throws Exception {
        assert_is_accepted(com.yahoo.container.servlet.jersey.classvisitor.ResourceWithMultipleAnnotations.class);
    }

    @Test
    public void interface_is_ignored() throws Exception {
        assert_is_ignored(com.yahoo.container.servlet.jersey.classvisitor.InterfaceResource.class);
    }

    @Test
    public void abstract_class_is_ignored() throws Exception {
        assert_is_ignored(com.yahoo.container.servlet.jersey.classvisitor.AbstractResource.class);
    }

    @Test
    public void className_is_equal_to_getName() throws Exception {
        assertThat(analyzeClass(com.yahoo.container.servlet.jersey.classvisitor.Resource.class).getClassName(), is(com.yahoo.container.servlet.jersey.classvisitor.Resource.class.getName()));
    }

    public void assert_is_accepted(Class<?> clazz) throws Exception {
        assertTrue(className(clazz) + " was not accepted",
                analyzeClass(clazz).isJerseyClass());
    }

    public void assert_is_ignored(Class<?> clazz) throws Exception {
        assertFalse(className(clazz) + " was not ignored",
                analyzeClass(clazz).isJerseyClass());
    }

    public ResourceOrProviderClassVisitor analyzeClass(Class<?> clazz) throws Exception {
        return ResourceOrProviderClassVisitor.visit(new ClassReader(className(clazz)));
    }

    public String className(Class<?> clazz) {
        return clazz.getName();
    }
}


