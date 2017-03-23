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

    void scanDir();
    struct PrivateConstructorTag { };
public:
    explicit AttributeDiskLayout(const vespalib::string &baseDir, PrivateConstructorTag tag);
    ~AttributeDiskLayout();
    std::vector<vespalib::string> listAttributes();
    const vespalib::string &getBaseDir() const { return _baseDir; }
    void createBaseDir();
    std::shared_ptr<AttributeDirectory> getAttributeDir(const vespalib::string &name);
    std::shared_ptr<AttributeDirectory> createAttributeDir(const vespalib::string &name);
    void removeAttributeDir(const vespalib::string &name, search::SerialNum serialNum);
    static std::shared_ptr<AttributeDiskLayout> create(const vespalib::string &baseDir);
    static std::shared_ptr<AttributeDiskLayout> createSimple(const vespalib::string &baseDir);
};

} // namespace proton

