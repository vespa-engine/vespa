// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Networking;
import com.yahoo.application.container.searchers.AddHitSearcher;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 * @author ollivir
 */
public class ContainerSchemaTest {

    @Test
    void processing_and_rendering_works() throws Exception {
        final String searcherId = AddHitSearcher.class.getName();

        try (JDisc container = containerWithSearch(searcherId)) {
            byte[] rendered = container.search().processAndRender(ComponentSpecification.fromString("mychain"),
                    ComponentSpecification.fromString("XmlRenderer"), new Query(""));
            String renderedAsString = new String(rendered, StandardCharsets.UTF_8);
            assertTrue(renderedAsString.contains(searcherId));
        }
    }

    @Test
    void searching_works() {
        final String searcherId = AddHitSearcher.class.getName();

        try (JDisc container = containerWithSearch(searcherId)) {
            Search searching = container.search();
            Result result = searching.process(ComponentSpecification.fromString("mychain"), new Query(""));
            String hitTitle = result.hits().get(0).getField("title").toString();
            assertEquals(searcherId, hitTitle);
        }
    }

    public JDisc containerWithSearch(String searcherId) {
        return JDisc.fromServicesXml("<container version=\"1.0\">" + //
                "  <search>" + //
                "    <chain id=\"mychain\">" + //
                "      <searcher id=\"" + searcherId + "\"/>" + //
                "    </chain>" + //
                "  </search>" + //
                "  <accesslog type=\"disabled\" />" + //
                "</container>", Networking.disable);
    }

    @Test
    void retrieving_search_from_container_without_search_is_illegal() {
        assertThrows(UnsupportedOperationException.class, () -> {
            try (JDisc container = JDisc.fromServicesXml("<container version=\"1.0\" />", Networking.disable)) {
                container.search(); // throws
            }

        });

    }
}
