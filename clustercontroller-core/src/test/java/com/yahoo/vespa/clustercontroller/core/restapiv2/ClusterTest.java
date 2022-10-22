// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClusterTest extends StateRestApiTest {

    @Test
    void testCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("books", 0));
        assertEquals("""
                     {
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
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

    @Test
    void testRecursiveCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music", 1));
        assertEquals("""
                     {
                       "state" : {
                         "generated" : {
                           "state" : "up",
                           "reason" : ""
                         }
                       },
                       "service" : {
                         "storage" : {
                           "node" : {
                             "1" : {
                               "link" : "/cluster/v2/music/storage/1"
                             },
                             "2" : {
                               "link" : "/cluster/v2/music/storage/2"
                             },
                             "3" : {
                               "link" : "/cluster/v2/music/storage/3"
                             },
                             "5" : {
                               "link" : "/cluster/v2/music/storage/5"
                             },
                             "7" : {
                               "link" : "/cluster/v2/music/storage/7"
                             }
                           }
                         },
                         "distributor" : {
                           "node" : {
                             "1" : {
                               "link" : "/cluster/v2/music/distributor/1"
                             },
                             "2" : {
                               "link" : "/cluster/v2/music/distributor/2"
                             },
                             "3" : {
                               "link" : "/cluster/v2/music/distributor/3"
                             },
                             "5" : {
                               "link" : "/cluster/v2/music/distributor/5"
                             },
                             "7" : {
                               "link" : "/cluster/v2/music/distributor/7"
                             }
                           }
                         }
                       },
                       "distribution-states" : {
                         "published" : {
                           "baseline" : "distributor:8 .0.s:d .2.s:d .4.s:d .6.s:d storage:8 .0.s:d .2.s:d .4.s:d .6.s:d",
                           "bucket-spaces" : [ ]
                         }
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }
}
