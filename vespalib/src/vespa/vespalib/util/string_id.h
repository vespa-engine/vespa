// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/allocator.h>
#include <cstdint>
#include <vector>

namespace vespalib {

class SharedStringRepo;

/**
 * A typed integer value representing the identity of a string stored
 * in the global SharedStringRepo. This class is a simple wrapper with
 * no lifetime management of the mapping between string value and
 * string id. For a string_id to be valid, it needs to be owned by at
 * least one SharedStringRepo::Handle or SharedStringRepo::Handles
 * object. This is similar to how std::enable_shared_from_this works;
 * the string_id acts like a reference to a mapping from a string to a
 * numerical value (without ownership) and Handle/Handles act like
 * shared pointers to the same mapping (with shared ownership).
 **/
class string_id {
    friend class ::vespalib::SharedStringRepo;
private:
    uint32_t _id;
    explicit constexpr string_id(uint32_t value_in) noexcept : _id(value_in) {}
public:
    constexpr string_id() noexcept : _id(0) {}
    constexpr string_id(const string_id &) noexcept = default;
    constexpr string_id(string_id &&) noexcept = default;
    constexpr string_id &operator=(const string_id &) noexcept = default;
    constexpr string_id &operator=(string_id &&) noexcept = default;
    constexpr uint32_t hash() const noexcept { return _id; }
    constexpr uint32_t value() const noexcept { return _id; }
    // NB: not lexical sorting order, but can be used in maps
    constexpr bool operator<(const string_id &rhs) const noexcept { return (_id < rhs._id); }
    constexpr bool operator==(const string_id &rhs) const noexcept { return (_id == rhs._id); }
    constexpr bool operator!=(const string_id &rhs) const noexcept { return (_id != rhs._id); }
};

using StringIdVector = std::vector<string_id, vespalib::allocator_large<string_id>>;

}
