package ai.vespa.llm.generation;

import ai.vespa.json.Json;
import ai.vespa.llm.LanguageModel;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

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

    public static String generateJsonSchemaForField(String destination, DataType targetType) {
        var schema = Json.Builder.newObject()
                .set("type", "object");

        var properties = schema.setObject("properties");
        var field = properties.setObject(destination);

        if (targetType.equals(DataType.STRING)) {
            field.set("type", "string");
        } else if (targetType.equals(DataType.getArray(DataType.STRING))) {
            field.set("type", "array");
            field.setObject("items").set("type", "string");
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + targetType);
        }

        var required = schema.setArray("required");
        required.add(destination);
        schema.set("additionalProperties", false);

        return schema.build().toJson(true);
    }
    
    public static FieldValue jsonToFieldType(String jsonText, String destination, DataType targetType) {
        var jsonData = Json.of(jsonText);
        var jsonField = jsonData.field(destination);
        
        if (targetType.equals(DataType.STRING) && jsonField.isString()) {
            return new StringFieldValue(jsonField.asString());
        }
        
        if (targetType.equals(DataType.getArray(DataType.STRING)) && jsonField.isString()) {
            var arrayFieldValue = new Array<>(DataType.getArray(DataType.STRING));
            
            for (var jsonItem : jsonField) {
                arrayFieldValue.add(new StringFieldValue(jsonItem.asString()));
            }
            
            return arrayFieldValue;
        }
        
        throw new IllegalArgumentException("Can't parse the following generated text as %s: %s".formatted(targetType.getName(), jsonText));
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

        throw new IllegalArgumentException("Can't construct a prompt without a prompt template or an input");
    }
}
