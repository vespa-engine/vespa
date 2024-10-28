package com.yahoo.language.process;

import java.util.Map;

public interface Generator {

    // Name of generator when none is explicitly given
    String defaultGeneratorId = "default";

    // An instance of this which throws IllegalStateException if attempted used
    Generator throwsOnUse = new FailingGenerator();

    // Returns this generator instance as a map with the default generator name
    default Map<String, Generator> asMap() {
        return asMap(defaultGeneratorId);
    }

    // Returns this generator instance as a map with the given name
    default Map<String, Generator> asMap(String name) {
        return Map.of(name, this);
    }

    String generate(String prompt);

    class FailingGenerator implements Generator {
        private final String message;

        public FailingGenerator() {
            this("No generator has been configured");
        }

        public FailingGenerator(String message) {
            this.message = message;
        }
        
        public String generate(String prompt) {
            throw new IllegalStateException(message);
        }
    }
}
