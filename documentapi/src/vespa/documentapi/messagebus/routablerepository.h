// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iroutablefactory.h"
#include <vespa/messagebus/blobref.h>
#include <vespa/vespalib/component/versionspecification.h>
#include <mutex>
#include <map>

namespace documentapi {

/**
 * This class encapsulates the logic required to map routable type and version to a corresponding {@link
 * RoutableFactory}. It is owned and accessed through a {@link DocumentProtocol} instance. This class uses a
 * factory cache to reduce the latency of matching version specifications to actual versions when resolving
 * factories.
 */
class RoutableRepository {
private:
    /**
     * Internal helper class that implements a map from {@link VersionSpecification} to {@link
     * RoutableFactory}.
     */
    class VersionMap {
    private:
        std::map<vespalib::VersionSpecification, IRoutableFactory::SP> _factoryVersions;

    public:
        VersionMap();
        bool putFactory(const vespalib::VersionSpecification &version, IRoutableFactory::SP factory);
        IRoutableFactory::SP getFactory(const vespalib::Version &version) const;
    };

    typedef std::pair<vespalib::Version, uint32_t>   CacheKey;
    typedef std::map<CacheKey, IRoutableFactory::SP> FactoryCache;
    typedef std::map<uint32_t, VersionMap>           TypeMap;

    mutable std::mutex   _lock;
    TypeMap              _factoryTypes;
    mutable FactoryCache _cache;

public:
    RoutableRepository(const RoutableRepository &) = delete;
    RoutableRepository & operator = (const RoutableRepository &) = delete;
    /**
     * Constructs a new routable repository.
     */
    RoutableRepository();

    /**
     * Decodes a {@link Routable} from the given byte array. This uses the content of the byte array to
     * dispatch the decode request to the appropriate {@link RoutableFactory} that was previously registered.
     *
     * If a routable can not be decoded, this method returns an empty blob.
     *
     * @param version The version of the encoded routable.
     * @param data    The byte array containing the encoded routable.
     * @return The decoded routable.
     */
    mbus::Routable::UP decode(const vespalib::Version &version, mbus::BlobRef data) const;

    /**
     * Encodes a {@link Routable} into a byte array. This dispatches the encode request to the appropriate
     * {@link RoutableFactory} that was previously registered.
     *
     * If a routable can not be encoded, this method returns an empty byte array.
     *
     * @param version The version to encode the routable as.
     * @param obj     The routable to encode.
     * @return The byte array containing the encoded routable.
     */
    mbus::Blob encode(const vespalib::Version &version, const mbus::Routable &obj) const;

    /**
     * Registers a routable factory for a given version and routable type.
     *
     * @param version The version specification that the given factory supports.
     * @param type    The routable type that the given factory supports.
     * @param factory The routable factory to register.
     */
    void putFactory(const vespalib::VersionSpecification &version,
                    uint32_t type, IRoutableFactory::SP factory);

    /**
     * Returns the routable factory for a given version and routable type.
     *
     * @param version The version that the factory must support.
     * @param type    The routable type that the factory must support.
     * @return The routable factory matching the criteria, or null.
     */
    IRoutableFactory::SP getFactory(const vespalib::Version &version, uint32_t type) const;

    /**
     * Returns a list of routable types that support the given version.
     *
     * @param version The version to return types for.
     * @param out     The list to write to.
     * @return The number of supported types.
     */
    uint32_t getRoutableTypes(const vespalib::Version &version, std::vector<uint32_t> &out) const;
};

}

