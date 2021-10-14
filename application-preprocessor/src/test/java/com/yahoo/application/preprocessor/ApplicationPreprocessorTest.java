// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.preprocessor;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public class ApplicationPreprocessorTest {

    @Rule
    public TemporaryFolder outputDir = new TemporaryFolder();

    // Basic test just to check that instantiation and run() works. Unit testing is in config-application-package
    @Test
    public void basic() throws ParserConfigurationException, TransformerException, SAXException, IOException {
        ApplicationPreprocessor preprocessor = new ApplicationPreprocessor(new File("src/test/resources/simple"),
                                                                           Optional.of(outputDir.newFolder()),
                                                                           Optional.empty(),
                                                                           Optional.empty());
        preprocessor.run();
    }

}
