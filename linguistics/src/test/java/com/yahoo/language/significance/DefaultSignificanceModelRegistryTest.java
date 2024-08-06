// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

import com.yahoo.language.Language;
import com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author MariusArhaug
 */
public class DefaultSignificanceModelRegistryTest {

    @Test
    public void testDefaultSignificanceModelRegistry() {
        List<Path> models = new ArrayList<>();

        models.add(Path.of("src/test/models/docv1.json"));
        models.add(Path.of("src/test/models/docv2.json"));

        DefaultSignificanceModelRegistry defaultSignificanceModelRegistry = new DefaultSignificanceModelRegistry(models);

        var optionalEnglishModel = defaultSignificanceModelRegistry.getModel(Language.ENGLISH);
        var optionalNorwegianModel = defaultSignificanceModelRegistry.getModel(Language.NORWEGIAN_BOKMAL);

        assertTrue(optionalEnglishModel.isPresent());
        assertTrue(optionalNorwegianModel.isPresent());

        var englishModel = optionalEnglishModel.get();
        var norwegianModel = optionalNorwegianModel.get();

        assertTrue( defaultSignificanceModelRegistry.getModel(Language.GERMAN).isEmpty());

        assertNotNull(englishModel);
        assertNotNull(norwegianModel);

        assertEquals("test::2", englishModel.getId());
        assertEquals("test::2", norwegianModel.getId());

        assertEquals(4, englishModel.documentFrequency("test").frequency());
        assertEquals(14, englishModel.documentFrequency("test").corpusSize());

        assertEquals(3, norwegianModel.documentFrequency("nei").frequency());
        assertEquals(20, norwegianModel.documentFrequency("nei").corpusSize());

        assertEquals(1, norwegianModel.documentFrequency("non-existent-word").frequency());
        assertEquals(20, norwegianModel.documentFrequency("non-existent-word").corpusSize());

    }

    @Test
    public void testDefaultSignificanceModelRegistryWithUnknownLanguage() {
        List<Path> models = new ArrayList<>();

        models.add(Path.of("src/test/models/docv2.json"));

        DefaultSignificanceModelRegistry defaultSignificanceModelRegistry = new DefaultSignificanceModelRegistry(models);

        assertTrue(defaultSignificanceModelRegistry.getModel(Language.ENGLISH).isPresent());
        assertTrue(defaultSignificanceModelRegistry.getModel(Language.FRENCH).isPresent());
        assertTrue(defaultSignificanceModelRegistry.getModel(Language.UNKNOWN).isPresent());

        var frenchModel = defaultSignificanceModelRegistry.getModel(Language.FRENCH).get();
        var unknownModel = defaultSignificanceModelRegistry.getModel(Language.UNKNOWN).get();

        assertEquals("test::2", frenchModel.getId());
        assertEquals("test::2", unknownModel.getId());

        assertEquals(1, frenchModel.documentFrequency("non-existent-word").frequency());
        assertEquals(20, frenchModel.documentFrequency("non-existent-word").corpusSize());

        assertEquals(1, unknownModel.documentFrequency("non-existent-word").frequency());
        assertEquals(20, unknownModel.documentFrequency("non-existent-word").corpusSize());

        assertEquals(6, frenchModel.documentFrequency("bonjour").frequency());
        assertEquals(20, frenchModel.documentFrequency("bonjour").corpusSize());
        assertEquals(6, unknownModel.documentFrequency("bonjour").frequency());
        assertEquals(20, unknownModel.documentFrequency("bonjour").corpusSize());

    }

    @Test
    public void testDefaultSignificanceModelRegistryWithZSTDecompressing() {
        List<Path> models = new ArrayList<>();

        models.add(Path.of("src/test/models/docv1.json.zst"));

        DefaultSignificanceModelRegistry defaultSignificanceModelRegistry = new DefaultSignificanceModelRegistry(models);

        var optionalEnglishModel = defaultSignificanceModelRegistry.getModel(Language.ENGLISH);
        assertTrue(optionalEnglishModel.isPresent());

        var englishModel = optionalEnglishModel.get();

        assertTrue( defaultSignificanceModelRegistry.getModel(Language.FRENCH).isEmpty());
        assertNotNull(englishModel);
        assertEquals("test::1", englishModel.getId());
        assertEquals(2, englishModel.documentFrequency("test").frequency());
        assertEquals(10, englishModel.documentFrequency("test").corpusSize());

    }

    @Test
    public void testDefaultSignificanceModelRegistryInOppsiteOrder() {

        List<Path> models = new ArrayList<>();

        models.add(Path.of("src/test/models/docv2.json"));
        models.add(Path.of("src/test/models/docv1.json"));

        DefaultSignificanceModelRegistry defaultSignificanceModelRegistry = new DefaultSignificanceModelRegistry(models);

        var optionalEnglishModel = defaultSignificanceModelRegistry.getModel(Language.ENGLISH);
        var optionalNorwegianModel = defaultSignificanceModelRegistry.getModel(Language.NORWEGIAN_BOKMAL);

        assertTrue(optionalEnglishModel.isPresent());
        assertTrue(optionalNorwegianModel.isPresent());

        var englishModel = optionalEnglishModel.get();
        var norwegianModel = optionalNorwegianModel.get();

        assertNotNull(englishModel);
        assertNotNull(norwegianModel);

        assertEquals("test::1", englishModel.getId());
        assertEquals("test::2", norwegianModel.getId());

        assertEquals(2, englishModel.documentFrequency("test").frequency());
        assertEquals(10, englishModel.documentFrequency("test").corpusSize());

        assertEquals(3, norwegianModel.documentFrequency("nei").frequency());
        assertEquals(20, norwegianModel.documentFrequency("nei").corpusSize());

        assertEquals(1, norwegianModel.documentFrequency("non-existent-word").frequency());
        assertEquals(20, norwegianModel.documentFrequency("non-existent-word").corpusSize());
    }
}
