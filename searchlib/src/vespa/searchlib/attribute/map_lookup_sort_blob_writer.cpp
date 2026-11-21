// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "map_lookup_sort_blob_writer.h"

#include "numeric_sort_blob_writer.h"
#include "string_sort_blob_writer.h"
#include "string_to_number.h"

#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>

#include <limits>

using search::common::FieldSortSpec;
using search::common::sortspec::MissingPolicy;
using vespalib::IllegalArgumentException;
using vespalib::Issue;
using vespalib::Stash;

namespace search::attribute {

namespace {

constexpr uint32_t npos = std::numeric_limits<uint32_t>::max();

/*
 * Finds the position of the selected key in the key array of a document.
 * The key attribute is enum backed, so the scan compares raw enum handles;
 * neither an enum store lookup nor a string compare is done per entry.
 */
class StringEnumKeyFinder {
    const IArrayEnumReadView* _view;
    uint32_t                  _key_handle;
    bool                      _usable; // false => key is not in the global enum dictionary, every document misses

public:
    StringEnumKeyFinder(const IAttributeVector& key_attr, const IArrayEnumReadView* view, const std::string& key)
        : _view(view), _key_handle(0), _usable(false) {
        IAttributeVector::EnumHandle handle = 0;
        if (key_attr.findEnum(key.c_str(), handle)) {
            _key_handle = handle;
            _usable = true;
        }
    }
    uint32_t position(uint32_t docid) const noexcept {
        if (!_usable) {
            return npos; // globally unknown key, no scan needed
        }
        auto keys = _view->get_values(docid);
        for (uint32_t i = 0; i < keys.size(); ++i) {
            if (keys[i].load_acquire().ref() == _key_handle) {
                return i;
            }
        }
        return npos;
    }
};

/*
 * Sort blob writer for a numeric value at the position of the selected key.
 * The selected value is logically a single valued optional scalar, hence the
 * scalar encoder is constructed with multi_value == false.
 */
template <typename T, bool asc> class NumericMapLookupSortBlobWriter final : public ISortBlobWriter {
    Stash                         _stash; // owns the read views
    StringEnumKeyFinder           _keys;
    const IArrayReadView<T>*      _values;
    NumericSortBlobWriter<T, asc> _writer;

public:
    NumericMapLookupSortBlobWriter(Stash&& stash, const StringEnumKeyFinder& keys, const IArrayReadView<T>* values,
                                   MissingPolicy policy, T missing_value)
        : _stash(std::move(stash)), _keys(keys), _values(values), _writer(policy, missing_value, false) {}
    ~NumericMapLookupSortBlobWriter() override;
    long write(uint32_t docid, void* buf, long available) override {
        _writer.reset();
        auto pos = _keys.position(docid);
        if (pos != npos) {
            auto values = _values->get_values(docid);
            // A selected undefined value counts as missing, matching single value attribute sorting.
            // isUndefined() also covers NaN for float and double.
            if (pos < values.size() && !isUndefined(values[pos])) {
                _writer.candidate(values[pos]);
            }
        }
        return _writer.write(buf, available);
    }
};

template <typename T, bool asc> NumericMapLookupSortBlobWriter<T, asc>::~NumericMapLookupSortBlobWriter() = default;

/*
 * Sort blob writer for a string value at the position of the selected key.
 * Only the selected enum handle is resolved to a string, the other values in
 * the document are never translated.
 */
template <bool asc> class StringMapLookupSortBlobWriter final : public ISortBlobWriter {
    Stash                     _stash; // owns the read views
    StringEnumKeyFinder       _keys;
    const IArrayEnumReadView* _values;
    const IAttributeVector&   _value_attr;
    StringSortBlobWriter<asc> _writer;

public:
    StringMapLookupSortBlobWriter(Stash&& stash, const StringEnumKeyFinder& keys, const IArrayEnumReadView* values,
                                  const IAttributeVector& value_attr, const common::BlobConverter* converter,
                                  MissingPolicy policy, std::string_view missing_value)
        : _stash(std::move(stash)),
          _keys(keys),
          _values(values),
          _value_attr(value_attr),
          _writer(converter, policy, missing_value, false) {}
    ~StringMapLookupSortBlobWriter() override;
    long write(uint32_t docid, void* buf, long available) override {
        _writer.reset(buf, available);
        auto pos = _keys.position(docid);
        if (pos != npos) {
            auto handles = _values->get_values(docid);
            if (pos < handles.size()) {
                const char* value = _value_attr.getStringFromEnum(handles[pos].load_acquire().ref());
                // isUndefined(const char*) is null or empty, matching single value string attribute sorting.
                if (!isUndefined(value) && !_writer.candidate(value)) {
                    return -1;
                }
            }
        }
        return _writer.write();
    }
};

template <bool asc> StringMapLookupSortBlobWriter<asc>::~StringMapLookupSortBlobWriter() = default;

void report_missing_read_view(const IAttributeVector& attr, const char* role) {
    Issue::report("make_map_lookup_sort_blob_writer: Could not create %s read view for attribute vector '%s' "
                  "(basic type %s)",
                  role, attr.getName().c_str(), BasicType(attr.getBasicType()).asString());
}

template <typename T>
std::unique_ptr<ISortBlobWriter>
make_numeric_writer(Stash& stash, const StringEnumKeyFinder& keys, const IAttributeVector& value_attr,
                    const IMultiValueAttribute& value_mva, const FieldSortSpec& field_sort_spec) {
    auto* values = value_mva.make_read_view(IMultiValueAttribute::ArrayTag<T>(), stash);
    if (values == nullptr) {
        report_missing_read_view(value_attr, "value array");
        return {};
    }
    T missing_value = string_to_number<T>(field_sort_spec._missing_value);
    if (field_sort_spec.is_ascending()) {
        return std::make_unique<NumericMapLookupSortBlobWriter<T, true>>(
            std::move(stash), keys, values, field_sort_spec._missing_policy, missing_value);
    }
    return std::make_unique<NumericMapLookupSortBlobWriter<T, false>>(std::move(stash), keys, values,
                                                                      field_sort_spec._missing_policy, missing_value);
}

std::unique_ptr<ISortBlobWriter>
make_string_writer(Stash& stash, const StringEnumKeyFinder& keys, const IAttributeVector& value_attr,
                   const IMultiValueAttribute& value_mva, const FieldSortSpec& field_sort_spec) {
    auto* values = value_mva.make_read_view(IMultiValueAttribute::ArrayEnumTag(), stash);
    if (values == nullptr) {
        report_missing_read_view(value_attr, "value array enum");
        return {};
    }
    if (field_sort_spec.is_ascending()) {
        return std::make_unique<StringMapLookupSortBlobWriter<true>>(
            std::move(stash), keys, values, value_attr, field_sort_spec._converter.get(),
            field_sort_spec._missing_policy, field_sort_spec._missing_value);
    }
    return std::make_unique<StringMapLookupSortBlobWriter<false>>(
        std::move(stash), keys, values, value_attr, field_sort_spec._converter.get(), field_sort_spec._missing_policy,
        field_sort_spec._missing_value);
}

bool is_supported_component(const IAttributeVector& attr, const char* role) {
    if (attr.isImported()) {
        Issue::report("make_map_lookup_sort_blob_writer: %s attribute vector '%s' is imported, which is not supported",
                      role, attr.getName().c_str());
        return false;
    }
    if (attr.getCollectionType() != CollectionType::ARRAY) {
        Issue::report("make_map_lookup_sort_blob_writer: %s attribute vector '%s' has collection type %s, "
                      "expected array",
                      role, attr.getName().c_str(), CollectionType(attr.getCollectionType()).asString());
        return false;
    }
    if (attr.as_multi_value_attribute() == nullptr) {
        Issue::report("make_map_lookup_sort_blob_writer: %s attribute vector '%s' is not a multi value attribute",
                      role, attr.getName().c_str());
        return false;
    }
    return true;
}

} // namespace

std::unique_ptr<ISortBlobWriter> make_map_lookup_sort_blob_writer(const IAttributeVector& key_attr,
                                                                  const IAttributeVector& value_attr,
                                                                  const FieldSortSpec&    field_sort_spec) {
    if (!field_sort_spec._map_key.has_value()) {
        Issue::report("make_map_lookup_sort_blob_writer: Sort spec for field '%s' has no map key",
                      field_sort_spec._field.c_str());
        return {};
    }
    if (!is_supported_component(key_attr, "Map key") || !is_supported_component(value_attr, "Map value")) {
        return {};
    }
    if (!key_attr.isStringType()) {
        Issue::report("make_map_lookup_sort_blob_writer: Map key attribute vector '%s' has basic type %s, "
                      "only string map keys are supported",
                      key_attr.getName().c_str(), BasicType(key_attr.getBasicType()).asString());
        return {};
    }
    if (!key_attr.hasEnum()) {
        Issue::report("make_map_lookup_sort_blob_writer: Map key attribute vector '%s' has no enum store",
                      key_attr.getName().c_str());
        return {};
    }
    Stash stash;
    auto* key_view = key_attr.as_multi_value_attribute()->make_read_view(IMultiValueAttribute::ArrayEnumTag(), stash);
    if (key_view == nullptr) {
        // Never folded into the key finder's "unknown key" flag: that would silently make every document missing.
        report_missing_read_view(key_attr, "key array enum");
        return {};
    }
    StringEnumKeyFinder keys(key_attr, key_view, *field_sort_spec._map_key);
    const auto&         value_mva = *value_attr.as_multi_value_attribute();
    try {
        switch (value_attr.getBasicType()) {
        case BasicType::INT8:
            return make_numeric_writer<int8_t>(stash, keys, value_attr, value_mva, field_sort_spec);
        case BasicType::INT16:
            return make_numeric_writer<int16_t>(stash, keys, value_attr, value_mva, field_sort_spec);
        case BasicType::INT32:
            return make_numeric_writer<int32_t>(stash, keys, value_attr, value_mva, field_sort_spec);
        case BasicType::INT64:
            return make_numeric_writer<int64_t>(stash, keys, value_attr, value_mva, field_sort_spec);
        case BasicType::FLOAT:
            return make_numeric_writer<float>(stash, keys, value_attr, value_mva, field_sort_spec);
        case BasicType::DOUBLE:
            return make_numeric_writer<double>(stash, keys, value_attr, value_mva, field_sort_spec);
        case BasicType::STRING:
            return make_string_writer(stash, keys, value_attr, value_mva, field_sort_spec);
        default:
            Issue::report("make_map_lookup_sort_blob_writer: Map value attribute vector '%s' has basic type %s, "
                          "which is not supported",
                          value_attr.getName().c_str(), BasicType(value_attr.getBasicType()).asString());
            return {};
        }
    } catch (const IllegalArgumentException& e) {
        Issue::report("make_map_lookup_sort_blob_writer: For attribute vector %s (basic type %s): %s",
                      value_attr.getName().c_str(), BasicType(value_attr.getBasicType()).asString(), e.message());
        return {};
    }
}

} // namespace search::attribute
