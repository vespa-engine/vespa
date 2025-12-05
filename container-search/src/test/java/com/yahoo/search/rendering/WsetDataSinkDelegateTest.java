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

public class WsetDataSinkDelegateTest {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                () -> "Expected " + SlimeUtils.toJson(expected) +
                        " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testWsetDataSinkDelegate() {
        var input = SlimeUtils.jsonToSlime("[{item:'a', weight: 5}, {item:'b', weight: 2}]");
        Inspector inspector = new SlimeAdapter(input.get());
        var output = new Slime();
        DataSink outputSink = new SlimeDataSink(new SlimeInserter(output));
        DataSink mySink = new WsetDataSinkDelegate(outputSink);
        inspector.emit(mySink);
        var expected = SlimeUtils.jsonToSlime("{a:5, b:2}");
        assertSlime(expected, output);
    }

}
