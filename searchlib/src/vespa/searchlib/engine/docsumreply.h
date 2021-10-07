// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/searchlib/common/unique_issues.h>
#include <memory>
#include <vespa/searchlib/engine/docsumrequest.h>

namespace vespalib { class Slime; }
namespace search::engine {

struct DocsumReply
{
    using UP = std::unique_ptr<DocsumReply>;

    using Blob = vespalib::MallocPtr;

    struct Docsum {
        document::GlobalId gid;
        Blob     data;

        Docsum() noexcept : gid(), data(0) {}
        Docsum(document::GlobalId gid_) noexcept : gid(gid_), data(0) { }
        Docsum(document::GlobalId gid_, const char *buf, uint32_t len) noexcept : gid(gid_), data(len) {
            memcpy(data.str(), buf, len);
        }
        Docsum & setData(const char *buf, uint32_t len) {
            data.resize(len);
            memcpy(data.str(), buf, len);
            return *this;
        }
    };
    std::vector<Docsum> docsums;

    mutable DocsumRequest::UP request;
    std::unique_ptr<vespalib::Slime> _root;
    UniqueIssues::UP my_issues;

    DocsumReply();
    DocsumReply(std::unique_ptr<vespalib::Slime> root);
    ~DocsumReply();
};

}

