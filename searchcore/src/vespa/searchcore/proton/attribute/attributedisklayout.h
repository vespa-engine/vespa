// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchcommon/common/schema.h>
#include <mutex>
#include <shared_mutex>
#include <map>

namespace proton {

class AttributeDirectory;
/**
 * Class with utility functions for handling the disk directory layout for attribute vectors.
 */
class AttributeDiskLayout : public std::enable_shared_from_this<AttributeDiskLayout>
{
private:
    const vespalib::string _baseDir;
    mutable std::shared_timed_mutex _mutex;
    std::map<vespalib::string, std::shared_ptr<AttributeDirectory>> _dirs;

    static vespalib::string getSnapshotDir(uint64_t syncToken);
    static vespalib::string getSnapshotRemoveDir(const vespalib::string &baseDir, const vespalib::string &snapDir);

    void scanDir();
    struct PrivateConstructorTag { };
public:
    explicit AttributeDiskLayout(const vespalib::string &baseDir, PrivateConstructorTag tag);
    ~AttributeDiskLayout();
    static vespalib::string  getAttributeBaseDir(const vespalib::string &baseDir, const vespalib::string &attrName);
    static search::AttributeVector::BaseName getAttributeFileName(const vespalib::string &baseDir, const vespalib::string &attrName, uint64_t syncToken);
    static bool removeOldSnapshots(search::IndexMetaInfo &snapInfo, vespalib::Lock &snapInfoLock);
    static bool removeAttribute(const vespalib::string &baseDir, const vespalib::string &attrName, uint64_t wipeSerial);
    static std::vector<vespalib::string> listAttributes(const vespalib::string &baseDir);
    const vespalib::string &getBaseDir() const { return _baseDir; }
    void createBaseDir();
    std::shared_ptr<AttributeDirectory> getAttributeDir(const vespalib::string &name);
    std::shared_ptr<AttributeDirectory> createAttributeDir(const vespalib::string &name);
    void removeAttributeDir(const vespalib::string &name, search::SerialNum serialNum);
    static std::shared_ptr<AttributeDiskLayout> create(const vespalib::string &baseDir);
};

} // namespace proton

