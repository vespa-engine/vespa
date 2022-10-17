// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.preprocessor;

import com.yahoo.config.provision.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class ApplicationPreprocessorTest {

    @TempDir
    public File outputDir;

    // Basic test just to check that instantiation and run() works. Unit testing is in config-application-package
    @Test
    void basic() throws IOException {
        ApplicationPreprocessor preprocessor = new ApplicationPreprocessor(new File("src/test/resources/simple"),
                                                                           Optional.of(newFolder(outputDir, "basic")),
                                                                           Optional.empty(),
                                                                           Optional.empty(),
                                                                           Optional.empty(),
                                                                           Tags.empty());
        preprocessor.run();
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
