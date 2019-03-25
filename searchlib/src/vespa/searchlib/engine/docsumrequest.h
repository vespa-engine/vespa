// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include "request.h"
#include "lazy_source.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/hitrank.h>

namespace search::fs4transport { class FS4Packet_GETDOCSUMSX; }

namespace search::engine {

class DocsumRequest : public Request
{
public:
    using FS4Packet_GETDOCSUMSX = fs4transport::FS4Packet_GETDOCSUMSX;

    using UP = std::unique_ptr<DocsumRequest>;
    using SP = std::shared_ptr<DocsumRequest>;
    using Source = LazySource<DocsumRequest>;

    class Hit {
    public:
        Hit() : gid(), docid(0), path(0) {}
        Hit(const document::GlobalId & gid_) : gid(gid_), docid(0), path(0) {}

        document::GlobalId gid;
        mutable uint32_t  docid; // converted in backend
        uint32_t  path;      // wide
    };

public:
    uint32_t          _flags;
    vespalib::string  resultClassName;
    bool              useWideHits;
private:
    const bool        _useRootSlime;
public:
    std::vector<Hit>  hits;
    std::vector<char> sessionId;

    DocsumRequest();
    explicit DocsumRequest(bool useRootSlime_);
    DocsumRequest(RelativeTime relativeTime, bool useRootSlime_);
    ~DocsumRequest() override;

    bool useRootSlime() const { return _useRootSlime; }
};

}
