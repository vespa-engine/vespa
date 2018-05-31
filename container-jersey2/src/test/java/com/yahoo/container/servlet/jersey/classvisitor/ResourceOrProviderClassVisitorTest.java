// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey.classvisitor;

import com.yahoo.container.servlet.jersey.ResourceOrProviderClassVisitor;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ResourceOrProviderClassVisitorTest {
    @Test
    public void resource_is_detected() throws IOException {
        assert_is_accepted(Resource.class);
    }

    @Test
    public void provider_is_detected() throws IOException {
        assert_is_accepted(Provider.class);
    }

    @Test
    public void inner_class_is_ignored() throws IOException {
        assert_is_ignored(InnerClass.Inner.class);
    }

    @Test
    public void nested_public_class_is_detected() throws IOException {
        assert_is_accepted(NestedClass.Nested.class);
    }

    @Test
    public void nested_non_public_class_is_ignored() throws IOException {
        assert_is_ignored(NonPublicNestedClass.Nested.class);
    }

    @Test
    public void resource_with_multiple_annotations_is_detected() throws IOException {
        assert_is_accepted(ResourceWithMultipleAnnotations.class);
    }

    @Test
    public void interface_is_ignored() throws IOException {
        assert_is_ignored(InterfaceResource.class);
    }

    @Test
    public void abstract_class_is_ignored() throws IOException {
        assert_is_ignored(AbstractResource.class);
    }

    @Test
    public void className_is_equal_to_getName() throws IOException {
        assertThat(analyzeClass(Resource.class).getClassName(), is(Resource.class.getName()));
    }

    private static void assert_is_accepted(Class<?> clazz) throws IOException {
        Assert.assertTrue(className(clazz) + " was not accepted", analyzeClass(clazz).isJerseyClass());
    }

    private static void assert_is_ignored(Class<?> clazz) throws IOException {
        Assert.assertFalse(className(clazz) + " was not ignored", analyzeClass(clazz).isJerseyClass());
    }

    private static ResourceOrProviderClassVisitor analyzeClass(Class<?> clazz) throws IOException {
        return ResourceOrProviderClassVisitor.visit(new ClassReader(className(clazz)));
    }

    private static String className(Class<?> clazz) {
        return clazz.getName();
    }
}
