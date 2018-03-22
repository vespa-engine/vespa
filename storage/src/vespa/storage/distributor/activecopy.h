// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage::lib { class Distribution; }
namespace storage::distributor {

class ActiveList;

struct ActiveCopy {
public:
    ActiveCopy() : nodeIndex(-1), _ideal(-1), _ready(false), _trusted(false), _active(false) { }
    ActiveCopy(uint16_t node, BucketDatabase::Entry& e, const std::vector<uint16_t>& idealState);

    vespalib::string getReason() const;
    friend std::ostream& operator<<(std::ostream& out, const ActiveCopy& e);

    static ActiveList calculate(const std::vector<uint16_t>& idealState,
                                const lib::Distribution&, BucketDatabase::Entry&);

    uint16_t nodeIndex;
    uint16_t _ideal;
    bool     _ready;
    bool     _trusted;
    bool     _active;
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

}
