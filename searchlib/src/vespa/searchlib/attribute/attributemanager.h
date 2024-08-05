// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributeguard.h"
#include "iattributemanager.h"
#include "interlock.h"
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>

namespace search::attribute { class Config; }

namespace search {

/**
 * You use the attribute manager to get access to attributes. You must specify what kind
 * of access you want to have.
 **/
class AttributeManager : public IAttributeManager
{
private:
    using Config = attribute::Config;
public:
    using StringVector = std::vector<string>;
    using Snapshot = search::IndexMetaInfo::Snapshot;
    using AttributeList = std::vector<AttributeGuard>;
    using VectorHolder = std::shared_ptr<AttributeVector>;
    AttributeManager();
    explicit AttributeManager(string base);
    ~AttributeManager() override;

    /**
     * This will give you a handle to an attributevector. It
     * guarantees that backed attribute is valid.  But no guarantees
     * about the content of the attribute. If that is required some of
     * the other getAttributeXX methods must be used.
     **/
    const VectorHolder * getAttributeRef(std::string_view name) const;

    AttributeGuard::UP getAttribute(std::string_view name) const override;
    std::unique_ptr<attribute::AttributeReadGuard> getAttributeReadGuard(std::string_view name, bool stableEnumGuard) const override;
    void asyncForAttribute(std::string_view name, std::unique_ptr<attribute::IAttributeFunctor> func) const override;

    /**
     * This will load attributes in the most memory economical way by loading largest first.
     */
    bool addVector(std::string_view name, const Config & config);
    bool add(const VectorHolder & vector);

    void getAttributeList(AttributeList & list) const override;
    attribute::IAttributeContext::UP createContext() const override;

    std::shared_ptr<attribute::ReadableAttributeVector> readable_attribute_vector(std::string_view name) const override;

    const Snapshot & getSnapshot()         const { return _snapShot; }
    const string & getBaseDir()       const { return _baseDir; }
    void setBaseDir(const string & base);
    uint64_t getMemoryFootprint() const;

protected:
    using AttributeMap = vespalib::hash_map<string, VectorHolder>;
    AttributeMap   _attributes;
    mutable std::mutex _loadLock;
private:
    const VectorHolder * findAndLoadAttribute(std::string_view name) const;
    string createBaseFileName(std::string_view name) const;
    string    _baseDir;
    Snapshot  _snapShot;
    std::shared_ptr<attribute::Interlock> _interlock;
};

}
