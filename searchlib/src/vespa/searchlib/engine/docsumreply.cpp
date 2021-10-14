// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumreply.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <cassert>

namespace search::engine {

DocsumReply::DocsumReply() { }

DocsumReply::DocsumReply(vespalib::Slime::UP root) : _slime(std::move(root)) { }

vespalib::slime::Inspector & DocsumReply::root() const {
    return _slime ? _slime->get() : *vespalib::slime::NixValue::invalid();
}

std::unique_ptr<vespalib::Slime> DocsumReply::releaseSlime() {
    return std::move(_slime);
}

DocsumReply::~DocsumReply() { }

}

