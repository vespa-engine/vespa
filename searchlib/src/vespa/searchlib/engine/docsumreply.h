// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsumrequest.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/unique_issues.h>
#include <vespa/vespalib/util/memory.h>
#include <cassert>
#include <memory>
#include <vector>

namespace vespalib { class Slime; }
namespace vespalib::slime { struct Inspector; }
namespace search::engine {

class DocsumReply {
private:
    std::unique_ptr<vespalib::Slime> _slime;
    DocsumRequest::UP _request;
    UniqueIssues::UP _issues;
public:
    using UP = std::unique_ptr<DocsumReply>;

    DocsumReply(std::unique_ptr<vespalib::Slime> root,
                DocsumRequest::UP request,
                UniqueIssues::UP issues);

    DocsumReply(std::unique_ptr<vespalib::Slime> root,
                DocsumRequest::UP request);

    DocsumReply(std::unique_ptr<vespalib::Slime> root);

    DocsumReply();

    bool hasResult() const;
    bool hasRequest() const { return (_request.get() != nullptr); }
    bool hasIssues() const { return _issues && (_issues->size() > 0); }

    const vespalib::Slime & slime() const { assert(_slime.get()); return *_slime; }
    const DocsumRequest& request() const { assert(_request.get()); return *_request; }
    const UniqueIssues & issues() const { assert(_issues.get()); return *_issues; }

    void setRequest(DocsumRequest::UP request) {
        _request = std::move(request);
    }

    void setIssues(UniqueIssues::UP issues) {
        _issues = std::move(issues);
    }

    // only used by unit test:
    std::unique_ptr<vespalib::Slime> releaseSlime();

    vespalib::slime::Inspector & root() const;

    ~DocsumReply();
};

}

