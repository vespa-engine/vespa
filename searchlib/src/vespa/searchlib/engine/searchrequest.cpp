// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchrequest.h"
#include "packetconverter.h"

namespace search {
namespace engine {

SearchRequest::SearchRequest()
    : Request(),
      offset(0),
      maxhits(10),
      sortSpec(),
      groupSpec(),
      sessionId()
{ }

SearchRequest::~SearchRequest() {}

void SearchRequest::Source::lazyDecode() const
{
    if ((_request.get() == NULL) && (_fs4Packet != NULL)) {
        _request.reset(new SearchRequest());
        PacketConverter::toSearchRequest(*_fs4Packet, *_request);
        _fs4Packet->Free();
        _fs4Packet = NULL;
    }
}

SearchRequest::Source::~Source() {
    if (_fs4Packet != NULL) {
        _fs4Packet->Free();
    }
}

} // namespace engine
} // namespace search
