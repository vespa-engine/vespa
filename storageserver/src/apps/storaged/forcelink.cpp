// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/forcelink.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/searchlib/aggregation/forcelink.hpp>
#include <vespa/searchlib/expression/forcelink.hpp>

/* Here is code that initializes a lot of stuff to force it to be linked */
namespace search {
    struct ForceLink {
        ForceLink() {
            if (time(NULL) == 7) {
                // grouping stuff
                forcelink_searchlib_aggregation();
                forcelink_searchlib_expression();
            }
        }
    };
}

namespace storage {

void serverForceLink()
{
    document::ForceLink documentForce;
    search::ForceLink searchForce;
}

} // namespace storage
