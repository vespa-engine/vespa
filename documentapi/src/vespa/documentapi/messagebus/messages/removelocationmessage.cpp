// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removelocationmessage.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/bucket/bucketselector.h>
#include <vespa/vespalib/util/exceptions.h>

namespace documentapi {

RemoveLocationMessage::RemoveLocationMessage(const document::BucketIdFactory& factory,
                                             document::select::Parser& parser,
                                             string documentSelection)
    : _documentSelection(std::move(documentSelection)),
      _bucketId(),
      _bucketSpace()
{
    document::BucketSelector bucketSel(factory);
    auto exprResult = bucketSel.select(*parser.parse(_documentSelection));

    if (exprResult && exprResult->size() == 1) {
        _bucketId = (*exprResult)[0];
    } else {
        throw vespalib::IllegalArgumentException("Document selection doesn't map to a single bucket!", VESPA_STRLOC);
    }
}

RemoveLocationMessage::~RemoveLocationMessage() = default;

DocumentReply::UP
RemoveLocationMessage::doCreateReply() const {
    return std::make_unique<DocumentReply>(DocumentProtocol::REPLY_REMOVELOCATION);
}

uint32_t
RemoveLocationMessage::getType() const {
    return DocumentProtocol::MESSAGE_REMOVELOCATION;
}

}
