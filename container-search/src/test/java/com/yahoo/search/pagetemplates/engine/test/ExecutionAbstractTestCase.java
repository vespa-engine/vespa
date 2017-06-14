// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.io.IOUtils;
import com.yahoo.prelude.templates.TiledTemplateSet;
import com.yahoo.prelude.templates.UserTemplate;
import com.yahoo.prelude.templates.test.TilingTestCase;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.config.PageTemplateXMLReader;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.io.*;

import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class ExecutionAbstractTestCase {

    private static final String root="src/test/java/com/yahoo/search/pagetemplates/engine/test/";

    protected PageTemplate importPage(String name) {
        PageTemplate template=new PageTemplateXMLReader().readFile(root + name);
        assertNotNull("Could look up page template '" + name + "'",template);
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

    protected void assertRendered(Result result,String resultFileName) {
        assertRendered(result,resultFileName,false);
    }

    protected void assertRendered(Result result,String resultFileName, UserTemplate<?> template) {
        assertRendered(result,resultFileName,template,false);
    }

    protected void assertRendered(Result result,String resultFileName,boolean print) {
        assertRendered(result,resultFileName,new TiledTemplateSet(),print);
    }

    @SuppressWarnings("deprecation")
    protected void assertRendered(Result result,String resultFileName,UserTemplate<?> template, boolean print) {
        result.getTemplating().setTemplates(template);
        try {
            TilingTestCase.assertRendered(IOUtils.readFile(new File(root + resultFileName)), result);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
