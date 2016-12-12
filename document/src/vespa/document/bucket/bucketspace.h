// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    BucketSpace(const BucketSpace&) noexcept = default;
    BucketSpace& operator=(const BucketSpace&) noexcept = default;
    explicit BucketSpace(Type id) noexcept : _id(id) {}

    bool operator <(const BucketSpace& bucket) const noexcept { return _id < bucket._id; }
    bool operator==(const BucketSpace& bucket) const noexcept { return _id == bucket._id; }
    bool operator!=(const BucketSpace& bucket) const noexcept { return _id != bucket._id; }

    Type getId() const noexcept { return _id; }
    vespalib::string toString() const;

    struct hash {
        size_t operator () (const BucketSpace& bs) const {
            return std::hash<Type>()(bs.getId());
        }
    };
private:
    Type _id;
};

vespalib::asciistream& operator<<(vespalib::asciistream&, const BucketSpace&);

}
