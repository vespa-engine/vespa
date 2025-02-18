// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm;

import com.yahoo.api.annotations.Beta;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Parameters for inference to language models. Parameters are typically
 * supplied from searchers or processors and comes from query strings,
 * headers, or other sources. Which parameters are available depends on
 * the language model used.
 *
 * author lesters
 * author glebashnik
 */
@Beta
public class InferenceParameters {
    // Consider replacing these options with fields.
    public static final String OPTION_MODEL = "model";
    public static final String OPTION_TEMPERATURE = "temperature";
    public static final String OPTION_MAX_TOKENS = "maxTokens";
    public static final String OPTION_RESPONSE_FORMAT_TYPE = "response_format_type";
    public static final String OPTION_RESPONSE_FORMAT_JSON_SCHEMA = "json_schema";
    public static final String OPTION_RESPONSE_FORMAT_TEXT = "text";
    
    private String apiKey;
    private String endpoint;
    private final Function<String, String> options;

    public InferenceParameters(Function<String, String> options) {
        this(null, null, options);
    }

    public InferenceParameters(String apiKey, Function<String, String> options) {
        this(apiKey, null, options);
    }

    public InferenceParameters(String apiKey, String endpoint, Function<String, String> options) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.options = Objects.requireNonNull(options);
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Optional<String> getApiKey() {
        return Optional.ofNullable(apiKey);
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Optional<String> getEndpoint() {
        return Optional.ofNullable(endpoint);
    }

    public Optional<String> get(String option) {
        return Optional.ofNullable(options.apply(option));
    }

    public Optional<Double> getDouble(String option) {
        try {
            return Optional.of(Double.parseDouble(options.apply(option)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> getInt(String option) {
        try {
            return Optional.of(Integer.parseInt(options.apply(option)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void ifPresent(String option, Consumer<String> func) {
        get(option).ifPresent(func);
    }
    
    // Creates a new InferenceParameters object with default values for options,
    // i.e. a value in the given default options is used when a corresponding value in the current options is null.
    public InferenceParameters withDefaultOptions(Function<String, String> defaultOptions) {
        Function<String, String> optionsWithDefault = key -> {
            var value = options.apply(key);
            return value != null ? value : defaultOptions.apply(key);
        };
        return new InferenceParameters(apiKey, endpoint, optionsWithDefault);
    }
}

