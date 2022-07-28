// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.io.IOUtils;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.config.PageTemplateXMLReader;
import com.yahoo.search.pagetemplates.result.PageTemplatesXmlRenderer;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.text.Utf8;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class ExecutionAbstractTestCase {

    private static final String root="src/test/java/com/yahoo/search/pagetemplates/engine/test/";

    protected PageTemplate importPage(String name) {
        PageTemplate template=new PageTemplateXMLReader().readFile(root + name);
        assertNotNull(template,"Could look up page template '" + name + "'");
        return template;
    }

    protected void assertEqualHitGroups(HitGroup expected,HitGroup actual) {
        assertEquals(expected.size(),actual.size());
        int i=0;
        for (Hit expectedHit : expected.asList()) {
            Hit actualHit=actual.get(i++);
            assertEquals(expectedHit.getId(),actualHit.getId());
            assertEquals(expectedHit.getSource(),actualHit.getSource());
        }
    }

    protected HitGroup createHits(String sourceName,int hitCount) {
        HitGroup source=new HitGroup("source:" + sourceName);
        for (int i=1; i<=hitCount; i++) {
            Hit hit=new Hit(sourceName + "-" + i,1/(double)i);
            hit.setSource(sourceName);
            source.add(hit);
        }
        return source;
    }

    protected void assertRendered(Result result, String resultFileName) {
        assertRendered(result,resultFileName,false);
    }

    protected void assertRendered(Result result, String resultFileName, boolean print) {
        try {
            PageTemplatesXmlRenderer renderer = new PageTemplatesXmlRenderer();
            renderer.init();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            CompletableFuture<Boolean> f = renderer.renderResponse(stream, result, null, null);
            assertTrue(f.get());
            String renderedResult = Utf8.toString(stream.toByteArray());
            if (print)
                System.out.println(renderedResult);
            assertEquals(removeComments(IOUtils.getLines(root + resultFileName)),
                         renderedResult);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String removeComments(List<String> xmlLines) {
        StringBuilder b = new StringBuilder();
        for (String line : xmlLines) {
            if (line.trim().startsWith("<!--")) continue;
            b.append(line).append('\n');
        }
        return b.toString();
    }

}
