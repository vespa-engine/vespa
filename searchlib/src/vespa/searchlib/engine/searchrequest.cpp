// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchrequest.h"
#include "packetconverter.h"

namespace search::engine {

SearchRequest::SearchRequest()
    : SearchRequest(fastos::ClockSystem::now()) {}

SearchRequest::SearchRequest(const fastos::TimeStamp &start_time)
    : Request(start_time),
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
        _request.reset(new SearchRequest(_start));
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

}

