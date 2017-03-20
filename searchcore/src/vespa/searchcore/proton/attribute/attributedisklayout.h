// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchcommon/common/schema.h>

namespace proton {

/**
 * Class with utility functions for handling the disk directory layout for attribute vectors.
 */
class AttributeDiskLayout
{
private:
    static vespalib::string getSnapshotDir(uint64_t syncToken);
    static vespalib::string getSnapshotRemoveDir(const vespalib::string &baseDir, const vespalib::string &snapDir);

public:
    static vespalib::string  getAttributeBaseDir(const vespalib::string &baseDir, const vespalib::string &attrName);
    static search::AttributeVector::BaseName getAttributeFileName(const vespalib::string &baseDir, const vespalib::string &attrName, uint64_t syncToken);
    static bool removeOldSnapshots(search::IndexMetaInfo &snapInfo, vespalib::Lock &snapInfoLock);
    static bool removeAttribute(const vespalib::string &baseDir, const vespalib::string &attrName);

};

} // namespace proton

