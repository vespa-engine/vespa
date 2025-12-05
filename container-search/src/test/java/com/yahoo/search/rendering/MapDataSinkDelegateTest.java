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

public class MapDataSinkDelegateTest {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                () -> "Expected " + SlimeUtils.toJson(expected) +
                        " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testMapDataSinkDelegate() {
        var input = SlimeUtils.jsonToSlime("[{key:'id', value: 5}, {key:'type', value: 'number'}]");
        Inspector inspector = new SlimeAdapter(input.get());
        var output = new Slime();
        DataSink outputSink = new SlimeDataSink(new SlimeInserter(output));
        DataSink mySink = new MapDataSinkDelegate(outputSink);
        inspector.emit(mySink);
        var expected = SlimeUtils.jsonToSlime("{id:5, type:'number'}");
        assertSlime(expected, output);
    }


}
