package ai.vespa.llm;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.config.ModelReference;
import com.yahoo.llm.GeneratorConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class GeneratorTest {

    @Test
    public void testGenerator() {
        String vocabPath = "src/test/models/onnx/llm/en.wiki.bpe.vs10000.model";
        String encoderModelPath = "src/test/models/onnx/llm/random_encoder.onnx";
        String decoderModelPath = "src/test/models/onnx/llm/random_decoder.onnx";
        assumeTrue(OnnxEvaluator.isRuntimeAvailable(encoderModelPath));

        GeneratorConfig.Builder builder = new GeneratorConfig.Builder();
        builder.tokenizerModel(ModelReference.valueOf(vocabPath));
        builder.encoderModel(ModelReference.valueOf(encoderModelPath));
        builder.decoderModel(ModelReference.valueOf(decoderModelPath));
        Generator generator = new Generator(builder.build());

        GeneratorOptions options = new GeneratorOptions();
        options.setSearchMethod(GeneratorOptions.SearchMethod.GREEDY);
        options.setMaxLength(10);

        String prompt = "generate some random text";
        String result = generator.generate(prompt, options);

        assertEquals("<unk> linear recruit latest sack annually institutions cert solid references", result);
    }

}
