// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::attribute {

/**
 * Search iterator used to match a multi-term query operator against a single value attribute.
 *
 * The caller must provide a hash map (token -> weight) containing all tokens in the multi-term operator.
 * In doSeek() the attribute value for the docid is matched against the tokens hash map.
 *
 * @tparam WrapperType Type that wraps an attribute vector and provides access to the attribute value for a given docid.
 */
template <typename WrapperType>
class MultiTermFilter final : public queryeval::SearchIterator
{
public:
    using Key = typename WrapperType::TokenT;
    using TokenMap = vespalib::hash_map<Key, int32_t, vespalib::hash<Key>, std::equal_to<Key>, vespalib::hashtable_base::and_modulator>;

private:
    fef::TermFieldMatchData& _tfmd;
    WrapperType _attr;
    TokenMap _map;
    int32_t _weight;

public:
    MultiTermFilter(fef::TermFieldMatchData& tfmd,
                    WrapperType attr,
                    TokenMap&& map);

    void and_hits_into(BitVector& result, uint32_t begin_id) override;
    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    void visitMembers(vespalib::ObjectVisitor&) const override {}
};

}
