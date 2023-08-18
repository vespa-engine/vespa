// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ideal_service_layer_nodes_bundle.h"
#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage::lib { class Distribution; }
namespace storage::distributor {

class ActiveList;
struct ActiveStateOrder;

class ActiveCopy {
    using Index = IdealServiceLayerNodesBundle::Index;
    using Node2Index = IdealServiceLayerNodesBundle::Node2Index;
public:
    constexpr ActiveCopy() noexcept
        : _nodeIndex(Index::invalid()),
          _ideal(Index::invalid()),
          _doc_count(0),
          _ready(false),
          _active(false)
    { }
    ActiveCopy(uint16_t node, const BucketCopy & copy, uint16_t ideal) noexcept
        : _nodeIndex(node),
          _ideal(ideal),
          _doc_count(copy.getDocumentCount()),
          _ready(copy.ready()),
          _active(copy.active())
    { }

    vespalib::string getReason() const;
    friend std::ostream& operator<<(std::ostream& out, const ActiveCopy& e);

    static ActiveList calculate(const Node2Index & idealState, const lib::Distribution&,
                                const BucketDatabase::Entry&, uint32_t max_activation_inhibited_out_of_sync_groups);
    uint16_t nodeIndex() const noexcept { return _nodeIndex; }
private:
    friend ActiveStateOrder;
    bool valid_ideal() const noexcept { return _ideal < Index::invalid(); }
    uint16_t _nodeIndex;
    uint16_t _ideal;
    uint32_t _doc_count;
    bool     _ready;
    bool     _active;
};

class ActiveList : public vespalib::Printable {
public:
    ActiveList() noexcept {}
    ActiveList(std::vector<ActiveCopy>&& v) noexcept : _v(std::move(v)) { }

    const ActiveCopy& operator[](size_t i) const noexcept { return _v[i]; }
    [[nodiscard]] bool contains(uint16_t) const noexcept;
    [[nodiscard]] bool empty() const noexcept { return _v.empty(); }
    size_t size() const noexcept { return _v.size(); }
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
private:
    std::vector<ActiveCopy> _v;
};

}
