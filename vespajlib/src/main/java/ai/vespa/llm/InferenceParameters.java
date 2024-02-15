package ai.vespa.llm;

import ai.vespa.llm.completion.Prompt;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class InferenceParameters {

    private final String apiKey;
    private final Function<String, String> options;

    public InferenceParameters(Function<String, String> options) {
        this(null, options);
    }

    public InferenceParameters(String apiKey, Function<String, String> options) {
        this.apiKey = apiKey;
        this.options = Objects.requireNonNull(options);
    }

    public Optional<String> getApiKey() {
        return Optional.ofNullable(apiKey);
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

}

