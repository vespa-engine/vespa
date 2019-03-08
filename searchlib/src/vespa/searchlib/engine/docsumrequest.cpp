// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumrequest.h"
#include "packetconverter.h"

namespace search::engine {

DocsumRequest::DocsumRequest()
    : DocsumRequest(false)
{}

DocsumRequest::DocsumRequest(bool useRootSlime_)
    : DocsumRequest(RelativeTime(std::make_unique<FastosClock>()), useRootSlime_)
{}

DocsumRequest::DocsumRequest(RelativeTime relativeTime, bool useRootSlime_)
    : Request(std::move(relativeTime)),
      _flags(0u),
      resultClassName(),
      useWideHits(false),
      _useRootSlime(useRootSlime_),
      hits()
{
}

DocsumRequest::~DocsumRequest() = default;

void DocsumRequest::Source::lazyDecode() const
{
    if ( !_request && (_fs4Packet != nullptr)) {
        _request = std::make_unique<DocsumRequest>(std::move(*_relativeTime), false);
        PacketConverter::toDocsumRequest(*_fs4Packet, *_request);
        _fs4Packet->Free();
        _fs4Packet = nullptr;
    }
}

DocsumRequest::Source::Source(FS4Packet_GETDOCSUMSX *query, SourceDescription desc)
    : _request(),
      _fs4Packet(query),
      _desc(desc),
      _relativeTime(std::make_unique<RelativeTime>(std::make_unique<FastosClock>()))
{ }

DocsumRequest::Source::Source(Source && rhs) noexcept
    : _request(std::move(rhs._request)),
      _fs4Packet(rhs._fs4Packet),
      _desc(std::move(rhs._desc)),
      _relativeTime(std::move(rhs._relativeTime))
{
    rhs._fs4Packet = nullptr;
}
DocsumRequest::Source::~Source() {
    if (_fs4Packet != nullptr) {
        _fs4Packet->Free();
    }
}

}
