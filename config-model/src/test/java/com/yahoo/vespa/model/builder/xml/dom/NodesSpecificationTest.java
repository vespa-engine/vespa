// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import com.yahoo.component.Version;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class NodesSpecificationTest {

    @Test
    void validResources() {
        var spec = nodesSpecification("""
                                      <nodes count='3'>
                                        <resources vcpu='2'
                                                   memory='3Y'
                                                   disk='4tB'
                                                   bandwidth='1ZbPs'
                                                   disk-speed='fast'
                                                   storage-type='local'
                                                   architecture='x86_64'>
                                          <gpu count='1g' memory='3' />
                                        </resources>
                                      </nodes>
                                      """);

        assertEquals(3, spec.minResources().nodes());
        assertEquals(3, spec.maxResources().nodes());

        assertEquals(2, spec.minResources().nodeResources().vcpu(), 1e-9);
        assertEquals(2, spec.maxResources().nodeResources().vcpu(), 1e-9);

        assertEquals(3e15, spec.minResources().nodeResources().memoryGb(), 1e-9);
        assertEquals(3e15, spec.maxResources().nodeResources().memoryGb(), 1e-9);

        assertEquals(4e3, spec.minResources().nodeResources().diskGb(), 1e-9);
        assertEquals(4e3, spec.maxResources().nodeResources().diskGb(), 1e-9);

        assertEquals(1e12, spec.minResources().nodeResources().bandwidthGbps(), 1e-9);
        assertEquals(1e12, spec.maxResources().nodeResources().bandwidthGbps(), 1e-9);

        assertEquals(1 << 30, spec.minResources().nodeResources().gpuResources().count());
        assertEquals(1 << 30, spec.maxResources().nodeResources().gpuResources().count());

        assertEquals(3e-9, spec.minResources().nodeResources().gpuResources().memoryGb(), 1e-12);
        assertEquals(3e-9, spec.maxResources().nodeResources().gpuResources().memoryGb(), 1e-12);

        assertEquals(DiskSpeed.fast, spec.minResources().nodeResources().diskSpeed());
        assertEquals(DiskSpeed.fast, spec.maxResources().nodeResources().diskSpeed());

        assertEquals(StorageType.local, spec.minResources().nodeResources().storageType());
        assertEquals(StorageType.local, spec.maxResources().nodeResources().storageType());

        assertEquals(Architecture.x86_64, spec.minResources().nodeResources().architecture());
        assertEquals(Architecture.x86_64, spec.maxResources().nodeResources().architecture());
    }

    @Test
    void invalidResources() {
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources vcpu='-1' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources vcpu='' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='-1' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='1x' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources vcpu='[-1,]' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources vcpu='[1,0.5]' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='[,-1b]' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='[1mb,999kb]' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='b' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes><resources memory='Yb' /></nodes>"));
        assertThrows(IllegalArgumentException.class,
                     () -> nodesSpecification("<nodes count='[0, 1]'></nodes>"));
    }

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
