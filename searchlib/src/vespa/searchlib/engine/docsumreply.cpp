// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumreply.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <cassert>

using vespalib::Slime;
using vespalib::slime::Inspector;
using vespalib::slime::NixValue;

namespace search::engine {

DocsumReply::DocsumReply(std::unique_ptr<vespalib::Slime> root,
                         DocsumRequest::UP request,
                         UniqueIssues::UP issues)
    : _slime(std::move(root)),
      _request(std::move(request)),
      _issues(std::move(issues))
{}

DocsumReply::DocsumReply(Slime::UP root, DocsumRequest::UP request)
    : DocsumReply(std::move(root), std::move(request), {}) {}

DocsumReply::DocsumReply(Slime::UP root)
    : DocsumReply(std::move(root), {}, {}) {}

DocsumReply::DocsumReply() = default;

vespalib::slime::Inspector & DocsumReply::root() const {
    return _slime ? _slime->get() : *NixValue::invalid();
}

bool DocsumReply::hasResult() const {
    return root().valid();
}

std::unique_ptr<Slime> DocsumReply::releaseSlime() {
    return std::move(_slime);
}

DocsumReply::~DocsumReply() = default;

}

