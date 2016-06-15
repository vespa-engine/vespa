// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsumapi.h"
#include <vespa/vespalib/util/sync.h>

namespace search {
namespace engine {

DocsumReply::UP
DocsumServer::getDocsums(DocsumRequest::UP request)
{
    (void) request;
    assert(false);
    return DocsumReply::UP();
}

}
}
