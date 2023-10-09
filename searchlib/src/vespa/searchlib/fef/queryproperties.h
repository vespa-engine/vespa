// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace fef {

/**
 * This namespace is a placeholder for several structs, each
 * representing a query property with name and default value. All
 * property names defined here will have the prefix "vespa." and are
 * known by the feature execution framework. When accessing a query
 * property from a @ref Properties instance one should use the
 * property names defined here to perform the lookup. The query
 * properties are the set of properties available through the query
 * environment. These properties are denoted as rank properties in
 * other parts of the system.
 **/
namespace queryproperties {

namespace now {
    /**
     * Property indicating the time to be used for time-sensitive
     * relevancy computations. This affects the value returned by the
     * global feature 'now'. The time is given in seconds since epoch.
     **/
    struct SystemTime {

        /**
         * Property name.
         **/
        static const vespalib::string NAME;
    };

} // namespace now

} // namespace queryproperties
} // namespace fef
} // namespace search

