// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/searchlib/common/unique_issues.h>
#include <memory>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <cassert>

namespace vespalib { class Slime; }
namespace vespalib::slime { struct Inspector; }
namespace search::engine {

class DocsumReply {
private:
    DocsumRequest::UP _request;
    std::unique_ptr<vespalib::Slime> _slime;
    UniqueIssues::UP _issues;
public:
    using UP = std::unique_ptr<DocsumReply>;

    DocsumReply();
    DocsumReply(std::unique_ptr<vespalib::Slime> root);

    bool hasSlime() const { return _slime.get() != nullptr; }
    bool hasRequest() const { return _request.get() != nullptr; }
    bool hasIssues() const { return _issues.get() != nullptr; }

    vespalib::Slime & slime() const { assert(hasSlime()); return *_slime; }
    DocsumRequest& request() const { assert(hasRequest()); return *_request; }
    UniqueIssues & issues() const { assert(hasIssues()); return *_issues; }

    void setRequest(DocsumRequest::UP request) {
        _request = std::move(request);
    }
    void setIssues(UniqueIssues::UP issues) {
        _issues = std::move(issues);
    }

    std::unique_ptr<vespalib::Slime> releaseSlime();

    vespalib::slime::Inspector & root() const;

    ~DocsumReply();
};

}

