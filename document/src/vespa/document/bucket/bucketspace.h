// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdint>
#include <functional>

namespace vespalib {
    class asciistream;
}

namespace document {

class BucketSpace {
public:
    using Type = uint64_t;

    constexpr BucketSpace(const BucketSpace&) noexcept = default;
    constexpr BucketSpace& operator=(const BucketSpace&) noexcept = default;
    constexpr explicit BucketSpace(Type id) noexcept : _id(id) {}

    constexpr bool operator <(const BucketSpace& bucket) const noexcept { return _id < bucket._id; }
    constexpr bool operator==(const BucketSpace& bucket) const noexcept { return _id == bucket._id; }
    constexpr bool operator!=(const BucketSpace& bucket) const noexcept { return _id != bucket._id; }

    constexpr Type getId() const noexcept { return _id; }
    constexpr bool valid() const noexcept { return (_id != 0); }
    vespalib::string toString() const;

    struct hash {
        size_t operator () (const BucketSpace& bs) const noexcept {
            return std::hash<Type>()(bs.getId());
        }
    };

    /*
     * Temporary placeholder value while wiring in use of BucketSpace in APIs.
     */
    static constexpr BucketSpace placeHolder() noexcept { return BucketSpace(1); }
    static constexpr BucketSpace invalid() noexcept { return BucketSpace(0); }
private:
    Type _id;
};

vespalib::asciistream& operator<<(vespalib::asciistream&, const BucketSpace&);
std::ostream& operator<<(std::ostream&, const BucketSpace&);

}
