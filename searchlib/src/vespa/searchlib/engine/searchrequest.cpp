// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".engine.searchrequest");
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
{
}

void SearchRequest::Source::lazyDecode() const
{
    if ((_request.get() == NULL) && (_fs4Packet != NULL)) {
        _request.reset(new SearchRequest());
        PacketConverter::toSearchRequest(*_fs4Packet, *_request);
        _fs4Packet->Free();
        _fs4Packet = NULL;
    }
}

} // namespace engine
} // namespace search
