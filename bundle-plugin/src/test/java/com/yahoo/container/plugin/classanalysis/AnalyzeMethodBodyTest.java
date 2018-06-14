// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.yahoo.container.plugin.classanalysis.sampleclasses.Base;
import com.yahoo.container.plugin.classanalysis.sampleclasses.ClassWithMethod;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Derived;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Dummy;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Fields;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface1;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Methods;
import org.junit.Test;

import java.io.PrintStream;

import static com.yahoo.container.plugin.classanalysis.TestUtilities.analyzeClass;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.name;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * Tests that classes used in method bodies are included in the imports list.
 *
 * @author tonytv
 */
public class AnalyzeMethodBodyTest {
    @Test
    public void require_that_class_of_locals_are_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(Base.class)));
    }

    @Test
    public void require_that_class_of_locals_in_static_method_are_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(Derived.class)));
    }

    @Test
    public void require_that_field_references_are_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(),
                allOf(hasItem(name(java.util.List.class)), hasItem(name(Fields.class))));
    }

    @Test
    public void require_that_class_owning_field_is_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(System.class)));
    }

    @Test
    public void require_that_class_containing_method_is_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(PrintStream.class)));
    }

    @Test
    public void require_that_element_of_new_multidimensional_array_is_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(Interface1.class)));
    }

    @Test
    public void require_that_basic_arrays_are_not_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), not(hasItem("int[]")));
    }

    @Test
    public void require_that_container_generic_parameters_are_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(Dummy.class)));
    }

    @Test
    public void require_that_class_owning_method_handler_is_included() {
        assertThat(analyzeClass(Methods.class).getReferencedClasses(), hasItem(name(ClassWithMethod.class)));
    }
}
