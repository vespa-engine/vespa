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
import java.util.logging.Logger;

/**
 * Text generator that uses a language model, configured with a config definition.
 *
 * @author glebashnik
 */
public class LanguageModelTextGenerator extends AbstractComponent implements TextGenerator {
    private static final Logger logger = Logger.getLogger(LanguageModelTextGenerator.class.getName());
    private final LanguageModel languageModel;
    
    // Template usually contains {input} placeholder for the dynamic part of the prompt, replaced with the actual input.
    // Templates without {input} are possible, which will ignore the input, making the prompt static.
    // TODO: Consider not allowing templates without {input} to avoid costly errors when users forget to include {input}.
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
                
                if (!promptTemplate.isEmpty()) {  // TODO: Consider throwing an exception if the template is empty.
                    return promptTemplate;
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read prompt template file: " + path, e);
            }
        }

        return DEFAULT_PROMPT_TEMPLATE;
    }

    @Override
    public String generate(Prompt prompt, Context context) {
        var finalPrompt = buildPrompt(prompt);
        var options = new InferenceParameters(s -> "");
        var completions = languageModel.complete(finalPrompt, options);
        var firstCompletion = completions.get(0);
        var generatedText = firstCompletion.text();
        
        if (config.maxLength() > -1) {
            generatedText = generatedText.substring(0, Math.min(config.maxLength(), generatedText.length()));
        }
        
        return generatedText;
    }
    
    private Prompt buildPrompt(Prompt inputPrompt) {
        String finalPrompt = promptTemplate.replace("{input}", inputPrompt.asString());
        return StringPrompt.from(finalPrompt);
    }
}