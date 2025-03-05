package ai.vespa.llm.generation;

import ai.vespa.llm.LanguageModel;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;

import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 *  Provide utilities for working with language models.
 *  
 *  @author glebashnik
 */
class LanguageModelUtils {
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

    public static String generatePrompt(String input, String promptTemplate, String jsonSchema) {
        if (promptTemplate != null) {
            var prompt = promptTemplate;

            if (prompt.contains("{input}")) {
                if (input != null) {
                    prompt = prompt.replace("{input}", input);
                } else {
                    throw new IllegalArgumentException("There no input to add to the prompt: %s"
                            .formatted(prompt));
                }
            }

            if (prompt.contains("{jsonSchema}")) {
                if (jsonSchema != null) {
                    prompt = prompt.replace("{jsonSchema}", jsonSchema);
                } else {
                    throw new IllegalArgumentException("There no JSON schema to add to the prompt: %s"
                            .formatted(prompt));
                }
            }

            return prompt;
        }

        if (input != null) {
            var prompt = input;

            if (prompt.contains("{jsonSchema}")) {
                if (jsonSchema != null) {
                    prompt = prompt.replace("{jsonSchema}", jsonSchema);
                } else {
                    throw new IllegalArgumentException("There no JSON schema to add to the prompt: %s"
                            .formatted(prompt));
                }
            }

            return prompt;
        }

        throw new IllegalArgumentException("Can't construct a prompt without a prompt template or an input text.");
    }
}
