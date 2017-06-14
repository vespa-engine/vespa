// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.ConfigInstance;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.UnprocessingSearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests VDS+streaming configuration deriving
 *
 * @author bratseth
 */
public class MailTestCase extends AbstractExportingTestCase {

    @Test
    public void testMail() throws IOException, ParseException {
        String dir = "src/test/derived/mail/";
        SearchBuilder sb = new SearchBuilder();
        sb.importFile(dir + "mail.sd");
        assertCorrectDeriving(sb, dir);
    }

    @Test
    public void testMailDocumentsonlyDeriving() {
        String root = "src/test/derived/mail/";
        File toDir = new File("temp/documentderiver/");
        if (!toDir.exists()) {
            toDir.mkdir();
        }
        List<String> files = new ArrayList<>();
        files.add(root + "mail.sd");
        Deriver.deriveDocuments(files, toDir.getPath());
        try {
            assertEqualFiles(root + "onlydoc/documentmanager.cfg",
                             toDir.getPath() + "/documentmanager.cfg");
        } catch (IOException e) {
            throw new RuntimeException("Exception while comparing files", e);
        }
    }

}
