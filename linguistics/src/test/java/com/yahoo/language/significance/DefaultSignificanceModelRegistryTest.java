// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

import com.yahoo.language.Language;
import com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry;
import org.junit.Test;

import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author MariusArhaug
 */
public class DefaultSignificanceModelRegistryTest {

    @Test
    public void testDefaultSignificanceModelRegistry() {
        HashMap<Language, Path> models = new HashMap<>();

        models.put(Language.ENGLISH, Path.of("src/test/models/en.json"));
        models.put(Language.NORWEGIAN_BOKMAL, Path.of("src/test/models/no.json"));

        DefaultSignificanceModelRegistry defaultSignificanceModelRegistry = new DefaultSignificanceModelRegistry(models);

        var optionalEnglishModel = defaultSignificanceModelRegistry.getModel(Language.ENGLISH);
        var optionalNorwegianModel = defaultSignificanceModelRegistry.getModel(Language.NORWEGIAN_BOKMAL);

        assertTrue(optionalEnglishModel.isPresent());
        assertTrue(optionalNorwegianModel.isPresent());

        var englishModel = optionalEnglishModel.get();
        var norwegianModel = optionalNorwegianModel.get();

        assertTrue( defaultSignificanceModelRegistry.getModel(Language.FRENCH).isEmpty());

        assertNotNull(englishModel);
        assertNotNull(norwegianModel);

        assertEquals(2, englishModel.documentFrequency("test").frequency());
        assertEquals(10, englishModel.documentFrequency("test").corpusSize());

        assertEquals(3, norwegianModel.documentFrequency("nei").frequency());
        assertEquals(20, norwegianModel.documentFrequency("nei").corpusSize());

        assertEquals(1, norwegianModel.documentFrequency("non-existent-word").frequency());
        assertEquals(20, norwegianModel.documentFrequency("non-existent-word").corpusSize());

    }
}
