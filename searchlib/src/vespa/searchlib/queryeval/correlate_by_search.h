// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "correlated_rows.h"
#include "same_element_search.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <optional>

namespace search::queryeval {

struct BoundAttribute {
    uint32_t column;
    const attribute::IAttributeVector* attribute;
};

struct ExactAttributeValues {
    const attribute::IAttributeVector* attribute;
    std::vector<std::string> values;
};

struct FirstPresentSelection {
    uint32_t clause;
    uint32_t presence;
    uint32_t column;
    std::vector<std::string> values;
};

class CorrelateBySearch : public SearchIterator {
    struct AttributeValues {
        const attribute::IAttributeVector* attribute;
        std::vector<BindingValue> values;
    };
    std::vector<std::unique_ptr<SameElementSearch>> _children;
    std::vector<std::vector<BoundAttribute>> _bindings;
    std::vector<std::optional<std::vector<std::string>>> _domains;
    std::vector<std::vector<ExactAttributeValues>> _exact_values;
    std::vector<AttributeValues> _attributes;
    std::vector<BindingTable> _tables;
    std::vector<BindingTable> _presence_tables;
    std::vector<BindingTable*> _child_tables;
    std::vector<bool> _presence_children;
    std::vector<FirstPresentSelection> _first_present;
    CorrelatedRows _join;
    bool _strict;
    uint32_t _loaded_docid;
    std::vector<attribute::IAttributeVector::largeint_t> _integers;
    std::vector<const char*> _strings;

    bool check_docid_match(uint32_t docid);
    void read_attributes(uint32_t docid);
    void read_matching_rows(uint32_t docid);
    const std::vector<BindingValue>& values_for(const attribute::IAttributeVector* attribute) const;

public:
    CorrelateBySearch(std::vector<std::unique_ptr<SameElementSearch>> children,
                     std::vector<std::vector<BoundAttribute>> bindings,
                     std::vector<std::optional<std::vector<std::string>>> domains, bool strict,
                     std::vector<std::vector<ExactAttributeValues>> exact_values = {},
                     std::vector<FirstPresentSelection> first_present = {});
    ~CorrelateBySearch() override;
    void initRange(uint32_t begin, uint32_t end) override;
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    const std::vector<BindingRow>& find_bindings(uint32_t docid);
    const std::vector<BindingRow>& find_bindings(uint32_t docid, std::span<const uint32_t> projection);
};

} // namespace search::queryeval
