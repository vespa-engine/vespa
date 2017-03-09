// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumrequest.h"
#include "packetconverter.h"

namespace search {
namespace engine {

DocsumRequest::DocsumRequest()
    : DocsumRequest(false)
{ }

DocsumRequest::~DocsumRequest() {}

DocsumRequest::DocsumRequest(bool useRootSlime_)
    : _flags(0u),
      resultClassName(),
      useWideHits(false),
      _useRootSlime(useRootSlime_),
      hits()
{ }


void DocsumRequest::Source::lazyDecode() const
{
    if ((_request.get() == NULL) && (_fs4Packet != NULL)) {
        _request.reset(new DocsumRequest());
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

} // namespace engine
} // namespace search
