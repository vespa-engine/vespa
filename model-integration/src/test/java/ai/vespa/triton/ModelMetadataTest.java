// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.triton.TritonOnnxClient.ModelMetadata;
import com.yahoo.tensor.TensorType;
import inference.GrpcService.ModelMetadataResponse.TensorMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author glebashnik
 */
class ModelMetadataTest {

    private static final TensorMetadata FIRST_INPUT = tensor("first_input", "FP32", 2);
    private static final TensorMetadata SECOND_INPUT = tensor("second/input:0", "INT64", -1, 4);
    private static final TensorMetadata OUTPUT = tensor("path/to/output:0", "FP32", -1);

    @Test
    void inputsAndOutputsAreKeyedByVespaIdentifier() {
        var metadata = ModelMetadata.of(List.of(FIRST_INPUT, SECOND_INPUT), List.of(OUTPUT));

        assertEquals(2, metadata.inputs.size());
        assertEquals(TensorType.fromSpec("tensor<float>(d0[2])"), metadata.inputs.get("first_input"));
        assertEquals(TensorType.fromSpec("tensor(d0[],d1[4])"), metadata.inputs.get("second_input_0"));
        assertEquals(1, metadata.outputs.size());
        assertEquals(TensorType.fromSpec("tensor<float>(d0[])"), metadata.outputs.get("path_to_output_0"));
    }

    @Test
    void findInputResolvesBothOnnxNameAndVespaIdentifier() {
        var metadata = ModelMetadata.of(List.of(FIRST_INPUT, SECOND_INPUT), List.of(OUTPUT));

        assertSame(FIRST_INPUT, metadata.findInput("first_input"));
        assertSame(SECOND_INPUT, metadata.findInput("second/input:0"));
        assertSame(SECOND_INPUT, metadata.findInput("second_input_0"));
    }

    @Test
    void findInputFailsOnUnknownName() {
        var metadata = ModelMetadata.of(List.of(FIRST_INPUT, SECOND_INPUT), List.of(OUTPUT));

        var e = assertThrows(TritonOnnxClient.TritonException.class, () -> metadata.findInput("no_such_input"));
        assertEquals("No matching input type found for no_such_input", e.getMessage());
    }

    private static TensorMetadata tensor(String name, String datatype, long... shape) {
        var builder = TensorMetadata.newBuilder().setName(name).setDatatype(datatype);
        for (long dim : shape) {
            builder.addShape(dim);
        }
        return builder.build();
    }

}
