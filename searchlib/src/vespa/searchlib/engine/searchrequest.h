// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "propertiesmap.h"
#include "request.h"
#include "source_description.h"

namespace search {
namespace fs4transport { class FS4Packet_QUERYX; }
namespace engine {

class SearchRequest : public Request
{
public:
    typedef std::unique_ptr<SearchRequest> UP;
    typedef fs4transport::FS4Packet_QUERYX FS4Packet_QUERYX;

    class Source {
    private:
        mutable std::unique_ptr<SearchRequest> _request;
        mutable FS4Packet_QUERYX *_fs4Packet;
        void lazyDecode() const;
        const SourceDescription _desc;
    public:

        Source(SearchRequest * request)
          : _request(request),
            _fs4Packet(NULL),
            _desc(0)
        { }

        Source(FS4Packet_QUERYX *query, SourceDescription desc)
          : _request(),
            _fs4Packet(query),
            _desc(desc)
        { }

        Source(Source && rhs)
          : _request(std::move(rhs._request)),
            _fs4Packet(rhs._fs4Packet),
            _desc(std::move(rhs._desc))
        {
            rhs._fs4Packet = NULL;
        }

        ~Source();

        const SearchRequest * operator -> () const { return get(); }

        const SearchRequest * get() const {
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
    typedef std::shared_ptr<SearchRequest> SP;

    uint32_t          offset;
    uint32_t          maxhits;
    vespalib::string  sortSpec;
    std::vector<char> groupSpec;
    std::vector<char> sessionId;

    SearchRequest();
    ~SearchRequest();
};

} // namespace engine
} // namespace search

