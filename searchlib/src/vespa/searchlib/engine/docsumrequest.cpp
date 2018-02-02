// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumrequest.h"
#include "packetconverter.h"

namespace search::engine {

DocsumRequest::DocsumRequest()
    : DocsumRequest(false) {}

DocsumRequest::DocsumRequest(const fastos::TimeStamp &start_time)
    : DocsumRequest(start_time, false) {}

DocsumRequest::DocsumRequest(bool useRootSlime_)
    : DocsumRequest(fastos::ClockSystem::now(), useRootSlime_) {}

DocsumRequest::DocsumRequest(const fastos::TimeStamp &start_time, bool useRootSlime_)
    : Request(start_time),
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
    if ((_request.get() == NULL) && (_fs4Packet != NULL)) {
        _request.reset(new DocsumRequest(_start));
        PacketConverter::toDocsumRequest(*_fs4Packet, *_request);
        _fs4Packet->Free();
        _fs4Packet = NULL;
    }
}

DocsumRequest::Source::~Source() {
    if (_fs4Packet != NULL) {
        _fs4Packet->Free();
    }
}

}
