package ai.vespa.generative;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.yahoo.document.DataType;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class LanguageModelGeneratorTest {
    @Test
    public void testGeneration() {
        LanguageModel languageModel1 = new RepeatMockLanguageModel(1);
        LanguageModel languageModel2 = new RepeatMockLanguageModel(2);
        var languageModels = Map.of("mock1", languageModel1, "mock2", languageModel2);
        
        var config1 = new LanguageModelGeneratorConfig.Builder().providerId("mock1").build();
        var generator1 = createGenerator(config1, languageModels);
        var context = new com.yahoo.language.process.Generator.Context("schema.indexing");
        var result1 = generator1.generate("hello", context);
        assertEquals("hello", result1);
    
        var config2 = new LanguageModelGeneratorConfig.Builder().providerId("mock2").build();
        var generator2 = createGenerator(config2, Map.of("mock1", languageModel1, "mock2", languageModel2));
        var result2 = generator2.generate("hello", context);
        assertEquals("hello hello", result2);
    }

    private static LanguageModelGenerator createGenerator(LanguageModelGeneratorConfig config, Map<String, LanguageModel> languageModels) {
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        languageModels.forEach((key, value) -> models.register(ComponentId.fromString(key), value));
        models.freeze();
        return new LanguageModelGenerator(config, models);
    }
    
    public static class RepeatMockLanguageModel implements LanguageModel {
        private final int repetitions;
        
        public RepeatMockLanguageModel(int repetitions) {
            this.repetitions = repetitions;
        }
        
        @Override
        public List<Completion> complete(Prompt prompt, InferenceParameters params) {
            var stringBuilder = new StringBuilder();

            for (int i = 0; i < repetitions; i++) {
                stringBuilder.append(prompt.asString());
                stringBuilder.append(" ");
            }
            
            return List.of(Completion.from(stringBuilder.toString().trim()));
        }

        @Override
        public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                        InferenceParameters params,
                                                                        Consumer<Completion> consumer) {
            throw new UnsupportedOperationException();
        }
    }
}
