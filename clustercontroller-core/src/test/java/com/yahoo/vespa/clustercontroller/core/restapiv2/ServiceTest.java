// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServiceTest extends StateRestApiTest {

    @Test
    void testService() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor", 0));
        assertEquals("""
                     {
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
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

    @Test
    void testRecursiveCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor", 1));
        assertEquals("""
                     {
                       "node" : {
                         "1" : {
                           "attributes" : {
                             "hierarchical-group" : "east.g2"
                           },
                           "state" : {
                             "generated" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "unit" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "user" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           }
                         },
                         "2" : {
                           "attributes" : {
                             "hierarchical-group" : "east.g1"
                           },
                           "state" : {
                             "generated" : {
                               "state" : "down",
                               "reason" : ""
                             },
                             "unit" : {
                               "state" : "down",
                               "reason" : "Node not seen in slobrok."
                             },
                             "user" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           }
                         },
                         "3" : {
                           "attributes" : {
                             "hierarchical-group" : "east.g2"
                           },
                           "state" : {
                             "generated" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "unit" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "user" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           }
                         },
                         "5" : {
                           "attributes" : {
                             "hierarchical-group" : "east.g2"
                           },
                           "state" : {
                             "generated" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "unit" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "user" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           }
                         },
                         "7" : {
                           "attributes" : {
                             "hierarchical-group" : "east.g2"
                           },
                           "state" : {
                             "generated" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "unit" : {
                               "state" : "up",
                               "reason" : ""
                             },
                             "user" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           }
                         }
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }
}
