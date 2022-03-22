// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::WeightedSetFieldValue
 * \ingroup fieldvalue
 *
 * \brief A fieldvalue containing fieldvalue <-> weight mappings.
 */
#pragma once

#include "collectionfieldvalue.h"
#include "mapfieldvalue.h"
#include "intfieldvalue.h"
#include <map>

namespace document {

class WeightedSetFieldValue final : public CollectionFieldValue
{
public:
    struct FieldValuePtrOrder {
        template<typename T> bool operator()(const T& s1, const T& s2) const
            { return *s1 < *s2; }
    };
    typedef MapFieldValue WeightedFieldValueMap;

private:
    std::shared_ptr<const MapDataType> _map_type;
    WeightedFieldValueMap _map;

    void verifyKey(const FieldValue & key);
    bool addValue(const FieldValue& fval) override { return add(fval, 1); }
    bool containsValue(const FieldValue& val) const override;
    bool removeValue(const FieldValue& val) override;
    fieldvalue::ModificationStatus onIterateNested(PathRange nested, fieldvalue::IteratorHandler& handler) const override;
public:
    typedef std::unique_ptr<WeightedSetFieldValue> UP;

    /**
     * @param wsetType Type of the weighted set. Must be a WeightedSetDataType,
     *                 but does not enforce type compile time so it will be
     *                 easier to create instances using field's getDataType().
     */
    WeightedSetFieldValue(const DataType &wsetType);
    WeightedSetFieldValue(const WeightedSetFieldValue &);
    WeightedSetFieldValue & operator = (const WeightedSetFieldValue &);
    WeightedSetFieldValue(WeightedSetFieldValue &&) = default;
    WeightedSetFieldValue & operator = (WeightedSetFieldValue &&) = default;
    ~WeightedSetFieldValue() override;

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    /**
     * Add an item to the weighted set with the given weight. If removeIfZero
     * is set in the data type and weight is zero, the new item will not be
     * added and any existing item for the key will be immediately removed.
     */
    bool add(const FieldValue&, int32_t weight = 1);
    /**
     * Add an item to the weighted set, but do not erase the item if the
     * weight is zero and removeIfZero is set in the weighted set's type.
     */
    bool addIgnoreZeroWeight(const FieldValue&, int32_t weight = 1);
    void push_back(FieldValue::UP, int32_t weight);
    void increment(const FieldValue& fval, int val = 1);
    void decrement(const FieldValue& fval, int val = 1) { increment(fval, -1*val); }
    int32_t get(const FieldValue&, int32_t defaultValue = 0) const;

    bool isEmpty() const override { return _map.isEmpty(); }
    size_t size() const override { return _map.size(); }
    void clear() override { _map.clear(); }
    void reserve(size_t sz) { _map.reserve(sz); }
    void resize(size_t sz) { _map.resize(sz); }

    FieldValue& assign(const FieldValue&) override;
    WeightedSetFieldValue* clone() const override { return new WeightedSetFieldValue(*this); }
    int compare(const FieldValue&) const override;
    void printXml(XmlOutputStream& out) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    // Implements iterating through internal content.
    typedef WeightedFieldValueMap::const_iterator const_iterator;
    typedef WeightedFieldValueMap::iterator iterator;

    const_iterator begin() const { return _map.begin(); }
    iterator begin() { return _map.begin(); }

    const_iterator end() const { return _map.end(); }
    iterator end() { return _map.end(); }

    const_iterator find(const FieldValue& fv) const;
    iterator find(const FieldValue& fv);
};

} // document

