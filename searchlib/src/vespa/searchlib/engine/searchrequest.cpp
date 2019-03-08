// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchrequest.h"
#include "packetconverter.h"

namespace search::engine {

SearchRequest::SearchRequest()
    : SearchRequest(RelativeTime(std::make_unique<FastosClock>())) {}

SearchRequest::SearchRequest(RelativeTime relativeTime)
    : Request(std::move(relativeTime)),
      offset(0),
      maxhits(10),
      sortSpec(),
      groupSpec(),
      sessionId()
{ }

SearchRequest::~SearchRequest() = default;


SearchRequest::Source::Source(SearchRequest * request)
    : _request(request),
      _fs4Packet(nullptr),
      _desc(0),
      _relativeTime()
{ }

SearchRequest::Source::Source(FS4Packet_QUERYX *query, SourceDescription desc)
    : _request(),
      _fs4Packet(query),
      _desc(desc),
      _relativeTime(std::make_unique<RelativeTime>(std::make_unique<FastosClock>()))
{ }

SearchRequest::Source::Source(Source && rhs) noexcept
    : _request(std::move(rhs._request)),
      _fs4Packet(rhs._fs4Packet),
      _desc(std::move(rhs._desc)),
      _relativeTime(std::move(rhs._relativeTime))
{
    rhs._fs4Packet = nullptr;
}

void SearchRequest::Source::lazyDecode() const
{
    if ( ! _request && (_fs4Packet != nullptr)) {
        _request = std::make_unique<SearchRequest>(std::move(*_relativeTime));
        PacketConverter::toSearchRequest(*_fs4Packet, *_request);
        _fs4Packet->Free();
        _fs4Packet = nullptr;
    }
}

SearchRequest::Source::~Source() {
    if (_fs4Packet != nullptr) {
        _fs4Packet->Free();
    }
}

}

