// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClusterListTest extends StateRestApiTest {

    @Test
    void testClusterList() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("", 0));
        assertEquals("""
                     {
                       "cluster" : {
                         "books" : {
                           "link" : "/cluster/v2/books"
                         },
                         "music" : {
                           "link" : "/cluster/v2/music"
                         }
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

    @Test
    void testRecursiveClusterList() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("", 1));
        assertEquals("""
                     {
                       "cluster" : {
                         "books" : {
                           "state" : {
                             "generated" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           },
                           "service" : {
                             "storage" : {
                               "link" : "/cluster/v2/books/storage"
                             },
                             "distributor" : {
                               "link" : "/cluster/v2/books/distributor"
                             }
                           },
                           "distribution-states" : {
                             "published" : {
                               "baseline" : "distributor:4 storage:4",
                               "bucket-spaces" : [ {
                                 "name" : "default",
                                 "state" : "distributor:4 storage:4 .3.s:m"
                               }, {
                                 "name" : "global",
                                 "state" : "distributor:4 storage:4"
                               } ]
                             }
                           }
                         },
                         "music" : {
                           "state" : {
                             "generated" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           },
                           "service" : {
                             "storage" : {
                               "link" : "/cluster/v2/music/storage"
                             },
                             "distributor" : {
                               "link" : "/cluster/v2/music/distributor"
                             }
                           },
                           "distribution-states" : {
                             "published" : {
                               "baseline" : "distributor:8 .0.s:d .2.s:d .4.s:d .6.s:d storage:8 .0.s:d .2.s:d .4.s:d .6.s:d",
                               "bucket-spaces" : [ ]
                             }
                           }
                         }
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

}
