package ai.vespa.llm.generation;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.process.FieldGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Component for generating field values with a language model.
 * 
 * @author glebashnik
 */
public class LanguageModelFieldGenerator extends AbstractComponent implements FieldGenerator {
    private static final Logger logger = Logger.getLogger(LanguageModelFieldGenerator.class.getName());
    private final LanguageModel languageModel;

    private final LanguageModelFieldGeneratorConfig config;
    private final String promptTemplate;
    
    @Inject
    public LanguageModelFieldGenerator(LanguageModelFieldGeneratorConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.languageModel = LanguageModelUtils.findLanguageModel(config.providerId(), languageModels, logger);
        this.config = config;
        this.promptTemplate = loadPromptTemplate(config);
    }

    private String loadPromptTemplate(LanguageModelFieldGeneratorConfig config) {
        if (config.promptTemplate() != null && !config.promptTemplate().isEmpty()) {
            return config.promptTemplate();
        }
        
        if (config.promptTemplateFile().isPresent()) {
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

        return null;
    }
    
    @Override
    public FieldValue generate(String input, Context context) {
        var options = new HashMap<String, String>();
        String jsonSchema = null;
        
        if (config.responseFormatType() == LanguageModelFieldGeneratorConfig.ResponseFormatType.JSON) {
            jsonSchema = FieldGeneratorUtils.generateJsonSchemaForField(context.getDestination(), context.getTargetType());
            options.put(InferenceParameters.OPTION_JSON_SCHEMA, jsonSchema);
        }
        
        var promptString = LanguageModelUtils.generatePrompt(input, promptTemplate, jsonSchema);
        var completions = languageModel.complete(StringPrompt.from(promptString), new InferenceParameters(options::get));
        var firstCompletion = completions.get(0);
        var generatedText = firstCompletion.text();
        FieldValue generatedFieldValue; 
        
        if (config.responseFormatType() == LanguageModelFieldGeneratorConfig.ResponseFormatType.JSON) {
            try {
                generatedFieldValue = FieldGeneratorUtils.parseJsonField(
                        generatedText, context.getDestination(), context.getTargetType());
            } catch (IllegalArgumentException e) {
                generatedFieldValue = switch (config.invalidResponseFormatPolicy()) {
                    case DISCARD -> null;
                    case WARN -> {
                        logger.warning(e.getMessage());
                        yield null;
                    }
                    case FAIL -> throw e;
                };
            }
        } else {
            generatedFieldValue = new StringFieldValue(generatedText);
        }
        
        return generatedFieldValue;
    }
}