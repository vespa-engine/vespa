package ai.vespa.llm.generation;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.clients.LlmLocalClientConfig;
import ai.vespa.llm.clients.LocalLLM;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.document.DataType;
import com.yahoo.language.process.FieldGenerator;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LanguageModelFieldGeneratorTest {

    @Test
    public void testGenerateWithOneModel() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder().providerId("languageModel").build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result = generator.generate("hello", context);
        assertEquals("hello hello", result.toString());
    }
    
    @Test
    public void testGenerateWithTwoLanguageModel() {
        LanguageModel languageModel1 = new RepeaterMockLanguageModel(2);
        LanguageModel languageModel2 = new RepeaterMockLanguageModel(3);
        var languageModels = Map.of("languageModel1", languageModel1, "languageModel2", languageModel2);
        
        var config1 = new LanguageModelFieldGeneratorConfig.Builder().providerId("languageModel1").build();
        var generator1 = createGenerator(config1, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result1 = generator1.generate("hello", context);
        assertEquals("hello hello", result1.toString());
    
        var config2 = new LanguageModelFieldGeneratorConfig.Builder().providerId("languageModel2").build();
        var generator2 = createGenerator(config2, languageModels);
        var result2 = generator2.generate("hello", context);
        assertEquals("hello hello hello", result2.toString());
    }

    @Test
    public void testGenerateWithPromptTemplate() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("hello {input}")
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        
        var result1 = generator.generate("world", context);
        assertEquals("hello world hello world", result1.toString());

        var result2 = generator.generate("there", context);
        assertEquals("hello there hello there", result2.toString());
    }

    @Test
    public void testGenerateWithEmptyPromptTemplate() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("")
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate("world", context);
        assertEquals("world world", result1.toString());
    }

    @Test
    public void testGenerateWithStaticPromptTemplate() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("hello")
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate("world", context);
        assertEquals("hello hello", result1.toString());

        var result2 = generator.generate("there", context);
        assertEquals("hello hello", result2.toString());
    }
    
    @Test
    public void testGenerateWithPromptTemplateFile() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/prompt_with_input.txt")))
                .build();
        
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("schema.indexing", DataType.STRING);

        var result1 = generator.generate("world", context);
        assertEquals("hello world hello world", result1.toString());
    }

    @Test
    public void testGenerateWithEmptyTemplateFile() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/empty_prompt.txt")))
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> createGenerator(config, languageModels));
    }

    @Test
    public void testGenerateWithMissingTemplateFile() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/missing_prompt.txt")))
                .build();

        try {
            createGenerator(config, languageModels);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not read prompt template file: src/test/prompts/missing_prompt.txt", e.getMessage());
        }
    }
    
    @Test
    public void testGenerateWithPromptTemplateOverridesPromptTemplateFile() {
        LanguageModel languageModel = new RepeaterMockLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("bye {input}")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/prompt_with_input.txt")))
                .build();

        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate("world", context);
        assertEquals("bye world bye world", result1.toString());
    }
    
    @Test
    public void testGenerateWithLocalLLM() {
        var localLLMPath = "src/test/models/llm/tinyllm.gguf";
        var localLLMConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .model(ModelReference.valueOf(localLLMPath));
        LanguageModel localLLM = new LocalLLM(localLLMConfig.build());

        var languageModels = Map.of("localLLM", localLLM);

        var generatorConfig = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("localLLM")
                .build();

        var generator = createGenerator(generatorConfig, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result = generator.generate("hello", context);
        assertTrue(result.toString().length() > 10);
    }

    private static LanguageModelFieldGenerator createGenerator(LanguageModelFieldGeneratorConfig config, Map<String, LanguageModel> languageModels) {
        ComponentRegistry<LanguageModel> languageModelsRegistry = new ComponentRegistry<>();
        languageModels.forEach((key, value) -> languageModelsRegistry.register(ComponentId.fromString(key), value));
        languageModelsRegistry.freeze();
        return new LanguageModelFieldGenerator(config, languageModelsRegistry);
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
