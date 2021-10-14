// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumreply.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <cassert>

namespace search::engine {

DocsumReply::DocsumReply() : DocsumReply(vespalib::Slime::UP(nullptr)) { }

DocsumReply::DocsumReply(vespalib::Slime::UP root)
    : _root(std::move(root))
{ }

DocsumReply::~DocsumReply() { }

vespalib::slime::Inspector & DocsumReply::root() const {
    assert(_root);
    return _root->get();
}

}

