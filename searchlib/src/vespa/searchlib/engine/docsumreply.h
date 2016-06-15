// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/memory.h>
#include <memory>
#include <vespa/searchlib/engine/docsumrequest.h>
#include "tracereply.h"

namespace search {
namespace engine {

struct DocsumReply
{
    typedef std::unique_ptr<DocsumReply> UP;

    typedef vespalib::MallocPtr Blob;

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
    vespalib::Slime::UP _root;

    DocsumReply();
    DocsumReply(vespalib::Slime::UP root);
};

} // namespace engine
} // namespace search
