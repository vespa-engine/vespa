// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "filespecification.h"
#include <vespa/vespalib/util/exceptions.h>

namespace storage {
namespace memfile {

FileSpecification::FileSpecification(const BucketId& bucket, Directory& dir,
                                     const String& path)
    : _bucketId(bucket),
      _dir(&dir),
      _path(path),
      _wantedVersion(TRADITIONAL_SLOTFILE)
{
    if (dir.getState() != Device::OK) {
        throw vespalib::IllegalStateException(
                "Attempt to create file specification for file on disk that "
                "is not available: " + dir.toString(), VESPA_STRLOC);
    }
}

void
FileSpecification::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "FileSpecification(" << _bucketId << ", " << *_dir << ", " << _path
        << ", wanted version 0x" << std::hex << _wantedVersion << std::dec
        << ")";
}

} // memfile
} // storage
