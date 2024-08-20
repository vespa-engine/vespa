// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <map>
#include <memory>
#include <mutex>
#include <shared_mutex>
#include <string>
#include <vector>

namespace proton {

class AttributeDirectory;
/**
 * Class with utility functions for handling the disk directory layout for attribute vectors.
 */
class AttributeDiskLayout : public std::enable_shared_from_this<AttributeDiskLayout>
{
private:
    const std::string _baseDir;
    mutable std::shared_mutex _mutex;
    std::map<std::string, std::shared_ptr<AttributeDirectory>> _dirs;

    void scanDir();
    struct PrivateConstructorTag { };
public:
    explicit AttributeDiskLayout(const std::string &baseDir, PrivateConstructorTag tag);
    ~AttributeDiskLayout();
    std::vector<std::string> listAttributes();
    const std::string &getBaseDir() const { return _baseDir; }
    std::shared_ptr<AttributeDirectory> getAttributeDir(const std::string &name);
    std::shared_ptr<AttributeDirectory> createAttributeDir(const std::string &name);
    void removeAttributeDir(const std::string &name, search::SerialNum serialNum);
    static std::shared_ptr<AttributeDiskLayout> create(const std::string &baseDir);
    static std::shared_ptr<AttributeDiskLayout> createSimple(const std::string &baseDir);
};

} // namespace proton
