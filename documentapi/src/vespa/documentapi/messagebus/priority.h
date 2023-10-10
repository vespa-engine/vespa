// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>

namespace documentapi {

class Priority {
public:
/**
   Define the different priorities allowed for document api messages.
   Most user traffic should be fit into the NORMAL categories. Traffic
   in the HIGH end will be usually be prioritized over important maintenance
   operations. Traffic in the LOW end will be prioritized after
   these operations.

   These enum values MUST be 0-indexed and continuous.
*/
    enum Value {
        PRI_HIGHEST = 0,
        PRI_VERY_HIGH = 1,
        PRI_HIGH_1 = 2,
        PRI_HIGH_2 = 3,
        PRI_HIGH_3 = 4,
        PRI_NORMAL_1 = 5,
        PRI_NORMAL_2 = 6,
        PRI_NORMAL_3 = 7,
        PRI_NORMAL_4 = 8,
        PRI_NORMAL_5 = 9,
        PRI_NORMAL_6 = 10,
        PRI_LOW_1 = 11,
        PRI_LOW_2 = 12,
        PRI_LOW_3 = 13,
        PRI_VERY_LOW = 14,
        PRI_LOWEST = 15,
        
        PRI_ENUM_SIZE = 16
    };

    static Value getPriority(const string& priorityName) {
        if (priorityName == ("HIGHEST")) { return PRI_HIGHEST; }
        if (priorityName == ("VERY_HIGH")) { return PRI_VERY_HIGH; }
        if (priorityName == ("HIGH_1")) { return PRI_HIGH_1; }
        if (priorityName == ("HIGH_2")) { return PRI_HIGH_2; }
        if (priorityName == ("HIGH_3")) { return PRI_HIGH_3; }
        if (priorityName == ("NORMAL_1")) { return PRI_NORMAL_1; }
        if (priorityName == ("NORMAL_2")) { return PRI_NORMAL_2; }
        if (priorityName == ("NORMAL_3")) { return PRI_NORMAL_3; }
        if (priorityName == ("NORMAL_4")) { return PRI_NORMAL_4; }
        if (priorityName == ("NORMAL_5")) { return PRI_NORMAL_5; }
        if (priorityName == ("NORMAL_6")) { return PRI_NORMAL_6; }
        if (priorityName == ("LOW_1")) { return PRI_LOW_1; }
        if (priorityName == ("LOW_2")) { return PRI_LOW_2; }
        if (priorityName == ("LOW_3")) { return PRI_LOW_3; }
        if (priorityName == ("VERY_LOW")) { return PRI_VERY_LOW; }
        if (priorityName == ("LOWEST")) { return PRI_LOWEST; }
        return PRI_LOWEST;
    };
};

}

