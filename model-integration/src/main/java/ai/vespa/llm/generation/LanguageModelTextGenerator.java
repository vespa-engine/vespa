package ai.vespa.llm.generation;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.process.TextGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Configurable component to generate text using a language model.
 * 
 * @author glebashnik
 */
public class LanguageModelTextGenerator extends AbstractComponent implements TextGenerator {
    private static final Logger logger = Logger.getLogger(LanguageModelTextGenerator.class.getName());
    private final LanguageModel languageModel;

    private static final String DEFAULT_PROMPT_TEMPLATE = "{input}";
    private final LanguageModelTextGeneratorConfig config;
    private final String promptTemplate;

    @Inject
    public LanguageModelTextGenerator(LanguageModelTextGeneratorConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.languageModel = LanguageModelUtils.findLanguageModel(config.providerId(), languageModels, logger);
        this.config = config;
        this.promptTemplate = loadPromptTemplate(config);
    }

    private String loadPromptTemplate(LanguageModelTextGeneratorConfig config) {
        if (config.promptTemplate() != null && !config.promptTemplate().isEmpty()) {
            return config.promptTemplate();
        } else if (config.promptTemplateFile().isPresent()) {
            Path path = config.promptTemplateFile().get();

            try {
                String promptTemplate = new String(Files.readAllBytes(path));

                if (promptTemplate.isEmpty()) {
                    throw new IllegalArgumentException("Prompt template file is empty: " + path);
                }
                
                return promptTemplate;
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read prompt template file: " + path, e);
            }
        }

        return DEFAULT_PROMPT_TEMPLATE;
    }

    @Override
    public String generate(Prompt prompt, Context context) {
        var options = new HashMap<String, String>();
        var jsonSchema = "";

        if (config.responseFormatType().equals(InferenceParameters.OPTION_JSON_SCHEMA)) {
            jsonSchema = LanguageModelUtils.generateJsonSchemaForDocumentField(
                    context.getDestination(), context.getDestinationType());
            options.put(InferenceParameters.OPTION_JSON_SCHEMA, jsonSchema);
        }

        var finalPrompt = buildPrompt(prompt, jsonSchema);
        var completions = languageModel.complete(finalPrompt, new InferenceParameters(options::get));
        var firstCompletion = completions.get(0);
        var generatedText = firstCompletion.text();

        return generatedText;
    }

    private Prompt buildPrompt(Prompt inputPrompt, String jsonSchema) {
        var builder = new StringBuilder();
        builder.append(promptTemplate.replace("{input}", inputPrompt.asString()));

        if (jsonSchema != null) {
            builder.append("Your task is to generate output in valid JSON format based on the following schema:")
                    .append("\n")
                    .append(jsonSchema)
                    .append("\n");
        }

        return StringPrompt.from(builder.toString());
    }
}