// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "collectiontype.h"
#include "basictype.h"
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <ostream>
#include <span>
#include <vector>

namespace search {
    class IDocidPostingStore;
    class IDocidWithWeightPostingStore;
    class QueryTermSimple;
}

namespace search::tensor {
    class ITensorAttribute;
}

namespace search::attribute {

class IMultiValueAttribute;
class ISearchContext;
class SearchContextParams;

/**
 * This class is used to store a value and a weight.
 * It is used when getting content from a weighted set attribute vector.
 *
 * @param T the type of the value stored in this object
 **/
template <typename T>
class WeightedType
{
private:
    T       _value;
    int32_t _weight;

public:
    WeightedType() noexcept : _value(T()), _weight(1) { }
    WeightedType(T value_, int32_t weight_ = 1) noexcept : _value(value_), _weight(weight_) { }
    const T & getValue() const { return _value; }
    const T & value() const { return _value; }
    void setValue(const T & v) { _value = v; }
    int32_t getWeight()  const { return _weight; }
    int32_t weight()  const { return _weight; }
    void setWeight(int32_t w)  { _weight = w; }
    bool operator==(const WeightedType & rhs) const {
        return _value == rhs._value && _weight == rhs._weight;
    }
};

template <typename T>
std::ostream&
operator<<(std::ostream& os, const WeightedType<T>& value)
{
    os << "{" << value.value() << "," << value.weight() << "}";
    return os;
}

/**
 * This is a read interface used to access the content of an attribute vector.
 **/
class IAttributeVector
{
public:
    using SP = std::shared_ptr<IAttributeVector>;
    using DocId = uint32_t;
    using EnumHandle = uint32_t;
    using largeint_t = int64_t;
    using WeightedFloat = WeightedType<double>;
    using WeightedInt = WeightedType<largeint_t>;
    using WeightedEnum = WeightedType<EnumHandle>;
    using WeightedConstChar = WeightedType<const char *>;
    using WeightedString = WeightedType<std::string>;
    using EnumRefs = std::span<const vespalib::datastore::AtomicEntryRef>;

    /**
     * Returns the name of this attribute vector.
     *
     * @return attribute name
     **/
    virtual const std::string & getName() const = 0;

    std::string_view getNamePrefix() const {
        std::string_view name = getName();
        return name.substr(0, name.find('.'));
    }

    /**
     * Returns the number of documents stored in this attribute vector.
     *
     * @return number of documents
     **/
    virtual uint32_t getNumDocs() const = 0;

    /**
     * Returns the number of values stored for the given document.
     *
     * @return number of values
     * @param doc document identifier
     **/
    virtual uint32_t getValueCount(uint32_t doc) const = 0;

    /**
     * Returns the maximum number of values stored for any document.
     *
     * @return maximum number of values
     **/
    virtual uint32_t getMaxValueCount() const = 0;

    /**
     * Returns the first value stored for the given document as an integer.
     *
     * @param docId document identifier
     * @return the integer value
     **/
    virtual largeint_t getInt(DocId doc) const = 0;

    /**
     * Returns the first value stored for the given document as a floating point number.
     *
     * @param docId document identifier
     * @return the floating point value
     **/
    virtual double getFloat(DocId doc)   const = 0;

    /**
     * Return raw value.
     *
     * TODO: Consider accessing via new IRawAttribute interface class.
     */
    virtual std::span<const char> get_raw(DocId doc) const = 0;

    /**
     * Returns the first value stored for the given document as an enum value.
     *
     * @param docId document identifier
     * @return the enum value
     **/
    virtual EnumHandle getEnum(DocId doc)   const = 0;

    /**
     * Copies the values stored for the given document into the given buffer.
     *
     * @param docId document identifier
     * @param buffer content buffer to copy integer values into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, largeint_t * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values stored for the given document into the given buffer.
     *
     * @param docId document identifier
     * @param buffer content buffer to copy floating point values into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, double * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values stored for the given document into the given buffer.
     *
     * @param docId document identifier
     * @param buffer content buffer to copy string values into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
//    virtual uint32_t get(DocId docId, std::string * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values stored for the given document into the given buffer.
     *
     * @param docId document identifier
     * @param buffer content buffer to copy const char values into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, const char ** buffer, uint32_t sz) const = 0;

    /**
     * Copies the enum values stored for the given document into the given buffer.
     *
     * @param docId document identifier
     * @param buffer content object to copy enum into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, EnumHandle * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values and weights stored for the given document into the given buffer.
     * This method should only be invoked if @ref getCollectionType(docId) returns CollectionType::WEIGHTED_SET.
     *
     * @param docId document identifier
     * @param buffer content object to copy integer values and weights into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, WeightedInt * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values and weights stored for the given document into the given buffer.
     * This method should only be invoked if @ref getCollectionType(docId) returns CollectionType::WEIGHTED_SET.
     *
     * @param docId document identifier
     * @param buffer content object to copy floating point values and weights into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, WeightedFloat * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values and weights stored for the given document into the given buffer.
     * This method should only be invoked if @ref getCollectionType(docId) returns CollectionType::WEIGHTED_SET.
     *
     * @param docId document identifier
     * @param buffer content object to copy string values and weights into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, WeightedString * buffer, uint32_t sz) const = 0;

    /**
     * Copies the values and weights stored for the given document into the given buffer.
     * This method should only be invoked if @ref getCollectionType(docId) returns CollectionType::WEIGHTED_SET.
     *
     * @param docId document identifier
     * @param buffer content object to copy const char values and weights into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, WeightedConstChar * buffer, uint32_t sz) const = 0;

    /**
     * Copies the enum values and weights stored for the given document into the given buffer.
     * This method should only be invoked if @ref getCollectionType(docId) returns CollectionType::WEIGHTED_SET.
     *
     * @param docId document identifier
     * @param buffer content object to copy enum values and weights into
     * @param sz the size of the buffer
     * @return the number of values for this document
     **/
    virtual uint32_t get(DocId docId, WeightedEnum * buffer, uint32_t sz) const = 0;

    /**
     * Finds the enum value for the given string value.
     * This method will only have effect if @ref getBasicType() returns BasicType::STRING and
     * @ref hasEnum() returns true.
     *
     * @param value the string value to lookup.
     * @param e the handle in which to store the enum value.
     * @return true if found.
     **/
    virtual bool findEnum(const char * value, EnumHandle & e) const = 0;

    /**
     * Finds all enum values matching the given string value.
     * This method will only have effect if @ref getBasicType() returns BasicType::STRING and
     * @ref hasEnum() returns true.
     *
     * @param value the string value to lookup.
     * @return vector of EnumHandles, size 0 if no match found.
     **/
    virtual std::vector<EnumHandle> findFoldedEnums(const char * value) const = 0;

    /**
     * Given an enum handle, returns the string it refers to.
     * This method will only have effect if @ref getBasicType() returns BasicType::STRING and
     * @ref hasEnum() returns true.
     *
     * Effectively functions as the inverse of @ref findEnum(value, handle)
     *
     * @param e a valid enum handle
     * @return enum string value, or nullptr if attribute type does
     *         not support enum handle lookups.
     */
    virtual const char * getStringFromEnum(EnumHandle e) const = 0;

    /**
     * Creates a context for searching this attribute with the given term.
     * The search context is used to create the actual search iterator.
     *
     * @param term the term to search for.
     * @param params optional bitvector and diversity settings for the search.
     * @return the search context.
     **/
    virtual std::unique_ptr<ISearchContext> createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                                const SearchContextParams &params) const = 0;

    /**
     * Type-safe down-cast to an interface supporting direct access to posting lists with docids.
     *
     * @return posting store or nullptr if not supported.
     */
    virtual const IDocidPostingStore* as_docid_posting_store() const = 0;

    /**
     * Type-safe down-cast to an interface supporting direct access to posting lists with {docid, weight} tuples.
     *
     * @return posting store or nullptr if not supported.
     */
    virtual const IDocidWithWeightPostingStore *as_docid_with_weight_posting_store() const = 0;

    /**
     * Type-safe down-cast to a tensor attribute.
     *
     * @return tensor attribute or nullptr if not supported.
     */
    virtual const tensor::ITensorAttribute *asTensorAttribute() const = 0;

    /**
     * Type-safe down-cast to a multi-value attribute.
     *
     * @return multi-value attribute or nullptr if not supported.
     */
    virtual const IMultiValueAttribute* as_multi_value_attribute() const = 0;

    /**
     * Returns the basic type of this attribute vector.
     *
     * @return basic type
     **/
    virtual BasicType::Type getBasicType() const = 0;

    /**
     * Returns the number of bytes a single value in this attribute occupies.
     **/
    virtual size_t getFixedWidth() const = 0;

    /**
     * Returns the collection type of this attribute vector.
     *
     * @return collection type
     **/
    virtual CollectionType::Type getCollectionType() const = 0;

    /**
     * Returns whether this is an integer attribute.
     **/
    virtual bool isIntegerType() const {
        BasicType::Type t = getBasicType();
        return t == BasicType::BOOL ||
               t == BasicType::UINT2 ||
               t == BasicType::UINT4 ||
               t == BasicType::INT8 ||
               t == BasicType::INT16 ||
               t == BasicType::INT32 ||
               t == BasicType::INT64;
    }

    /**
     * Returns whether this is a floating point attribute.
     **/
    virtual bool isFloatingPointType() const {
        BasicType::Type t = getBasicType();
        return t == BasicType::FLOAT || t == BasicType::DOUBLE;
    }

    /**
     * Returns whether this is a string attribute.
     **/
    virtual bool isStringType() const {
        return getBasicType() == BasicType::STRING;
    }


    virtual bool isPredicateType() const { return getBasicType() == BasicType::PREDICATE; }
    virtual bool isTensorType() const { return getBasicType() == BasicType::TENSOR; }
    virtual bool isReferenceType() const { return getBasicType() == BasicType::REFERENCE; }
    virtual bool is_raw_type() const noexcept {
        BasicType::Type t = getBasicType();
        return t == BasicType::RAW ||
               t == BasicType::STRING;
    }

    /**
     * Returns whether this is a multi value attribute.
     **/
    virtual bool hasMultiValue() const {
        return getCollectionType() != CollectionType::SINGLE;
    }

    /**
     * Returns whether this is a weighted set attribute.
     **/
    virtual bool hasWeightedSetType() const {
        return getCollectionType() == CollectionType::WSET;
    }

    /**
     * Returns whether this attribute vector has underlying enum values.
     *
     * @return true if it has enum values.
     **/
    virtual bool hasEnum() const = 0;

    /**
     * Returns whether the attribute vector is a filter attribute.
     *
     * @return true if attribute vector is a filter attribute.
     */
    virtual bool getIsFilter() const = 0;

    /**
     * Returns whether the attribute vector is marked as fast search.
     *
     * @return true if attribute vector is marked as fast search.
     */
    virtual bool getIsFastSearch() const = 0;

    /**
     * Returns the committed docid limit for the attribute.
     *
     * @return committed docid limit for the attribute.
     */
    virtual uint32_t getCommittedDocIdLimit() const = 0;

    /*
     * Returns whether the current attribute vector is an imported attribute
     * vector.
     */
    virtual bool isImported() const = 0;

    /*
     * Returns whether the match setting for the attribute is uncased.
     * This is only relevant for string attributes (i.e. when isStringType()
     * returns true). The default for string attributes is uncased matching.
     */
    virtual bool has_uncased_matching() const noexcept { return true; }

    virtual bool is_sortable() const noexcept = 0;

    /**
     * Will serialize the values for the documentid in ascending order. The serialized form can be used by memcmp and
     * sortorder will be preserved.
     * @param doc The document id to serialize for.
     * @param serTo The buffer to serialize into.
     * @param available. Number of bytes available in the serialization buffer.
     * @param bc An optional converter to use.
     * @return The number of bytes serialized, -1 if not enough space.
     */
    long serializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc=nullptr) const {
        return onSerializeForAscendingSort(doc, serTo, available, bc);
    }
    /**
     * Will serialize the values for the documentid in descending order. The serialized form can be used by memcmp and
     * sortorder will be preserved.
     * @param doc The document id to serialize for.
     * @param serTo The buffer to serialize into.
     * @param available. Number of bytes available in the serialization buffer.
     * @param bc An optional converter to use.
     * @return The number of bytes serialized, -1 if not enough space.
     */
    long serializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc=nullptr) const {
        return onSerializeForDescendingSort(doc, serTo, available, bc);
    }

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~IAttributeVector() = default;

    /**
     * This method is used to simulate sparseness in the single value attributes.
     * @param doc The document id to verify if attribute has a undefined value for this document.
     * @return true if value is undefined.
     */
    virtual bool isUndefined(DocId doc) const { (void) doc; return false; }

    /**
     * Will return a readonly view of any single value enumeration. If not applicable an empty one will be returned.
     *
     * @return returns a readonly enumrefs view with entries equal to number of docs committed.
     */
    virtual EnumRefs make_enum_read_view() const noexcept {
        return EnumRefs();
    }

private:
    virtual long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const = 0;
    virtual long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const = 0;

};

}
