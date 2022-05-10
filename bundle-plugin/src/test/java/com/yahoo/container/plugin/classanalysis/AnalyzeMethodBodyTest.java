// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.yahoo.container.plugin.classanalysis.sampleclasses.Base;
import com.yahoo.container.plugin.classanalysis.sampleclasses.ClassWithMethod;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Derived;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Dummy;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Fields;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface1;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface3;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Methods;
import org.junit.Test;

import java.io.PrintStream;
import java.util.List;

import static com.yahoo.container.plugin.classanalysis.TestUtilities.analyzeClass;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.name;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that classes used in method bodies are included in the imports list.
 *
 * @author Tony Vaagenes
 */
public class AnalyzeMethodBodyTest {

    @Test
    public void class_of_locals_are_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(Base.class)));
    }

    @Test
    public void class_of_locals_in_static_method_are_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(Derived.class)));
    }

    @Test
    public void field_references_are_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().containsAll(List.of(name(java.util.List.class), name(Fields.class))));
    }

    @Test
    public void class_owning_field_is_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(System.class)));
    }

    @Test
    public void class_containing_method_is_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(PrintStream.class)));
    }

    @Test
    public void element_of_new_multidimensional_array_is_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(Interface1.class)));
    }

    @Test
    public void basic_arrays_are_not_included() {
        assertFalse(analyzeClass(Methods.class).getReferencedClasses().contains("int[]"));
    }

    @Test
    public void container_generic_parameters_are_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(Dummy.class)));
    }

    @Test
    public void functional_interface_usage_is_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(Interface3.class)));
    }

    @Test
    public void class_owning_method_handler_is_included() {
        assertTrue(analyzeClass(Methods.class).getReferencedClasses().contains(name(ClassWithMethod.class)));
    }
}
