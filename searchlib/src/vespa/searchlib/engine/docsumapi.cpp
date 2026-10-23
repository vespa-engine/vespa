// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsumapi.h"

#include <vespa/check_require.h>

namespace search::engine {

DocsumReply::UP DocsumServer::getDocsums(DocsumRequest::UP request) {
    (void)request;
    CHECK(false);
    return DocsumReply::UP();
}

} // namespace search::engine
