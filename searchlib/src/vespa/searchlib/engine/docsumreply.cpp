// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".engine.docsumreply");
#include "docsumreply.h"
#include "tracereply.h"

namespace search {
namespace engine {

DocsumReply::DocsumReply() : DocsumReply(vespalib::Slime::UP(nullptr)) { }

DocsumReply::DocsumReply(vespalib::Slime::UP root)
    : docsums(),
      _root(std::move(root))
{
}

} // namespace engine
} // namespace search
