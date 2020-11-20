// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static com.yahoo.vespa.config.server.configchange.Utils.*;

/**
 * @author geirst
 * @since 5.44
 */
public class ConfigChangeActionsSlimeConverterTest {

    private static String toJson(ConfigChangeActions actions) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        new ConfigChangeActionsSlimeConverter(actions).toSlime(root);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new JsonFormat(false).encode(outputStream, slime);
        return outputStream.toString();
    }

    @Test
    public void json_representation_of_empty_actions() throws IOException {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().build();
        assertEquals(   "{\n" +
                        " \"configChangeActions\": {\n" +
                        "  \"restart\": [\n" +
                        "  ],\n" +
                        "  \"refeed\": [\n" +
                        "  ],\n" +
                        "  \"reindex\": [\n" +
                        "  ]\n" +
                        " }\n" +
                        "}\n",
                     toJson(actions));
    }

    @Test
    public void json_representation_of_restart_actions() throws IOException {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME_2).
                restart(CHANGE_MSG_2, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG_2, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME_2).build();
        assertEquals("{\n" +
                        " \"configChangeActions\": {\n" +
                        "  \"restart\": [\n" +
                        "   {\n" +
                        "    \"clusterName\": \"foo\",\n" +
                        "    \"clusterType\": \"search\",\n" +
                        "    \"serviceType\": \"searchnode\",\n" +
                        "    \"messages\": [\n" +
                        "     \"change\",\n" +
                        "     \"other change\"\n" +
                        "    ],\n" +
                        "    \"services\": [\n" +
                        "     {\n" +
                        "      \"serviceName\": \"baz\",\n" +
                        "      \"serviceType\": \"searchnode\",\n" +
                        "      \"configId\": \"searchnode/baz\",\n" +
                        "      \"hostName\": \"hostname\"\n" +
                        "     },\n" +
                        "     {\n" +
                        "      \"serviceName\": \"qux\",\n" +
                        "      \"serviceType\": \"searchnode\",\n" +
                        "      \"configId\": \"searchnode/qux\",\n" +
                        "      \"hostName\": \"hostname\"\n" +
                        "     }\n" +
                        "    ]\n" +
                        "   }\n" +
                        "  ],\n" +
                        "  \"refeed\": [\n" +
                        "  ],\n" +
                        "  \"reindex\": [\n" +
                        "  ]\n" +
                        " }\n" +
                        "}\n",
                     toJson(actions));
    }

    @Test
    public void json_representation_of_refeed_actions() throws IOException {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                refeed(CHANGE_ID, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_TYPE).
                refeed(CHANGE_ID_2, CHANGE_MSG, DOC_TYPE_2, CLUSTER, SERVICE_TYPE).build();
        assertEquals("{\n" +
                        " \"configChangeActions\": {\n" +
                        "  \"restart\": [\n" +
                        "  ],\n" +
                        "  \"refeed\": [\n" +
                        "   {\n" +
                        "    \"name\": \"field-type-change\",\n" +
                        "    \"documentType\": \"music\",\n" +
                        "    \"clusterName\": \"foo\",\n" +
                        "    \"messages\": [\n" +
                        "     \"change\"\n" +
                        "    ],\n" +
                        "    \"services\": [\n" +
                        "     {\n" +
                        "      \"serviceName\": \"searchnode\",\n" +
                        "      \"serviceType\": \"myservicetype\",\n" +
                        "      \"configId\": \"myservicetype/searchnode\",\n" +
                        "      \"hostName\": \"hostname\"\n" +
                        "     }\n" +
                        "    ]\n" +
                        "   },\n" +
                        "   {\n" +
                        "    \"name\": \"indexing-change\",\n" +
                        "    \"documentType\": \"book\",\n" +
                        "    \"clusterName\": \"foo\",\n" +
                        "    \"messages\": [\n" +
                        "     \"change\"\n" +
                        "    ],\n" +
                        "    \"services\": [\n" +
                        "     {\n" +
                        "      \"serviceName\": \"searchnode\",\n" +
                        "      \"serviceType\": \"myservicetype\",\n" +
                        "      \"configId\": \"myservicetype/searchnode\",\n" +
                        "      \"hostName\": \"hostname\"\n" +
                        "     }\n" +
                        "    ]\n" +
                        "   }\n" +
                        "  ],\n" +
                        "  \"reindex\": [\n" +
                        "  ]\n" +
                        " }\n" +
                        "}\n",
                toJson(actions));
    }

        @Test
        public void json_representation_of_reindex_actions() throws IOException {
            ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                    reindex(CHANGE_ID, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_TYPE).build();
            assertEquals(
                    "{\n" +
                            " \"configChangeActions\": {\n" +
                            "  \"restart\": [\n" +
                            "  ],\n" +
                            "  \"refeed\": [\n" +
                            "  ],\n" +
                            "  \"reindex\": [\n" +
                            "   {\n" +
                            "    \"name\": \"field-type-change\",\n" +
                            "    \"documentType\": \"music\",\n" +
                            "    \"clusterName\": \"foo\",\n" +
                            "    \"messages\": [\n" +
                            "     \"change\"\n" +
                            "    ],\n" +
                            "    \"services\": [\n" +
                            "     {\n" +
                            "      \"serviceName\": \"searchnode\",\n" +
                            "      \"serviceType\": \"myservicetype\",\n" +
                            "      \"configId\": \"myservicetype/searchnode\",\n" +
                            "      \"hostName\": \"hostname\"\n" +
                            "     }\n" +
                            "    ]\n" +
                            "   }\n" +
                            "  ]\n" +
                            " }\n" +
                            "}\n",
                    toJson(actions));
    }

}
