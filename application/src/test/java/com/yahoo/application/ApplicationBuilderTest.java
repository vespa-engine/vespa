// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.nio.file.Files;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author tonytv
 * @author Olli Virtanen
 */
public class ApplicationBuilderTest {
    @Test
    public void query_profile_types_can_be_added() throws Exception {
        withApplicationBuilder(builder -> {
            builder.queryProfileType("MyProfileType", "<query-profile-type id=\"MyProfileType\">" + //
                    "<field name=\"age\" type=\"integer\" />" + //
                    "<field name=\"profession\" type=\"string\" />" + //
                    "<field name=\"user\" type=\"query-profile:MyUserProfile\" />" + //
                    "</query-profile-type>");

            assertTrue(Files.exists(builder.getPath().resolve("search/query-profiles/types/MyProfileType.xml")));
        });
    }

    @Test
    public void query_profile_can_be_added() throws Exception {
        withApplicationBuilder(builder -> {
            builder.queryProfile("MyProfile", "<query-profile id=\"MyProfile\">" + //
                    "<field name=\"message\">Hello world!</field>" + //
                    "</query-profile>");

            assertTrue(Files.exists(builder.getPath().resolve("search/query-profiles/MyProfile.xml")));
        });
    }

    @Test
    public void rank_expression_can_be_added() throws Exception {
        withApplicationBuilder(builder -> {
            builder.rankExpression("myExpression", "content");
            assertTrue(Files.exists(builder.getPath().resolve("searchdefinitions/myExpression.expression")));
        });
    }

    @Test
    public void builder_cannot_be_reused() throws Exception {
        try {
            ApplicationBuilder builder = new ApplicationBuilder();
            builder.servicesXml("<jdisc version=\"1.0\" />");
            builder.build();

            builder.servicesXml("");
            fail("Expected exception.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("build method"));
        }

    }

    private interface TestCase {
        public void accept(ApplicationBuilder ab) throws Exception;
    }

    public void withApplicationBuilder(TestCase f) throws Exception {
        ApplicationBuilder builder = new ApplicationBuilder();
        try {
            f.accept(builder);
        } finally {
            IOUtils.recursiveDeleteDir(builder.getPath().toFile());
        }
    }
}
