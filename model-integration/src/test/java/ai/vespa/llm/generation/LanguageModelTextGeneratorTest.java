package ai.vespa.llm.generation;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.yahoo.language.process.TextGenerator;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class LanguageModelTextGeneratorTest {
    @Test
    public void testGenerate() {
        LanguageModel languageModel1 = new RepeaterMockLanguageModel(1);
        LanguageModel languageModel2 = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("mock1", languageModel1, "mock2", languageModel2);
        
        var config1 = new TextGeneratorConfig.Builder().providerId("mock1").build();
        var generator1 = createGenerator(config1, languageModels);
        var context = new TextGenerator.Context("schema.indexing");
        var result1 = generator1.generate(StringPrompt.from("hello"), context);
        assertEquals("hello", result1);
    
        var config2 = new TextGeneratorConfig.Builder().providerId("mock2").build();
        var generator2 = createGenerator(config2, Map.of("mock1", languageModel1, "mock2", languageModel2));
        var result2 = generator2.generate(StringPrompt.from("hello"), context);
        assertEquals("hello hello", result2);
    }

    private static LanguageModelTextGenerator createGenerator(TextGeneratorConfig config, Map<String, LanguageModel> languageModels) {
        ComponentRegistry<LanguageModel> languageModelsRegistry = new ComponentRegistry<>();
        languageModels.forEach((key, value) -> languageModelsRegistry.register(ComponentId.fromString(key), value));
        languageModelsRegistry.freeze();
        return new LanguageModelTextGenerator(config, languageModelsRegistry);
    }
    
    public static class RepeaterMockLanguageModel implements LanguageModel {
        private final int repetitions;
        
        public RepeaterMockLanguageModel(int repetitions) {
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
