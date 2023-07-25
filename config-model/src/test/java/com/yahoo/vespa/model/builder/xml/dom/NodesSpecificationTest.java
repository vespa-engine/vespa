// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import com.yahoo.component.Version;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class NodesSpecificationTest {

    @Test
    void noExplicitGroupLimits() {
        var spec = nodesSpecification("<nodes count='30'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals( 1, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals( 1, spec.maxResources().groups()); // no grouping by default -> implicit max groups is 1
        assertTrue(spec.groupSize().from().isEmpty());
        assertTrue(spec.groupSize().to().isEmpty());
    }

    @Test
    void testGroupSize1() {
        var spec = nodesSpecification("<nodes count='30' group-size='1'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals(30, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals(30, spec.maxResources().groups());
        assertEquals(1, spec.groupSize().from().getAsInt());
        assertEquals(1, spec.groupSize().to().getAsInt());
    }

    @Test
    void testGroupSize3() {
        var spec = nodesSpecification("<nodes count='30' group-size='3'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals(10, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals(10, spec.maxResources().groups());
        assertEquals( 3, spec.groupSize().from().getAsInt());
        assertEquals( 3, spec.groupSize().to().getAsInt());
    }

    @Test
    void testVariableGroupSize1() {
        var spec = nodesSpecification("<nodes count='30' group-size='[15, 30]'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals( 1, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals( 2, spec.maxResources().groups());
        assertEquals(15, spec.groupSize().from().getAsInt());
        assertEquals(30, spec.groupSize().to().getAsInt());
    }

    @Test
    void testVariableGroupSize2() {
        var spec = nodesSpecification("<nodes count='30' group-size='[6, 10]'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals( 3, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals( 5, spec.maxResources().groups());
        assertEquals( 6, spec.groupSize().from().getAsInt());
        assertEquals(10, spec.groupSize().to().getAsInt());
    }

    @Test
    void testGroupSizeLowerBound() {
        var spec = nodesSpecification("<nodes count='30' group-size='[6, ]'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals( 1, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals( 5, spec.maxResources().groups());
        assertEquals( 6, spec.groupSize().from().getAsInt());
        assertTrue(spec.groupSize().to().isEmpty());
    }

    @Test
    void testGroupSizeUpperBound() {
        var spec = nodesSpecification("<nodes count='30' group-size='[, 10]'/>");
        assertEquals(30, spec.minResources().nodes());
        assertEquals( 3, spec.minResources().groups());
        assertEquals(30, spec.maxResources().nodes());
        assertEquals( 30, spec.maxResources().groups());
        assertTrue(spec.groupSize().from().isEmpty());
        assertEquals(10, spec.groupSize().to().getAsInt());
    }

    private NodesSpecification nodesSpecification(String nodesElement) {
        Document nodesXml = XML.getDocument(nodesElement);
        return NodesSpecification.create(false, false, Version.emptyVersion,
                                         new ModelElement(nodesXml.getDocumentElement()),
                                         Optional.empty(), Optional.empty());

    }

}
