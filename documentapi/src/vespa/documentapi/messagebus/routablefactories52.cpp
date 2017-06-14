// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "routablefactories52.h"
#include <vespa/documentapi/documentapi.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::nbostream;
using std::make_shared;
using std::make_unique;

namespace documentapi {

void
RoutableFactories52::PutDocumentMessageFactory::decodeInto(PutDocumentMessage & msg, document::ByteBuffer & buf) const {
    super::decodeInto(msg, buf);
    decodeTasCondition(msg, buf);
}

bool
RoutableFactories52::PutDocumentMessageFactory::doEncode(const DocumentMessage & msg, vespalib::GrowableByteBuffer & buf) const
{
    if (! super::doEncode(msg, buf)) {
        return false;
    }

    encodeTasCondition(buf, msg);
    return true;
}

void
RoutableFactories52::RemoveDocumentMessageFactory::decodeInto(RemoveDocumentMessage & msg, document::ByteBuffer & buf) const {
    super::decodeInto(msg, buf);
    decodeTasCondition(msg, buf);
}


bool
RoutableFactories52::RemoveDocumentMessageFactory::doEncode(const DocumentMessage & msg, vespalib::GrowableByteBuffer & buf) const
{
    if (! super::doEncode(msg, buf)) {
        return false;
    }

    encodeTasCondition(buf, msg);
    return true;
}

void
RoutableFactories52::UpdateDocumentMessageFactory::decodeInto(UpdateDocumentMessage & msg, document::ByteBuffer & buf) const {
    super::decodeInto(msg, buf);
    decodeTasCondition(msg, buf);
}

bool
RoutableFactories52::UpdateDocumentMessageFactory::doEncode(const DocumentMessage & msg, vespalib::GrowableByteBuffer & buf) const
{
    if (! super::doEncode(msg, buf)) {
        return false;
    }

    encodeTasCondition(buf, msg);
    return true;
}

void RoutableFactories52::decodeTasCondition(DocumentMessage & docMsg, document::ByteBuffer & buf) {
    auto & msg = static_cast<TestAndSetMessage &>(docMsg);
    msg.setCondition(TestAndSetCondition(decodeString(buf)));
}

void RoutableFactories52::encodeTasCondition(vespalib::GrowableByteBuffer & buf, const DocumentMessage & docMsg) {
    auto & msg = static_cast<const TestAndSetMessage &>(docMsg);
    buf.putString(msg.getCondition().getSelection());
}

}
