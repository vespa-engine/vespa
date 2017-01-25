package com.yahoo.example;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.Search;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * @author  Joe Developer
 */
public class SubqueriesSearcherTest {
    @Test
    public void hit_is_added() throws Exception {
        try (Application app = Application.fromApplicationPackage(
                Paths.get("src/test/application"),
                Networking.disable))
        {
            Search search = app.getJDisc("jdisc").search();
            Result result = search.process(ComponentSpecification.fromString("default"), new Query("?query=ignored"));

            assertEquals(3, result.hits().size());
            Hit hit = result.hits().get(0);

            assertEquals(null, hit.getField("summaryfeatures"));  // Summaryfeatures was removed by searcher
            assertEquals(0x100000003L, hit.getField("subqueries(target)"));
        }
    }

}
