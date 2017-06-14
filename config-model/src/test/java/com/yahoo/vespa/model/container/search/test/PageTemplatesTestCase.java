// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.test;

import com.yahoo.config.ConfigInstance;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.model.container.search.PageTemplates;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class PageTemplatesTestCase extends junit.framework.TestCase {

    private final static String root="src/test/java/com/yahoo/vespa/model/container/search/test/pages";

    public void testExport() throws IOException {
        List<NamedReader> pageFiles=new ArrayList<>(2);
        pageFiles.add(new NamedReader(root + "/slottingSerp.xml", IOUtils.createReader(root + "/slottingSerp.xml")));
        pageFiles.add(new NamedReader(root + "/richSerp.xml", IOUtils.createReader(root + "/richSerp.xml")));
        pageFiles.add(new NamedReader(root + "/footer.xml", IOUtils.createReader(root + "/footer.xml")));
        pageFiles.add(new NamedReader(root + "/richerSerp.xml", IOUtils.createReader(root + "/richerSerp.xml")));
        pageFiles.add(new NamedReader(root + "/header.xml", IOUtils.createReader(root + "/header.xml")));
        assertEquals(IOUtils.readFile(new File(root, "/pages.cfg")), StringUtilities.implodeMultiline(ConfigInstance.serialize(new PageTemplates(pageFiles).getConfig())));
    }

}
