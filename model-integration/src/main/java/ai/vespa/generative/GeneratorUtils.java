package ai.vespa.generative;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;

import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 *  Provide utilities to implement Generator interface.
 *  It is used by language models as well as other generators.
 */
public class GeneratorUtils {
    public static LanguageModel findLanguageModel(String providerId, ComponentRegistry<LanguageModel> languageModels, Logger log)
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
    
    public static String generate(
            Prompt prompt, LanguageModel languageModel)
    {
        var options = new InferenceParameters(s -> "");
        var completions = languageModel.complete(prompt, options);
        var firstCompletion = completions.get(0);
        return firstCompletion.text();
    }
}
