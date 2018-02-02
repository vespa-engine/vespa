// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include "request.h"
#include "source_description.h"
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

    class Source {
    private:
        mutable DocsumRequest::UP _request;
        mutable FS4Packet_GETDOCSUMSX *_fs4Packet;
        void lazyDecode() const;
        const SourceDescription _desc;
        const fastos::TimeStamp _start;
    public:

        Source(DocsumRequest * request) : _request(request), _fs4Packet(NULL), _desc(0), _start(_request->getStartTime()) {}
        Source(DocsumRequest::UP request) : _request(std::move(request)), _fs4Packet(NULL), _desc(0), _start(_request->getStartTime()) {}
        Source(FS4Packet_GETDOCSUMSX *query, SourceDescription desc) : _request(), _fs4Packet(query), _desc(desc), _start(fastos::ClockSystem::now()) { }

        Source(Source && rhs)
          : _request(std::move(rhs._request)),
            _fs4Packet(rhs._fs4Packet),
            _desc(std::move(rhs._desc)),
            _start(rhs._start)
        {
            rhs._fs4Packet = NULL;
        }

        ~Source();

        const DocsumRequest * operator -> () const { return get(); }

        const DocsumRequest * get() const {
            lazyDecode();
            return _request.get();
        }

        Source& operator= (Source && rhs) = delete;
        Source & operator= (const Source &) = delete;
        Source(const Source &) = delete;

        UP release() {
            lazyDecode();
            return std::move(_request);
        }
    };

    class Hit
    {
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
    explicit DocsumRequest(const fastos::TimeStamp &start_time);
    explicit DocsumRequest(bool useRootSlime_);
    DocsumRequest(const fastos::TimeStamp &start_time, bool useRootSlime_);
    ~DocsumRequest();

    bool useRootSlime() const { return _useRootSlime; }
};

}
