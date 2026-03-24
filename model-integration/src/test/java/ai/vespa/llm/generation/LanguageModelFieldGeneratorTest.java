// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.generation;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.clients.LlmLocalClientConfig;
import ai.vespa.llm.clients.LocalLLM;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.process.FieldGenerator;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LanguageModelFieldGeneratorTest {

    @Test
    public void testGenerateWithOneModel() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel").build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result = generator.generate(StringPrompt.from("hello"), context);
        assertNotNull(result);
        assertEquals("hello hello", result.toString());
    }

    @Test
    public void testGenerateWithTwoLanguageModel() {
        LanguageModel languageModel1 = new RepeatInputLanguageModel(2);
        LanguageModel languageModel2 = new RepeatInputLanguageModel(3);
        var languageModels = Map.of("languageModel1", languageModel1, "languageModel2", languageModel2);

        var config1 = new LanguageModelFieldGeneratorConfig.Builder().providerId("languageModel1").build();
        var generator1 = createGenerator(config1, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result1 = generator1.generate(StringPrompt.from("hello"), context);
        assertNotNull(result1);
        assertEquals("hello hello", result1.toString());

        var config2 = new LanguageModelFieldGeneratorConfig.Builder().providerId("languageModel2").build();
        var generator2 = createGenerator(config2, languageModels);
        var result2 = generator2.generate(StringPrompt.from("hello"), context);
        assertNotNull(result2);
        assertEquals("hello hello hello", result2.toString());
    }

    @Test
    public void testGenerateWithPromptTemplate() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("hello {input}")
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate(StringPrompt.from("world"), context);
        assertNotNull(result1);
        assertEquals("hello world hello world", result1.toString());

        var result2 = generator.generate(StringPrompt.from("there"), context);
        assertNotNull(result2);
        assertEquals("hello there hello there", result2.toString());
    }

    @Test
    public void testGenerateWithEmptyPromptTemplate() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("")
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate(StringPrompt.from("world"), context);
        assertNotNull(result1);
        assertEquals("world world", result1.toString());
    }

    @Test
    public void testGenerateWithStaticPromptTemplate() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("hello")
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate(StringPrompt.from("world"), context);
        assertNotNull(result1);
        assertEquals("hello hello", result1.toString());

        var result2 = generator.generate(StringPrompt.from("there"), context);
        assertNotNull(result2);
        assertEquals("hello hello", result2.toString());
    }

    @Test
    public void testGenerateWithPromptTemplateFile() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/prompt_with_input.txt")))
                .build();

        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate(StringPrompt.from("world"), context);
        assertNotNull(result1);
        assertEquals("hello world hello world", result1.toString());
    }

    @Test
    public void testGenerateWithEmptyTemplateFile() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/empty_prompt.txt")))
                .build();

        assertThrows(IllegalArgumentException.class, () -> createGenerator(config, languageModels));
    }

    @Test
    public void testGenerateWithMissingTemplateFile() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
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
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .promptTemplate("bye {input}")
                .promptTemplateFile(Optional.of(new FileReference("src/test/prompts/prompt_with_input.txt")))
                .build();

        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result1 = generator.generate(StringPrompt.from("world"), context);
        assertNotNull(result1);
        assertEquals("bye world bye world", result1.toString());
    }

    @Test
    public void testGenerateWithResponseJsonSchema() {
        var languageModel = new CaptureCompleteArgsLanguageModel();
        Map<String, LanguageModel> languageModels = Map.of("languageModel", languageModel);

        var customSchema = """
            {
              "type": "object",
              "properties": {
                "doc.text": {
                  "type": "string",
                  "enum": ["electronics", "clothing"]
                }
              },
              "required": ["doc.text"],
              "additionalProperties": false
            }
            """;
        var config = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel")
                .responseJsonSchema(customSchema)
                .build();
        var generator = createGenerator(config, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        generator.generate(StringPrompt.from("hello"), context);

        // Verify the schema was passed to the language model
        assertEquals(
                customSchema,
                languageModel.lastCompleteParams.get(InferenceParameters.OPTION_JSON_SCHEMA).orElseThrow());
    }

    private static final String BIGGER_MODEL_URL = "https://data.vespa-cloud.com/gguf_models/llama-3.2-1b-instruct-q4_k_m.gguf";
    private static final String BIGGER_MODEL_PATH = "src/test/models/llm/llama-3.2-1b-instruct-q4_k_m.gguf";
    private static final String TINY_MODEL_PATH = "src/test/models/llm/tinyllm.gguf";

    private static final Logger log = Logger.getLogger(LanguageModelFieldGeneratorTest.class.getName());

    private static String downloadModelOrFallback() {
        var path = Path.of(BIGGER_MODEL_PATH);
        if (Files.exists(path)) {
            log.info("Using existing model: " + BIGGER_MODEL_PATH);
            return BIGGER_MODEL_PATH;
        }
        log.info("Downloading model from " + BIGGER_MODEL_URL + " ...");
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(URI.create(BIGGER_MODEL_URL)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofFile(path));
            if (response.statusCode() == 200) {
                long size = Files.size(path);
                log.info("Downloaded model to " + BIGGER_MODEL_PATH + " (" + size + " bytes)");
                return BIGGER_MODEL_PATH;
            }
            log.warning("Download failed with HTTP status " + response.statusCode() + ", falling back to " + TINY_MODEL_PATH);
            Files.deleteIfExists(path);
        } catch (IOException | InterruptedException e) {
            log.warning("Download failed: " + e.getMessage() + ", falling back to " + TINY_MODEL_PATH);
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        }
        return TINY_MODEL_PATH;
    }

    @Test
    public void testGenerateWithLocalLLM() {
        var localLLMPath = downloadModelOrFallback();
        var localLLMConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .model(ModelReference.valueOf(localLLMPath));
        var llm = new LocalLLM(localLLMConfig.build());
        try {
            LanguageModel localLLM = llm;

            var languageModels = Map.of("localLLM", localLLM);

            var generatorConfig = new LanguageModelFieldGeneratorConfig.Builder()
                    .providerId("localLLM")
                    .build();

            var generator = createGenerator(generatorConfig, languageModels);
            var context = new FieldGenerator.Context("doc.text", DataType.STRING);
            FieldValue result = null;
            for (int attempt = 0; attempt <= 10; attempt++) {
                result = generator.generate(StringPrompt.from("hello"), context);
                if (result != null && !result.toString().isEmpty()) break;
                log.info("LLM.generate retry: " + attempt);
            }
            assertNotNull(result);
            assertFalse(result.toString().isEmpty());
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    public void testGenerateWithTextOutput() {
        LanguageModel languageModel = new RepeatInputLanguageModel(2);
        var languageModels = Map.of("languageModel", languageModel);

        var generatorConfig = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel").responseFormatType(
                        LanguageModelFieldGeneratorConfig.ResponseFormatType.Enum.TEXT
                ).build();

        var generator = createGenerator(generatorConfig, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);

        var result = generator.generate(StringPrompt.from("hello world"), context);
        assertNotNull(result);
        assertEquals("hello world hello world", result.toString());
    }

    @Test
    public void testGenerateInvalidResponseFormatPolicyDiscard() {
        LanguageModel languageModel = new IvalidJsonLanguageModel();
        var languageModels = Map.of("languageModel", languageModel);

        var generatorConfig = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel").invalidResponseFormatPolicy(
                        LanguageModelFieldGeneratorConfig.InvalidResponseFormatPolicy.Enum.DISCARD
                ).build();

        var generator = createGenerator(generatorConfig, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result = generator.generate(StringPrompt.from("hello world"), context);
        assertEquals(null, result);
    }

    @Test
    public void testGenerateInvalidResponseFormatPolicyFail() {
        LanguageModel languageModel = new IvalidJsonLanguageModel();
        var languageModels = Map.of("languageModel", languageModel);

        var generatorConfig = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel").invalidResponseFormatPolicy(
                        LanguageModelFieldGeneratorConfig.InvalidResponseFormatPolicy.Enum.FAIL
                ).build();

        var generator = createGenerator(generatorConfig, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(StringPrompt.from("hello world"), context));
    }

    @Test
    public void testGenerateInvalidResponseFormatPolicyWarn() {
        var logger = Logger.getLogger(LanguageModelFieldGenerator.class.getName());
        var testLogHandler = new TestLogHandler();
        logger.addHandler(testLogHandler);

        LanguageModel languageModel = new IvalidJsonLanguageModel();
        var languageModels = Map.of("languageModel", languageModel);

        var generatorConfig = new LanguageModelFieldGeneratorConfig.Builder()
                .providerId("languageModel").invalidResponseFormatPolicy(
                        LanguageModelFieldGeneratorConfig.InvalidResponseFormatPolicy.Enum.WARN
                ).build();

        var generator = createGenerator(generatorConfig, languageModels);
        var context = new FieldGenerator.Context("doc.text", DataType.STRING);
        var result = generator.generate(StringPrompt.from("hello world"), context);

        assertEquals(null, result);


        boolean foundWarning = false;

        for (var record : testLogHandler.getRecords()) {
            if (record.getLevel() == Level.WARNING) {
                if (record.getMessage().contains("Failed to parse JSON value")) {
                    foundWarning = true;
                    break;
                }
            }
        }

        assertTrue(foundWarning);
    }

    private static LanguageModelFieldGenerator createGenerator(LanguageModelFieldGeneratorConfig config, Map<String, LanguageModel> languageModels) {
        ComponentRegistry<LanguageModel> languageModelsRegistry = new ComponentRegistry<>();
        languageModels.forEach((key, value) -> languageModelsRegistry.register(ComponentId.fromString(key), value));
        languageModelsRegistry.freeze();
        return new LanguageModelFieldGenerator(config, languageModelsRegistry);
    }

    public static class RepeatInputLanguageModel implements LanguageModel {
        private final int repetitions;

        public RepeatInputLanguageModel(int repetitions) {
            this.repetitions = repetitions;
        }

        @Override
        public List<Completion> complete(Prompt prompt, InferenceParameters params) {
            var stringBuilder = new StringBuilder();

            for (int i = 0; i < repetitions; i++) {
                stringBuilder.append(prompt.asString());
                stringBuilder.append(" ");
            }

            var completionStr = stringBuilder.toString().trim();

            if (params.get(InferenceParameters.OPTION_JSON_SCHEMA).isPresent())
                completionStr = Text.format("""
                       {
                        "doc.text": "%s"
                       }
                       """, completionStr);

            return List.of(Completion.from(completionStr));
        }

        @Override
        public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                        InferenceParameters params,
                                                                        Consumer<Completion> consumer) {
            throw new UnsupportedOperationException();
        }
    }

    public static class IvalidJsonLanguageModel implements LanguageModel {
        @Override
        public List<Completion> complete(Prompt prompt, InferenceParameters params) {
            var invalidJson = "{doc.text:: test347ukdrjfhds";
            return List.of(Completion.from(invalidJson));
        }

        @Override
        public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                        InferenceParameters params,
                                                                        Consumer<Completion> consumer) {
            throw new UnsupportedOperationException();
        }
    }

    private static class CaptureCompleteArgsLanguageModel implements LanguageModel {
        public Prompt lastCompletePrompt;
        public InferenceParameters lastCompleteParams;

        @Override
        public List<Completion> complete(Prompt prompt, InferenceParameters params) {
            this.lastCompletePrompt = prompt;
            this.lastCompleteParams = params;
            return List.of(Completion.from(""));
        }

        @Override
        public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                        InferenceParameters params,
                                                                        Consumer<Completion> consumer) {
            throw new UnsupportedOperationException();
        }
    }

    private static class TestLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}

        public List<LogRecord> getRecords() {
            return records;
        }
    }

}
