// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/common/mapnames.h>

namespace search::engine {

/**
 * A simple wrapper class used to hold multiple named collections of
 * properties.
 **/
class PropertiesMap
{
private:
    using Props = search::fef::Properties;
    using PropsMap = vespalib::hash_map<vespalib::string, Props>;

    static Props _emptyProperties;
    PropsMap     _propertiesMap;

    /**
     * Obtain a named collection of properties. This method will
     * return an empty collection of properties if the properties did
     * not exist.
     *
     * @param name name of properties
     * @return the properties
     **/
    const Props &lookup(vespalib::stringref name) const;

public:
    using ITR = PropsMap::const_iterator;

    PropertiesMap();
    PropertiesMap(uint32_t sz);
    PropertiesMap(const PropertiesMap &) = delete;
    PropertiesMap & operator=(const PropertiesMap &) = delete;
    ~PropertiesMap();

    /**
     * Obtain a named collection of properties. This method will
     * create the properties if they did not exist yet.
     *
     * @param name name of properties
     * @return the properties
     **/
    Props &lookupCreate(vespalib::stringref name);

    /**
     * Obtain the number of named collection of properties held by
     * this object.
     *
     * @return number of named collections of properties
     **/
    uint32_t size() const { return _propertiesMap.size(); }

    /**
     * Iterate the map.
     *
     * @return begin iterator
     **/
    ITR begin() const { return _propertiesMap.begin(); }

    /**
     * Iterate the map.
     *
     * @return end iterator
     **/
    ITR end() const { return _propertiesMap.end(); }

    /**
     * Obtain rank properties (used to tune ranking evaluation)
     *
     * @return rank properties
     **/
    const Props &rankProperties() const {
        return lookup(MapNames::RANK);
    }

    /**
     * Obtain feature overrides (used to hardwire the values of
     * features during ranking evaluation)
     *
     * @return feature overrides
     **/
    const Props &featureOverrides() const {
        return lookup(MapNames::FEATURE);
    }

    /**
     * Obtain properties used to define additional highlight terms to
     * be used during dynamic summary generation.
     *
     * @return highlight terms properties
     **/
    const Props &highlightTerms() const {
        return lookup(MapNames::HIGHLIGHTTERMS);
    }

    /**
     * Obtain match properties (used to tune match evaluation)
     *
     * @return match properties
     **/
    const Props &matchProperties() const {
        return lookup(MapNames::MATCH);
    }

    /**
     * Obtain cache properties (used to tune cache usage)
     *
     * @return cache properties
     **/
    const Props &cacheProperties() const {
        return lookup(MapNames::CACHES);
    }

    /**
     * Obtain model overrides
     *
     * @return model properties
     **/
    const Props &modelOverrides() const {
        return lookup(MapNames::MODEL);
    }

    /**
     * Obtain trace
     *
     * @return trace
     **/
    const Props &trace() const {
        return lookup(MapNames::TRACE);
    }
};

}
