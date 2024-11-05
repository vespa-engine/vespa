package ai.vespa.generative;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.document.DataType;

import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Generator extends AbstractComponent implements com.yahoo.language.process.Generator {
    private static final Logger log = Logger.getLogger(Generator.class.getName());
    private final LanguageModel languageModel;

    @Inject
    public Generator(GeneratorConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.languageModel = findLanguageModel(config.providerId(), languageModels);
    }
    
    private LanguageModel findLanguageModel(String providerId, ComponentRegistry<LanguageModel> languageModels)
            throws IllegalArgumentException
    {
        if (languageModels.allComponents().isEmpty()) {
            throw new IllegalArgumentException("No language models were found");
        }
        
        if (providerId == null || providerId.isEmpty()) {
            var entry = languageModels.allComponentsById().entrySet().stream().findFirst();
            
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("No language models were found");  // shouldn't happen given check above
            }
            
            log.info("Language model provider was not found in config. " +
                    "Fallback to using first available language model: " + entry.get().getKey());
            
            return entry.get().getValue();
        }
        
        final LanguageModel languageModel = languageModels.getComponent(providerId);
        
        if (languageModel == null) {
            throw new IllegalArgumentException("No component with id '" + providerId + "' was found. " +
                    "Available LLM components are: " + languageModels.allComponentsById().keySet().stream()
                    .map(ComponentId::toString).collect(Collectors.joining(",")));
        }
        
        return languageModel;
    }
    
    @Override
    public String generate(String prompt, Context context, DataType dataType) {
        var options = new InferenceParameters(s -> "");
        var promptObj = StringPrompt.from(prompt);
        var completions = languageModel.complete(promptObj, options);
        var firstCompletion = completions.get(0);
        return firstCompletion.text();
    }
}
