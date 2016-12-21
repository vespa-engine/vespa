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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author tonyv
 */
public class ExampleSearcherTest {
    @Test
    public void hit_is_added() throws Exception {
        try (Application app = Application.fromApplicationPackage(
                Paths.get("src/test/application"),
                Networking.disable))
        {
            Search search = app.getJDisc("jdisc").search();
            Result result = search.process(ComponentSpecification.fromString("default"), new Query("?query=ignored"));

            Hit hit = result.hits().get(ExampleSearcher.hitId);
            assertNotNull("Hit was not added by ExampleSearcher", hit);

            Object messageFromConfig = "Hello, Vespa!";
            assertThat(hit.getField("message"), is(messageFromConfig));
        }
    }
}
