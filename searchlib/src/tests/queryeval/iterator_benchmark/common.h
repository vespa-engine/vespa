// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/bitvector.h>
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

struct HitSpec {
    uint32_t term_value;
    uint32_t num_hits;
    HitSpec(uint32_t term_value_in, uint32_t num_hits_in) : term_value(term_value_in), num_hits(num_hits_in) {}
};

using TermVector = std::vector<uint32_t>;

class HitSpecs {
private:
    std::vector<HitSpec> _specs;
    uint32_t _next_term_value;

public:
    HitSpecs(uint32_t first_term_value)
        : _specs(), _next_term_value(first_term_value)
    {
    }
    TermVector add(uint32_t num_terms, uint32_t hits_per_term) {
        TermVector res;
        for (uint32_t i = 0; i < num_terms; ++i) {
            uint32_t term_value = _next_term_value++;
            _specs.push_back({term_value, hits_per_term});
            res.push_back(term_value);
        }
        return res;
    }
    size_t size() const { return _specs.size(); }
    auto begin() const { return _specs.begin(); }
    auto end() const { return _specs.end(); }
};

BitVector::UP random_docids(uint32_t docid_limit, uint32_t count);

}
