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

/**
 * Returns, for each variable, whether its values are compared without case.
 * This is the case when the variable is bound to at least one string attribute
 * with uncased matching, which is the default for string attributes.
 */
std::vector<bool> find_uncased_columns(size_t column_count, std::span<const std::vector<BoundAttribute>> bindings);

class CorrelateBySearch : public SearchIterator {
    struct AttributeValues {
        const attribute::IAttributeVector* attribute;
        // Values of uncased attributes, and of attributes bound to an uncased
        // variable, are lowercased before they are compared.
        bool fold;
        std::vector<BindingValue> values;
        std::vector<std::string> folded;
    };
    std::vector<std::unique_ptr<SameElementSearch>> _children;
    std::vector<std::vector<BoundAttribute>> _bindings;
    std::vector<std::optional<std::vector<std::string>>> _domains;
    std::vector<std::vector<ExactAttributeValues>> _exact_values;
    std::vector<bool> _uncased_columns;
    std::vector<AttributeValues> _attributes;
    // Index into _attributes for each binding and each exact-value filter.
    std::vector<std::vector<uint32_t>> _binding_attributes;
    std::vector<std::vector<uint32_t>> _exact_attributes;
    std::vector<BindingTable> _tables;
    std::vector<BindingTable> _presence_tables;
    std::vector<BindingTable*> _child_tables;
    std::vector<bool> _presence_children;
    std::vector<FirstPresentSelection> _first_present;
    std::vector<std::vector<BindingValue>> _priorities;
    CorrelatedRows _join;
    bool _strict;
    uint32_t _loaded_docid;
    uint32_t _rows_docid;
    std::vector<attribute::IAttributeVector::largeint_t> _integers;
    std::vector<const char*> _strings;
    BindingRow _row;
    std::vector<uint32_t> _elements;

    uint32_t attribute_index(const attribute::IAttributeVector* attribute, bool fold);
    void fold_literals();
    bool check_docid_match(uint32_t docid);
    void read_attributes(uint32_t docid);
    void read_matching_rows(uint32_t docid);
    bool make_row(size_t child, uint32_t element, BindingRow& row) const;

public:
    CorrelateBySearch(std::vector<std::unique_ptr<SameElementSearch>> children,
                     std::vector<std::vector<BoundAttribute>> bindings,
                     std::vector<std::optional<std::vector<std::string>>> domains, bool strict,
                     std::vector<std::vector<ExactAttributeValues>> exact_values = {},
                     std::vector<FirstPresentSelection> first_present = {});
    ~CorrelateBySearch() override;
    void initRange(uint32_t begin, uint32_t end) override;
    void doSeek(uint32_t docid) override;
    /**
     * Unpacks only the elements that take part in a correlated match. Presence
     * clauses of firstPresent are not unpacked, since they do not match anything.
     */
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    size_t visited_rows() const noexcept { return _join.visited_rows(); }
    const std::vector<BindingRow>& find_bindings(uint32_t docid);
    const std::vector<BindingRow>& find_bindings(uint32_t docid, std::span<const uint32_t> projection);
    /**
     * Appends the elements of one clause that take part in at least one correlated
     * match. The search must be positioned at docid. Adds nothing for a presence clause.
     */
    void find_correlated_elements(uint32_t docid, size_t child, std::vector<uint32_t>& dst);
    /** Whether each variable is compared without case. String values of these variables are lowercased. */
    const std::vector<bool>& uncased_columns() const noexcept { return _uncased_columns; }
};

} // namespace search::queryeval
