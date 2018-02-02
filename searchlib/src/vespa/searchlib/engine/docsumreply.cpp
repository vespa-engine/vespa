// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumreply.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace search::engine {

DocsumReply::DocsumReply() : DocsumReply(vespalib::Slime::UP(nullptr)) { }

DocsumReply::DocsumReply(vespalib::Slime::UP root)
    : docsums(),
      _root(std::move(root))
{ }

DocsumReply::~DocsumReply() { }

}

