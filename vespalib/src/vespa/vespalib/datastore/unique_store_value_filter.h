// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::datastore {

/*
 * Helper class for normalizing values inserted into unique store.
 */
template <typename EntryT>
class UniqueStoreValueFilter {
public:
    static const EntryT &filter(const EntryT &value) {
        return value;
    }
};

/*
 * Specialized helper class for normalizing floating point values
 * inserted into unique store.  Any type of NAN is normalized to a
 * specific one.
 */
template <typename EntryT>
class UniqueStoreFloatingPointValueFilter {
    static const EntryT normalized_nan;
public:
    static const EntryT &filter(const EntryT &value) {
        return std::isnan(value) ? normalized_nan : value;
    }
};

template <typename EntryT>
const EntryT UniqueStoreFloatingPointValueFilter<EntryT>::normalized_nan = -std::numeric_limits<EntryT>::quiet_NaN(); 

/*
 * Specialized helper class for normalizing float values inserted into unique store.
 * Any type of NAN is normalized to a specific one.
 */
template <>
class UniqueStoreValueFilter<float> : public UniqueStoreFloatingPointValueFilter<float> {
};

/*
 * Specialized helper class for normalizing double values inserted into unique store.
 * Any type of NAN is normalized to a specific one.
 */
template <>
class UniqueStoreValueFilter<double> : public UniqueStoreFloatingPointValueFilter<double> {
};

}
