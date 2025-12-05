package com.yahoo.search.rendering;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.slime.SlimeDataSink;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeInserter;
import com.yahoo.slime.SlimeUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InspectorDataSinkDelegateTest {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                () -> "Expected " + SlimeUtils.toJson(expected) +
                        " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testNestedMapConversion() {
        var input = SlimeUtils.jsonToSlime("[{key:'outer', value:[{key:'inner', value:1}]}]");
        Inspector inspector = new SlimeAdapter(input.get());
        var output = new Slime();
        DataSink outputSink = new SlimeDataSink(new SlimeInserter(output));
        var settings = new ConversionSettings(true, true, true, true);
        new InspectorDataSinkDelegate(outputSink, settings).emitInspector(inspector);
        var expected = SlimeUtils.jsonToSlime("{outer:{inner:1}}");
        assertSlime(expected, output);
    }

    @Test
    public void testWsetConversion() {
        var input = SlimeUtils.jsonToSlime("[{item:'a', weight: 5}, {item:'b', weight: 2}]");
        Inspector inspector = new SlimeAdapter(input.get());
        var output = new Slime();
        DataSink outputSink = new SlimeDataSink(new SlimeInserter(output));
        var settings = new ConversionSettings(true, true, true, true);
        new InspectorDataSinkDelegate(outputSink, settings).emitInspector(inspector);
        var expected = SlimeUtils.jsonToSlime("{a:5, b:2}");
        assertSlime(expected, output);
    }
}
