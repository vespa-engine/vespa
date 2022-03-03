// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentid.h"
#include <vespa/vespalib/util/md5.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <ostream>

using vespalib::nbostream;

namespace document {

DocumentId::DocumentId()
    : _globalId(),
      _id()
{
}

DocumentId::DocumentId(vespalib::stringref id)
    : _globalId(),
      _id(id)
{
}

DocumentId::DocumentId(vespalib::nbostream & is)
    : _globalId(),
      _id({is.peek(), strlen(is.peek())})
{
    is.adjustReadPos(strlen(is.peek()) + 1);
}

DocumentId::DocumentId(const DocumentId & rhs) = default;
DocumentId & DocumentId::operator = (const DocumentId & rhs) = default;
DocumentId::~DocumentId() noexcept = default;

vespalib::string
DocumentId::toString() const {
    return _id.toString();
}

void
DocumentId::set(vespalib::stringref id) {
    _id = IdString(id);
    _globalId.first = false;
}

size_t
DocumentId::getSerializedSize() const
{
    return _id.toString().size() + 1;
}

void
DocumentId::calculateGlobalId() const
{
    vespalib::string id(_id.toString());

    unsigned char key[16];
    fastc_md5sum(reinterpret_cast<const unsigned char*>(id.c_str()), id.size(), key);

    IdString::LocationType location(_id.getLocation());
    memcpy(key, &location, 4);

    // FIXME this lazy init mechanic does not satisfy const thread safety
    _globalId.second.set(key);
    _globalId.first = true;
}

std::ostream &
operator << (std::ostream & os, const DocumentId & id) {
    return os << id.toString();
}

} // document
