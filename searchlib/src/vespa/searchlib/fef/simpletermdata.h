// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"
#include "itermdata.h"
#include "simpletermfielddata.h"
#include <vespa/searchlib/query/weight.h>
#include <vector>
#include <cassert>

namespace search::fef {

/**
 * Static match data for a single unit (term/phrase/etc).
 **/
class SimpleTermData final : public ITermData
{
private:
    query::Weight   _weight;
    uint32_t        _numTerms;
    uint32_t        _uniqueId;
    std::optional<vespalib::string>  _query_tensor_name;
    std::vector<SimpleTermFieldData> _fields;

public:
    SimpleTermData() noexcept;
    SimpleTermData(const ITermData &rhs);
    SimpleTermData(const SimpleTermData &);
    SimpleTermData(SimpleTermData &&) noexcept;
    SimpleTermData & operator=(SimpleTermData &&) noexcept;
    ~SimpleTermData() override;

    //----------- ITermData implementation ------------------------------------

    [[nodiscard]] query::Weight getWeight() const override { return _weight; }

    [[nodiscard]] uint32_t getPhraseLength() const override { return _numTerms; }

    [[nodiscard]] uint32_t getUniqueId() const override { return _uniqueId; }

    [[nodiscard]] std::optional<vespalib::string> query_tensor_name() const override { return _query_tensor_name; }

    [[nodiscard]] size_t numFields() const override { return _fields.size(); }

    [[nodiscard]] const ITermFieldData &field(size_t i) const override {
        return _fields[i];
    }

    [[nodiscard]] const ITermFieldData *lookupField(uint32_t fieldId) const override {
        for (size_t fieldIdx(0), m(numFields()); fieldIdx < m; ++fieldIdx) {
            const ITermFieldData &tfd = field(fieldIdx);
            if (tfd.getFieldId() == fieldId) {
                return &tfd;
            }
        }
        return nullptr;
    }

    //----------- Utility functions -------------------------------------------

    /**
     * Sets the term weight.
     **/
    SimpleTermData &setWeight(query::Weight weight) {
        _weight = weight;
        return *this;
    }

    /**
     * Sets the number of terms represented by this term data object.
     **/
    SimpleTermData &setPhraseLength(uint32_t numTerms) {
        _numTerms = numTerms;
        return *this;
    }

    /**
     * Set the unique id of this term. 0 means not set.
     *
     * @param id unique id or 0
     * @return this to allow chaining.
     **/
    SimpleTermData &setUniqueId(uint32_t id) {
        _uniqueId = id;
        return *this;
    }

    SimpleTermData &set_query_tensor_name(const vespalib::string &name) {
        _query_tensor_name = name;
        return *this;
    }

    /**
     * Add a new field to the set that is searched by this term.
     *
     * @return the newly added field
     * @param fieldId field id of the added field
     **/
    SimpleTermFieldData &addField(uint32_t fieldId) {
        _fields.emplace_back(fieldId);
        return _fields.back();
    }

    /**
     * Direct access to data for individual fields
     * @param i local index, must have: 0 <= i < numFields()
     */
    SimpleTermFieldData &field(size_t i) {
        return _fields[i];
    }

    /**
     * Obtain information about a specific field that may be searched
     * by this term. If the requested field is not searched by this
     * term, NULL will be returned.
     *
     * @return term field data, or NULL if not found
     **/
    SimpleTermFieldData *lookupField(uint32_t fieldId) {
        for (size_t fieldIdx(0), m(numFields()); fieldIdx < m; ++fieldIdx) {
            SimpleTermFieldData& tfd = field(fieldIdx);
            if (tfd.getFieldId() == fieldId) {
                return &tfd;
            }
        }
        return nullptr;
    }
};

/**
 * convenience adapter for easy iteration
 **/
class SimpleTermFieldRangeAdapter
{
    SimpleTermData& _ref;
    size_t _idx;
    size_t _lim;
public:
    explicit SimpleTermFieldRangeAdapter(SimpleTermData& ref)
        : _ref(ref), _idx(0), _lim(ref.numFields())
    {}

    [[nodiscard]] bool valid() const { return (_idx < _lim); }

    [[nodiscard]] SimpleTermFieldData& get() const  { return _ref.field(_idx); }

    void next() { assert(valid()); ++_idx; }
};

}
