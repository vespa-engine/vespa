// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/searchlib/common/unique_issues.h>
#include <memory>
#include <vespa/searchlib/engine/docsumrequest.h>

namespace vespalib { class Slime; }
namespace vespalib::slime { struct Inspector; }
namespace search::engine {

struct DocsumReply
{
    using UP = std::unique_ptr<DocsumReply>;

    mutable DocsumRequest::UP request;
    std::unique_ptr<vespalib::Slime> _root;
    UniqueIssues::UP my_issues;

    vespalib::slime::Inspector & root() const;

    DocsumReply();
    DocsumReply(std::unique_ptr<vespalib::Slime> root);
    ~DocsumReply();
};

}

