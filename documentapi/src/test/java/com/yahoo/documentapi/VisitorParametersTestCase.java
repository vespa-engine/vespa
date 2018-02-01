// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisitorParametersTestCase {
    private LoadType loadType = new LoadType(3, "samnmax", DocumentProtocol.Priority.HIGH_3);

    private VisitorParameters createVisitorParameters() {
        VisitorParameters params = new VisitorParameters("");
        params.setDocumentSelection("id.user==5678");
        params.setBucketSpace("narnia");
        params.setFromTimestamp(9001);
        params.setToTimestamp(10001);
        params.setVisitorLibrary("CoolVisitor");
        params.setLibraryParameter("groovy", "dudes");
        params.setLibraryParameter("ninja", "turtles");
        params.setMaxBucketsPerVisitor(55);
        params.setPriority(DocumentProtocol.Priority.HIGHEST);
        params.setRoute("extraterrestrial/highway");
        params.setTimeoutMs(1337);
        params.setMaxPending(111);
        params.setFieldSet("[header]");
        params.setVisitorOrdering(123);
        params.setLoadType(loadType);
        params.setVisitRemoves(true);
        params.setVisitInconsistentBuckets(true);
        params.setTraceLevel(9);
        params.setResumeFileName("foo.txt");
        params.setResumeToken(new ProgressToken());
        params.setRemoteDataHandler("mars_rover");
        params.setControlHandler(new VisitorControlHandler());
        params.setMaxFirstPassHits(555);
        params.setMaxTotalHits(777);
        params.setDynamicallyIncreaseMaxBucketsPerVisitor(true);
        params.setDynamicMaxBucketsIncreaseFactor(2.5f);
        params.skipBucketsOnFatalErrors(true);

        return params;
    }

    @Test
    public void testCopyConstructor() {
        VisitorParameters params = createVisitorParameters();

        VisitorParameters copy = new VisitorParameters(params);

        assertEquals("id.user==5678", copy.getDocumentSelection());
        assertEquals(9001, copy.getFromTimestamp());
        assertEquals(10001, copy.getToTimestamp());
        assertEquals("CoolVisitor", copy.getVisitorLibrary());
        assertEquals(2, copy.getLibraryParameters().size());
        assertEquals("dudes", new String(copy.getLibraryParameters().get("groovy")));
        assertEquals("turtles", new String(copy.getLibraryParameters().get("ninja")));
        assertEquals(55, copy.getMaxBucketsPerVisitor());
        assertEquals(DocumentProtocol.Priority.HIGHEST, copy.getPriority());
        assertEquals("extraterrestrial/highway", copy.getRoute().toString());
        assertEquals(1337, copy.getTimeoutMs());
        assertEquals(111, copy.getMaxPending());
        assertEquals("[header]", copy.getFieldSet());
        assertEquals(123, copy.getVisitorOrdering());
        assertEquals(loadType, copy.getLoadType());
        assertEquals(true, copy.getVisitRemoves());
        assertEquals(true, copy.getVisitInconsistentBuckets());
        assertEquals(9, copy.getTraceLevel());
        assertEquals("foo.txt", copy.getResumeFileName());
        assertEquals(params.getResumeToken(), copy.getResumeToken()); // instance compare
        assertEquals("mars_rover", copy.getRemoteDataHandler());
        assertEquals(params.getControlHandler(), copy.getControlHandler());
        assertEquals(555, copy.getMaxFirstPassHits());
        assertEquals(777, copy.getMaxTotalHits());
        assertEquals(true, copy.getDynamicallyIncreaseMaxBucketsPerVisitor());
        assertEquals(2.5f, copy.getDynamicMaxBucketsIncreaseFactor(), 0.0001);
        assertEquals(true, copy.skipBucketsOnFatalErrors());

        // Test local data handler copy
        VisitorParameters params2 = new VisitorParameters("");
        params2.setLocalDataHandler(new SimpleVisitorDocumentQueue());
        VisitorParameters copy2 = new VisitorParameters(params2);
        assertEquals(params2.getLocalDataHandler(), copy2.getLocalDataHandler()); // instance compare
    }

    @Test
    public void testToString() {
        VisitorParameters params = createVisitorParameters();

        assertEquals(
                "VisitorParameters(\n" +
                "  Document selection: id.user==5678\n" +
                "  Bucket space:       narnia\n" +
                "  Visitor library:    CoolVisitor\n" +
                "  Max pending:        111\n" +
                "  Timeout (ms):       1337\n" +
                "  Time period:        9001 - 10001\n" +
                "  Visiting remove entries\n" +
                "  Visiting inconsistent buckets\n" +
                "  Visitor library parameters:\n" +
                "    groovy : dudes\n" +
                "    ninja : turtles\n" +
                "  Field set:          [header]\n" +
                "  Route:              extraterrestrial/highway\n" +
                "  Weight:             1.0\n" +
                "  Max firstpass hits: 555\n" +
                "  Max total hits:     777\n" +
                "  Visitor ordering:   123\n" +
                "  Max buckets:        55\n" +
                "  Priority:           HIGHEST\n" +
                "  Dynamically increasing max buckets per visitor\n" +
                "  Increase factor:    2.5\n" +
                ")",
                params.toString());
    }
}
