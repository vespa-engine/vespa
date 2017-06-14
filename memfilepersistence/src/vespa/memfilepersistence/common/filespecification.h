// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::slotfile::FileSpecification
 * \ingroup memfile
 *
 * \brief Information about the file currently worked on.
 *
 * The file specification specifies what file a given MemFile should work on.
 */

#pragma once

#include "types.h"
#include <vespa/memfilepersistence/device/directory.h>
#include <vespa/vespalib/util/printable.h>

namespace storage {
namespace memfile {

class MemFileEnvironment;

class FileSpecification : private Types,
                          public vespalib::Printable
{
    BucketId _bucketId;
    Directory* _dir;
    String _path;
    FileVersion _wantedVersion;

public:
    FileSpecification(const BucketId&, Directory&, const String& path);

    void setWantedVersion(FileVersion v) { _wantedVersion = v; }

    const document::BucketId& getBucketId() const { return _bucketId; }
    Directory& getDirectory() const { return *_dir; }
    const String& getPath() const { return _path; }
    FileVersion getWantedFileVersion() const { return _wantedVersion; }

     void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    bool operator==(const FileSpecification& o) const {
        return (_bucketId == o._bucketId && _dir == o._dir
                && _path == o._path && _wantedVersion == o._wantedVersion);
    }
};

} // storage
} // memfile
