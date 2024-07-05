// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributemanager.h"
#include "attribute_read_guard.h"
#include "attributecontext.h"
#include "attributefactory.h"
#include "attrvector.h"
#include "interlock.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <condition_variable>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attributemanager");

using vespalib::string;
using vespalib::IllegalStateException;
using search::attribute::IAttributeContext;

namespace {

std::mutex baseDirLock;
std::condition_variable baseDirCond;
using BaseDirSet = std::set<string>;
BaseDirSet baseDirSet;

void
waitBaseDir(const string &baseDir)
{
    if (baseDir.empty()) { return; }
    std::unique_lock<std::mutex> guard(baseDirLock);
    bool waited = false;

    auto it = baseDirSet.find(baseDir);
    while (it != baseDirSet.end()) {
        if (!waited) {
            waited = true;
            LOG(debug, "AttributeManager: Waiting for basedir %s to be available", baseDir.c_str());
        }
        baseDirCond.wait(guard);
        it = baseDirSet.find(baseDir);
    }

    baseDirSet.insert(baseDir);
    if (waited) {
        LOG(debug, "AttributeManager: basedir %s available", baseDir.c_str());
    }
}

void
dropBaseDir(const string &baseDir)
{
    if (baseDir.empty())
        return;
    std::lock_guard<std::mutex> guard(baseDirLock);

    auto it = baseDirSet.find(baseDir);
    if (it == baseDirSet.end()) {
        LOG(error, "AttributeManager: Cannot drop basedir %s, already dropped", baseDir.c_str());
    } else {
        baseDirSet.erase(it);
    }
    baseDirCond.notify_all();
}

}

namespace search {

AttributeManager::AttributeManager()
    : _attributes(),
      _loadLock(),
      _baseDir(),
      _snapShot(),
      _interlock(std::make_shared<attribute::Interlock>())
{
    LOG(debug, "New attributeManager %p", static_cast<const void *>(this));
}


AttributeManager::AttributeManager(const string & baseDir)
    :  _attributes(),
       _loadLock(),
       _baseDir(baseDir),
       _snapShot(),
       _interlock(std::make_shared<attribute::Interlock>())
{
    LOG(debug, "New attributeManager %p, baseDir %s", static_cast<const void *>(this), baseDir.c_str());
    waitBaseDir(baseDir);
}


void
AttributeManager::setBaseDir(const string & base)
{
    dropBaseDir(_baseDir);
    _baseDir = base;
    LOG(debug, "attributeManager %p new baseDir %s", static_cast<const void *>(this), _baseDir.c_str());
    waitBaseDir(base);
}


AttributeManager::~AttributeManager()
{
    _attributes.clear();
    LOG(debug, "delete attributeManager %p baseDir %s", static_cast<const void *>(this), _baseDir.c_str());
    dropBaseDir(_baseDir);
}


uint64_t AttributeManager::getMemoryFootprint() const
{
    uint64_t sum(0);
    for (const auto& elem : _attributes) {
        sum += elem.second->getStatus().getAllocated();
    }

    return sum;
}

const AttributeManager::VectorHolder *
AttributeManager::findAndLoadAttribute(const string & name) const
{
    const VectorHolder * loadedVector(nullptr);
    auto found = _attributes.find(name);
    if (found != _attributes.end()) {
        AttributeVector & vec = *found->second;
        if ( ! vec.isLoaded() ) {
            std::lock_guard<std::mutex> loadGuard(_loadLock);
            if ( ! vec.isLoaded() ) {
                vec.load();
            } else {
                LOG(debug, "Multi load of %s prevented by double checked locking.", vec.getBaseFileName().c_str());
            }
        }
        loadedVector = & found->second;
    }
    return loadedVector;
}


const AttributeManager::VectorHolder *
AttributeManager::getAttributeRef(const string & name) const
{
    return findAndLoadAttribute(name);
}

AttributeGuard::UP
AttributeManager::getAttribute(const string & name) const
{
    const VectorHolder * vh = findAndLoadAttribute(name);
    if ( vh != nullptr ) {
        return std::make_unique<AttributeGuard>(*vh);
    }
    return std::make_unique<AttributeGuard>();
}

std::unique_ptr<attribute::AttributeReadGuard>
AttributeManager::getAttributeReadGuard(const string &name, bool stableEnumGuard) const
{
    const VectorHolder * vh = findAndLoadAttribute(name);
    if (vh != nullptr) {
        return (*vh)->makeReadGuard(stableEnumGuard);
    } else {
        return {};
    }
}

bool
AttributeManager::add(const AttributeManager::VectorHolder & vector)
{
    bool retval(true);
    auto found = _attributes.find(vector->getName());
    if (found == _attributes.end()) {
        vector->setInterlock(_interlock);
        _attributes[vector->getName()] = vector;
        retval = true;
    }
    return retval;
}

void
AttributeManager::getAttributeList(AttributeList & list) const
{
    list.reserve(_attributes.size());
    for (const auto& elem : _attributes) {
        list.emplace_back(elem.second);
    }
}

IAttributeContext::UP
AttributeManager::createContext() const
{
    return std::make_unique<AttributeContext>(*this);
}

string
AttributeManager::createBaseFileName(const string & name) const
{
    vespalib::string dir = getBaseDir();
    if ( ! getSnapshot().dirName.empty()) {
        dir += "/";
        dir += getSnapshot().dirName;
    }
    return attribute::BaseName(dir, name);
}

bool
AttributeManager::addVector(const string & name, const Config & config)
{
    bool retval = false;
    AttributeGuard::UP vector_owner(getAttribute(name));
    AttributeGuard &vector(*vector_owner);

    if (vector.valid()) {
        if ((vector->getInternalBasicType() == config.basicType())  &&
            (vector->getInternalCollectionType() == config.collectionType()))
        {
            retval = true;
        } else {
            LOG(error, "Attribute Vector '%s' has type conflict", name.c_str());
        }
    } else {
        auto found = _attributes.find(name);
        if (found != _attributes.end()) {
            const VectorHolder & vh(found->second);
            if ( vh.get() &&
                 (vh->getInternalBasicType() == config.basicType()) &&
                 (vh->getInternalCollectionType() == config.collectionType()))
            {
                retval = true;
            }
        }
        if (! retval ) {
            string baseFileName = createBaseFileName(name);
            VectorHolder vh(AttributeFactory::createAttribute(baseFileName, config));
            assert(vh.get());
            if (vh->load()) {
                assert(vh->getInternalBasicType() == config.basicType());
                assert(vh->getInternalCollectionType() == config.collectionType());
                retval = add(vh);
            } else {
                retval = add(vh);
            }
        }
    }
    return retval;
}

void
AttributeManager::asyncForAttribute(const vespalib::string &, std::unique_ptr<attribute::IAttributeFunctor>) const {
    throw std::runtime_error("search::AttributeManager::asyncForAttribute should never be called.");
}

std::shared_ptr<attribute::ReadableAttributeVector>
AttributeManager::readable_attribute_vector(const string& name) const
{
    const auto* attr = findAndLoadAttribute(name);
    if (attr) {
        return *attr;
    }
    return {};
}

}
