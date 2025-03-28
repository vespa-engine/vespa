// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.generation.OnnxEncoderDecoderConfig;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.ModelReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class OnnxEncoderDecoderTest {

    @Test
    public void testGenerator() {
        String vocabPath = "src/test/models/onnx/llm/en.wiki.bpe.vs10000.model";
        String encoderModelPath = "src/test/models/onnx/llm/random_encoder.onnx";
        String decoderModelPath = "src/test/models/onnx/llm/random_decoder.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(encoderModelPath));

        var builder = new OnnxEncoderDecoderConfig.Builder();
        builder.tokenizerModel(ModelReference.valueOf(vocabPath));
        builder.encoderModel(ModelReference.valueOf(encoderModelPath));
        builder.decoderModel(ModelReference.valueOf(decoderModelPath));
        OnnxEncoderDecoder generator = newGenerator(builder.build());

        var options = new OnnxEncoderDecoder.DecoderOptions();
        options.setSearchMethod(OnnxEncoderDecoder.DecoderOptions.SearchMethod.GREEDY);
        options.setMaxLength(10);

        String prompt = "generate some random text";
        String result = generator.generate(prompt, options);

        assertEquals("<unk> linear recruit latest sack annually institutions cert solid references", result);
    }

    private static OnnxEncoderDecoder newGenerator(OnnxEncoderDecoderConfig cfg) {
        return new OnnxEncoderDecoder(new OnnxRuntime(), cfg);
    }

}
