package ai.vespa.generative;

import ai.vespa.llm.LanguageModel;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.document.DataType;

import java.util.logging.Logger;

/**
 * A generator that uses a language model to generate text.
 * Unlike using a language model directly, this is supposed to be extended with configurable parameters, 
 * e.g. prompt template, postprocessors, etc.
 */
public  class LanguageModelGenerator extends AbstractComponent implements com.yahoo.language.process.Generator {
    private static final Logger logger = Logger.getLogger(LanguageModelGenerator.class.getName());
    private final LanguageModel languageModel;

    @Inject
    public LanguageModelGenerator(LanguageModelGeneratorConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.languageModel = GeneratorUtils.findLanguageModel(config.providerId(), languageModels, logger);
    }
    
    @Override
    public String generate(String prompt, Context context, DataType dataType) {
        return GeneratorUtils.generate(prompt, languageModel);
    }
}
