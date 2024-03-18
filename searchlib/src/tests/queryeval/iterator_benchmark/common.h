// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/schema.h>
#include <variant>

namespace search::queryeval::test {

using search::attribute::Config;
using search::index::Schema;

vespalib::string to_string(const Config& attr_config);

class FieldConfig {
private:
    std::variant<Config, Schema::IndexField> _cfg;

public:
    FieldConfig(const Config& attr_cfg_in) : _cfg(attr_cfg_in) {}
    FieldConfig(const Schema::IndexField& index_cfg_in) : _cfg(index_cfg_in) {}
    bool is_attr() const { return _cfg.index() == 0; }
    const Config& attr_cfg() const { return std::get<0>(_cfg); }
    Schema index_cfg() const {
        Schema res;
        res.addIndexField(std::get<1>(_cfg));
        return res;
    }
    vespalib::string to_string() const {
        return is_attr() ? search::queryeval::test::to_string(attr_cfg()) : "diskindex";
    }
};

enum class QueryOperator {
    Term,
    In,
    WeightedSet,
    DotProduct,
    And,
    Or
};

vespalib::string to_string(QueryOperator query_op);

}
