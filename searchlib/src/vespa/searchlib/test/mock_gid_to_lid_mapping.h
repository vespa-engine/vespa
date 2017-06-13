// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <map>
#include <memory>

namespace search {
namespace attribute {
namespace test {

using MockGidToLidMap = std::map<document::GlobalId, uint32_t>;

struct MockGidToLidMapper : public search::IGidToLidMapper {
    const MockGidToLidMap &_map;

    MockGidToLidMapper(const MockGidToLidMap &map)
        : _map(map)
    {
    }

    uint32_t mapGidToLid(const document::GlobalId &gid) const override {
        auto itr = _map.find(gid);
        if (itr != _map.end()) {
            return itr->second;
        } else {
            return 0u;
        }
    }
};

struct MockGidToLidMapperFactory : public search::IGidToLidMapperFactory {
    MockGidToLidMap _map;

    std::unique_ptr<search::IGidToLidMapper> getMapper() const override {
        return std::make_unique<MockGidToLidMapper>(_map);
    }
};

} // test
} // attribute
} // search
