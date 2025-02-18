package ai.vespa.llm.generation;

import ai.vespa.json.Json;
import ai.vespa.llm.LanguageModel;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.document.DataType;

import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 *  Provide utilities for working with language models.
 *  
 *  @author glebashnik
 */
public class LanguageModelUtils {
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

     public static String generateJsonSchemaForDocumentField(String fieldName, DataType fieldType) {
        var schema = Json.Builder.newObject()
                .set("type", "object");
        
        var properties = schema.setObject("properties");
        var field = properties.setObject(fieldName);

        if (fieldType.equals(DataType.STRING)) {
            field.set("type", "string");
        } if (fieldType.equals(DataType.INT)) {
            field.set("type", "integer");
        } else if (fieldType.equals(DataType.DOUBLE)) {
            field.set("type", "double");
        } else if (fieldType.equals(DataType.FLOAT)) {
            field.set("type", "float");
        } else if (fieldType.equals(DataType.BOOL)) {
            field.set("type", "boolean");
        } else if (fieldType.equals(DataType.STRING)) {
            field.set("type", "string");
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + fieldType);
        }
        
        var required = schema.setArray("required");
        required.add(fieldName);
        schema.set("additionalProperties", false);
        
        return schema.build().toJson(true);
    }

    public static String convertJsonSchemaToGrammar(String jsonSchema) {
        return "";
    }
}
