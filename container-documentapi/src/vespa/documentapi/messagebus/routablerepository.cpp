// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablerepository.h"
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".routablerepository");

namespace documentapi {

RoutableRepository::VersionMap::VersionMap() :
    _factoryVersions()
{ }

bool
RoutableRepository::VersionMap::putFactory(const vespalib::VersionSpecification &version, IRoutableFactory::SP factory)
{
    bool ret = _factoryVersions.find(version) != _factoryVersions.end();
    _factoryVersions[version] = factory;
    return ret;

}

IRoutableFactory::SP
RoutableRepository::VersionMap::getFactory(const vespalib::Version &version) const
{
    const vespalib::VersionSpecification versionSpec{version.getMajor(), version.getMinor(), version.getMicro()};

    std::vector< std::pair<vespalib::VersionSpecification, IRoutableFactory::SP> > candidates;
    for (auto & entry : _factoryVersions) {
        if (entry.first.compareTo(versionSpec) <= 0) {
            candidates.push_back(std::make_pair(entry.first, entry.second));
        }
    }
    if (candidates.empty()) {
        return IRoutableFactory::SP();
    }

    return std::max_element(candidates.begin(), candidates.end(),
                            [](auto & lhs, auto & rhs) { return lhs.first.compareTo(rhs.first) <= 0; })->second;
}

RoutableRepository::RoutableRepository(const LoadTypeSet& loadTypes) :
    _lock(),
    _factoryTypes(),
    _cache(),
    _loadTypes(loadTypes)
{
}

mbus::Routable::UP
RoutableRepository::decode(const vespalib::Version &version, mbus::BlobRef data) const
{
    if (data.size() == 0) {
        LOG(error, "Received empty byte array for deserialization.");
        return mbus::Routable::UP();
    }

    document::ByteBuffer in(data.data(), data.size());
    int type;
    in.getIntNetwork(type);
    IRoutableFactory::SP factory = getFactory(version, type);
    if (!factory) {
        LOG(error, "No routable factory found for routable type %d (version %s).",
            type, version.toString().c_str());
        return mbus::Routable::UP();
    }
    mbus::Routable::UP ret = factory->decode(in, _loadTypes);
    if (!ret) {
        LOG(error, "Routable factory failed to deserialize routable of type %d (version %s).",
            type, version.toString().c_str());

        std::ostringstream ost;
        document::StringUtil::printAsHex(ost, data.data(), data.size());
        LOG(error, "%s", ost.str().c_str());
        return mbus::Routable::UP();
    }
    return ret;
}

mbus::Blob
RoutableRepository::encode(const vespalib::Version &version, const mbus::Routable &obj) const
{
    uint32_t type = obj.getType();

    IRoutableFactory::SP factory = getFactory(version, type);
    if (!factory) {
        LOG(error, "No routable factory found for routable type %d (version %s).",
            type, version.toString().c_str());
        return mbus::Blob(0);
    }
    vespalib::GrowableByteBuffer out;
    out.putInt(obj.getType());
    if (!factory->encode(obj, out)) {
        LOG(error, "Routable factory failed to serialize routable of type %d (version %s).",
            type, version.toString().c_str());
        return mbus::Blob(0);
    }
    mbus::Blob ret(out.position());
    memcpy(ret.data(), out.getBuffer(), out.position());
    return ret;
}

void
RoutableRepository::putFactory(const vespalib::VersionSpecification &version,
                               uint32_t type, IRoutableFactory::SP factory)
{
    vespalib::LockGuard guard(_lock);
    if (_factoryTypes[type].putFactory(version, factory)) {
        _cache.clear();
    }
}

IRoutableFactory::SP
RoutableRepository::getFactory(const vespalib::Version &version, uint32_t type) const
{
    vespalib::LockGuard guard(_lock);
    CacheKey cacheKey(version, type);
    FactoryCache::const_iterator cit = _cache.find(cacheKey);
    if (cit != _cache.end()) {
        return cit->second;
    }
    TypeMap::const_iterator vit = _factoryTypes.find(type);
    if (vit == _factoryTypes.end()) {
        return IRoutableFactory::SP();
    }
    IRoutableFactory::SP factory = vit->second.getFactory(version);
    if (!factory) {
        return IRoutableFactory::SP();
    }
    _cache[cacheKey] = factory;
    return factory;
}

uint32_t
RoutableRepository::getRoutableTypes(const vespalib::Version &version, std::vector<uint32_t> &out) const
{
    vespalib::LockGuard guard(_lock);
    for (const auto & type :  _factoryTypes) {
        if (type.second.getFactory(version)) {
            out.push_back(type.first);
        }
    }
    return _factoryTypes.size();
}

}
