package com.yahoo.prelude.query;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class InItemTestCase {

    @Test
    void testNumericInItemTracing() {
        Query q = new Query();
        q.getTrace().setLevel(7);
        q.getModel().getQueryTree().setRoot(new NumericInItem("default", Set.of(3L, 5L)));
        q.trace("Trace 1", true, 0);
        List<String> traces = new ArrayList<>();
        for (String trace : q.getContext(true).getTrace().traceNode().descendants(String.class))
            traces.add(trace);

        String expected = """
Trace 1: [
select * from sources * where default in (3, 5)
NUMERIC_IN{
  INT[index="" origin=null]{
    "3"
  }
  INT[index="" origin=null]{
    "5"
  }
}

]""";
        assertEquals(expected, traces.get(1));
    }

    @Test
    void testStringItemTracing() {
        Query q = new Query();
        q.getTrace().setLevel(7);
        q.getModel().getQueryTree().setRoot(new StringInItem("default", Set.of("foo", "bar")));
        q.trace("Trace 1", true, 0);
        List<String> traces = new ArrayList<>();
        for (String trace : q.getContext(true).getTrace().traceNode().descendants(String.class))
            traces.add(trace);
        String expected = """
Trace 1: [
select * from sources * where default in ("bar", "foo")
STRING_IN{
  WORD[fromSegmented=false index="" origin=null segmentIndex=0 stemmed=false words=true]{
    "bar"
  }
  WORD[fromSegmented=false index="" origin=null segmentIndex=0 stemmed=false words=true]{
    "foo"
  }
}

]""";
        assertEquals(expected, traces.get(1));
    }

}
