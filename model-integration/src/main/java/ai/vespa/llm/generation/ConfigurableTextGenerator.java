package ai.vespa.llm.generation;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.process.TextGenerator;

import java.util.logging.Logger;

/**
 * Text generator based on a language model, configured with a config definition.
 *
 * @author glebashnik
 */
public class ConfigurableTextGenerator extends AbstractComponent implements TextGenerator {
    private static final Logger logger = Logger.getLogger(ConfigurableTextGenerator.class.getName());
    private final LanguageModel languageModel;

    @Inject
    public ConfigurableTextGenerator(TextGeneratorConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.languageModel = LanguageModelUtils.findLanguageModel(config.providerId(), languageModels, logger);
    }
    
    @Override
    public String generate(Prompt prompt, Context context) {
        var options = new InferenceParameters(s -> "");
        var completions = languageModel.complete(prompt, options);
        var firstCompletion = completions.get(0);
        return firstCompletion.text();
    }
}