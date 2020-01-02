// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <map>
#include <memory>

namespace search::attribute::test {

using MockGidToLidMap = std::map<document::GlobalId, uint32_t>;

struct MockGidToLidMapper : public search::IGidToLidMapper {
    const MockGidToLidMap &_map;

    MockGidToLidMapper(const MockGidToLidMap &map)
        : _map(map)
    {
    }

    void foreach(const search::IGidToLidMapperVisitor &visitor) const override {
        for (const auto &kv : _map) {
            if (kv.second != 0) {
                visitor.visit(kv.first, kv.second);
            }
        }
    }
};

struct MockGidToLidMapperFactory : public search::IGidToLidMapperFactory {
    MockGidToLidMap _map;

    std::unique_ptr<search::IGidToLidMapper> getMapper() const override {
        return std::make_unique<MockGidToLidMapper>(_map);
    }
};

}
