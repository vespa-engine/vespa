package com.yahoo.searchlib.rankingexpression.integration.ml;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class VariableConverterTestCase {

    @Test
    public void testConversion() {
        byte[] converted = VariableConverter.importVariable("src/test/files/integration/tensorflow/mnist_softmax/saved",
                                                            "Variable_1",
                                                            "tensor(d0[10],d1[1])");
        assertEquals("{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d0\":\"1\",\"d1\":\"0\"},\"value\":0.3759574592113495},{\"address\":{\"d0\":\"2\",\"d1\":\"0\"},\"value\":0.06054411828517914},{\"address\":{\"d0\":\"3\",\"d1\":\"0\"},\"value\":-0.251544713973999},{\"address\":{\"d0\":\"4\",\"d1\":\"0\"},\"value\":0.01795101352035999},{\"address\":{\"d0\":\"5\",\"d1\":\"0\"},\"value\":1.289906740188599},{\"address\":{\"d0\":\"6\",\"d1\":\"0\"},\"value\":-0.1038961559534073},{\"address\":{\"d0\":\"7\",\"d1\":\"0\"},\"value\":0.6367976665496826},{\"address\":{\"d0\":\"8\",\"d1\":\"0\"},\"value\":-1.413674473762512},{\"address\":{\"d0\":\"9\",\"d1\":\"0\"},\"value\":-0.2573896050453186}]}",
                     new String(converted, StandardCharsets.UTF_8));
    }

}
