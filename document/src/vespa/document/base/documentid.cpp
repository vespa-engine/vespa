// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentid.h"
#include <vespa/vespalib/util/md5.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <ostream>

using vespalib::nbostream;

namespace document {

DocumentId::DocumentId()
    : Printable(),
      _globalId(),
      _id(new NullIdString())
{
}

DocumentId::DocumentId(vespalib::stringref id)
    : Printable(),
      _globalId(),
      _id(IdString::createIdString(id.c_str(), id.size()).release())
{
}

DocumentId::DocumentId(vespalib::nbostream & is)
    : Printable(),
      _globalId(),
      _id(IdString::createIdString(is.peek(), strlen(is.peek())).release())
{
    is.adjustReadPos(strlen(is.peek()) + 1);
}

DocumentId::DocumentId(const IdString& id)
    : Printable(),
      _globalId(),
      _id(id.clone())
{
}

vespalib::string
DocumentId::toString() const {
    return _id->toString();
}

void DocumentId::set(vespalib::stringref id) {
    _id.reset(IdString::createIdString(id).release());
    _globalId.first = false;
}

void
DocumentId::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) indent;
    if (verbose) {
        out << "DocumentId(id = ";
    }
    out << _id->toString().c_str();
    if (verbose) {
        out << ", " << getGlobalId().toString() << ")";
    }
}

size_t
DocumentId::getSerializedSize() const
{
    return _id->toString().size() + 1;
}

void DocumentId::swap(DocumentId & rhs) {
    _id.swap(rhs._id);
    std::swap(_globalId, rhs._globalId);
}

void
DocumentId::calculateGlobalId() const
{
    vespalib::string id(_id->toString());

    unsigned char key[16];
    fastc_md5sum(reinterpret_cast<const unsigned char*>(id.c_str()), id.size(), key);

    IdString::LocationType location(_id->getLocation());
    memcpy(key, &location, 4);

    _globalId.first = true;
    _globalId.second.set(key);
}


} // document
