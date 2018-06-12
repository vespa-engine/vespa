// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valueupdate.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/document/util/bytebuffer.h>
#include <stdexcept>

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(ValueUpdate, Identifiable);

// Create a value update from a byte buffer.
std::unique_ptr<ValueUpdate>
ValueUpdate::createInstance(const DocumentTypeRepo& repo, const DataType& type, ByteBuffer& buffer, int serializationVersion)
{
    ValueUpdate* update(NULL);
    int32_t classId = 0;
    buffer.getIntNetwork(classId);

    const Identifiable::RuntimeClass * rtc(Identifiable::classFromId(classId));
    if (rtc != NULL) {
        update = static_cast<ValueUpdate*>(Identifiable::classFromId(classId)->create());
        /// \todo TODO (was warning):  Updates are not versioned in serialization format. Will not work with altering it.
        update->deserialize(repo, type, buffer, serializationVersion);
    } else {
        throw std::runtime_error(vespalib::make_string("Could not find a class for classId %d(%x)", classId, classId));
    }

    return std::unique_ptr<ValueUpdate>(update);
}

}
