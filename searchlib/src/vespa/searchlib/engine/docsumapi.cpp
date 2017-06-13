// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsumapi.h"
#include <cassert>

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
