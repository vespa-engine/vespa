// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/memory.h>
#include <memory>
#include <vespa/searchlib/engine/docsumrequest.h>

namespace vespalib { class Slime; }
namespace search::engine {

struct DocsumReply
{
    using UP = std::unique_ptr<DocsumReply>;

    using Blob = vespalib::MallocPtr;

    struct Docsum {
        uint32_t docid;
        document::GlobalId gid;
        Blob     data;

        Docsum() : docid(0), gid(), data(0) {}
        Docsum(document::GlobalId gid_) : docid(0), gid(gid_), data(0) { }
        Docsum(document::GlobalId gid_, const char *buf, uint32_t len) : docid(0), gid(gid_), data(len) {
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

    DocsumReply();
    DocsumReply(std::unique_ptr<vespalib::Slime> root);
    ~DocsumReply();
};

}

