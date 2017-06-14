// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage {
namespace lib {
    class Distribution;
}
namespace distributor {

class ActiveList;

struct ActiveCopy {
    uint16_t nodeIndex;
    vespalib::string reason;

    ActiveCopy() : nodeIndex(0xffff), reason(0) {}
    ActiveCopy(uint16_t index, vespalib::stringref r)
        : nodeIndex(index), reason(r) {}

    static ActiveList calculate(
            const std::vector<uint16_t>& idealState,
            const lib::Distribution&, BucketDatabase::Entry&);
};

class ActiveList : public vespalib::Printable {
    std::vector<ActiveCopy> _v;

public:
    ActiveList() {}
    ActiveList(std::vector<ActiveCopy>& v) { _v.swap(v); }

    ActiveCopy& operator[](size_t i) { return _v[i]; }
    const ActiveCopy& operator[](size_t i) const { return _v[i]; }
    bool contains(uint16_t) const;
    bool empty() const { return _v.empty(); }
    size_t size() const { return _v.size(); }
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
};

} // distributor
} // storage
