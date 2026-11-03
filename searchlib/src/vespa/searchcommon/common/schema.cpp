// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schema.h"

#include <vespa/config/common/configparser.h>
#include <vespa/fastos/file.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/vespalib/stllike/hashtable.hpp>

#include <algorithm>
#include <array>
#include <fstream>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".index.schema");

using namespace config;
using namespace search::index;

namespace {

template <typename T>
void writeFields(vespalib::asciistream& os, std::string_view prefix, const std::vector<T>& fields) {
    os << prefix << "[" << fields.size() << "]\n";
    for (size_t i = 0; i < fields.size(); ++i) {
        fields[i].write(os, vespalib::make_string("%s[%zu].", prefix.data(), i));
    }
}

void writeFieldSets(vespalib::asciistream& os, const std::string& name, const std::vector<Schema::FieldSet>& fss) {
    std::string prefix(name);
    prefix += "[";
    os << prefix << fss.size() << "]\n";
    for (size_t i = 0; i < fss.size(); ++i) {
        os << prefix << i << "].name " << fss[i].getName() << "\n";
        os << prefix << i << "].field[" << fss[i].getFields().size() << "]\n";
        vespalib::asciistream tmp;
        tmp << prefix << i << "].field[";
        for (size_t j = 0; j < fss[i].getFields().size(); ++j) {
            os << tmp.view() << j << "].name " << fss[i].getFields()[j] << "\n";
        }
    }
}

// The index fields of a uri field 'x' are 'x' and 'x.<suffix>' for each of these suffixes.
constexpr std::array<std::string_view, 7> uri_suffixes = {"scheme", "host",     "port",    "path",
                                                          "query",  "fragment", "hostname"};

void writeUriFields(vespalib::asciistream& os, const std::vector<std::string>& uriFields) {
    os << "urifield[" << uriFields.size() << "]\n";
    for (size_t i = 0; i < uriFields.size(); ++i) {
        os << "urifield[" << i << "].name " << uriFields[i] << "\n";
    }
    os << "explicituris true\n";
}

struct FieldName {
    std::string name;
    explicit FieldName(const config::StringVector& lines) : name(ConfigParser::parse<std::string>("name", lines)) {}
};

template <typename T> uint32_t getFieldId(std::string_view name, const T& map) noexcept {
    auto it = map.find(name);
    return (it != map.end()) ? it->second : Schema::UNKNOWN_FIELD_ID;
}

} // namespace

namespace search::index {

const uint32_t Schema::UNKNOWN_FIELD_ID(std::numeric_limits<uint32_t>::max());

Schema::Field::Field(std::string_view n, DataType dt) noexcept : Field(n, dt, schema::CollectionType::SINGLE, "") {
}

Schema::Field::Field(std::string_view n, DataType dt, CollectionType ct) noexcept : Field(n, dt, ct, "") {
}

Schema::Field::Field(std::string_view n, DataType dt, CollectionType ct, std::string_view tensor_spec) noexcept
    : _name(n), _dataType(dt), _collectionType(ct), _tensor_spec(tensor_spec) {
}

// XXX: Resource leak if exception is thrown.
Schema::Field::Field(const config::StringVector& lines)
    : _name(ConfigParser::parse<std::string>("name", lines)),
      _dataType(schema::dataTypeFromName(ConfigParser::parse<std::string>("datatype", lines))),
      _collectionType(schema::collectionTypeFromName(ConfigParser::parse<std::string>("collectiontype", lines))) {
}

Schema::Field::Field(const Field&) noexcept = default;
Schema::Field& Schema::Field::operator=(const Field&) noexcept = default;
Schema::Field::Field(Field&&) noexcept = default;
Schema::Field& Schema::Field::operator=(Field&&) noexcept = default;

Schema::Field::~Field() = default;

void Schema::Field::write(vespalib::asciistream& os, std::string_view prefix) const {
    os << prefix << "name " << _name << "\n";
    os << prefix << "datatype " << getTypeName(_dataType) << "\n";
    os << prefix << "collectiontype " << getTypeName(_collectionType) << "\n";
}

bool Schema::Field::operator==(const Field& rhs) const noexcept {
    return _name == rhs._name && _dataType == rhs._dataType && _collectionType == rhs._collectionType &&
           _tensor_spec == rhs._tensor_spec;
}

bool Schema::Field::operator!=(const Field& rhs) const noexcept {
    return !((*this) == rhs);
}

Schema::IndexField::IndexField(std::string_view name, DataType dt) noexcept
    : Field(name, dt), _avgElemLen(512), _interleaved_features(false) {
}

Schema::IndexField::IndexField(std::string_view name, DataType dt, CollectionType ct) noexcept
    : Field(name, dt, ct), _avgElemLen(512), _interleaved_features(false) {
}

Schema::IndexField::IndexField(const config::StringVector& lines)
    : Field(lines),
      _avgElemLen(ConfigParser::parse<int32_t>("averageelementlen", lines, 512)),
      _interleaved_features(ConfigParser::parse<bool>("interleavedfeatures", lines, false)) {
}

Schema::IndexField::IndexField(const IndexField&) noexcept = default;
Schema::IndexField& Schema::IndexField::operator=(const IndexField&) noexcept = default;
Schema::IndexField::IndexField(IndexField&&) noexcept = default;
Schema::IndexField& Schema::IndexField::operator=(IndexField&&) noexcept = default;

void Schema::IndexField::write(vespalib::asciistream& os, std::string_view prefix) const {
    Field::write(os, prefix);
    os << prefix << "averageelementlen " << static_cast<int32_t>(_avgElemLen) << "\n";
    os << prefix << "interleavedfeatures " << (_interleaved_features ? "true" : "false") << "\n";

    // TODO: Remove prefix, phrases and positions when breaking downgrade is no longer an issue.
    os << prefix << "prefix false" << "\n";
    os << prefix << "phrases false" << "\n";
    os << prefix << "positions true" << "\n";
}

bool Schema::IndexField::operator==(const IndexField& rhs) const noexcept {
    return Field::operator==(rhs) && _avgElemLen == rhs._avgElemLen &&
           _interleaved_features == rhs._interleaved_features;
}

bool Schema::IndexField::operator!=(const IndexField& rhs) const noexcept {
    return Field::operator!=(rhs) || _avgElemLen != rhs._avgElemLen ||
           _interleaved_features != rhs._interleaved_features;
}

Schema::FieldSet::FieldSet(const config::StringVector& lines)
    : _name(ConfigParser::parse<std::string>("name", lines)), _fields() {
    auto fn = ConfigParser::parseArray<std::vector<FieldName>>("field", lines);
    for (const auto& fname : fn) {
        _fields.push_back(fname.name);
    }
}

Schema::FieldSet::FieldSet(const FieldSet&) = default;
Schema::FieldSet& Schema::FieldSet::operator=(const FieldSet&) = default;

Schema::FieldSet::~FieldSet() = default;

bool Schema::FieldSet::operator==(const FieldSet& rhs) const noexcept {
    return _name == rhs._name && _fields == rhs._fields;
}

bool Schema::FieldSet::operator!=(const FieldSet& rhs) const noexcept {
    return _name != rhs._name || _fields != rhs._fields;
}

void Schema::writeToStream(vespalib::asciistream& os, bool saveToDisk) const {
    writeFields(os, "attributefield", _attributeFields);
    writeFieldSets(os, "fieldset", _fieldSets);
    writeFields(os, "indexfield", _indexFields);
    if (_explicitUriFields) {
        writeUriFields(os, _uriFields);
    }
    if (!saveToDisk) {
        writeFields(os, "importedattributefields", _importedAttributeFields);
    }
}

Schema::Schema() = default;

Schema::Schema(const Schema& rhs) = default;
Schema& Schema::operator=(const Schema& rhs) = default;
Schema::Schema(Schema&& rhs) noexcept = default;
Schema& Schema::operator=(Schema&& rhs) noexcept = default;
Schema::~Schema() = default;

bool Schema::loadFromFile(const std::string& fileName) {
    std::ifstream file(fileName.c_str());
    if (!file) {
        LOG(error, "Could not open input file '%s' as part of loadFromFile()", fileName.c_str());
        return false;
    }
    config::StringVector lines;
    std::string          tmpLine;
    while (file) {
        getline(file, tmpLine);
        lines.push_back(tmpLine);
    }
    _indexFields = ConfigParser::parseArray<std::vector<IndexField>>("indexfield", lines);
    _attributeFields = ConfigParser::parseArray<std::vector<AttributeField>>("attributefield", lines);
    _fieldSets = ConfigParser::parseArray<std::vector<FieldSet>>("fieldset", lines);
    _uriFields.clear();
    for (auto& uriField : ConfigParser::parseArray<std::vector<FieldName>>("urifield", lines)) {
        _uriFields.push_back(std::move(uriField.name));
    }
    _explicitUriFields = ConfigParser::parse<bool>("explicituris", lines, false);
    _importedAttributeFields.clear(); // NOTE: these are not persisted to disk
    _indexIds.clear();
    for (size_t i(0), m(_indexFields.size()); i < m; i++) {
        _indexIds[_indexFields[i].getName()] = i;
    }
    _attributeIds.clear();
    for (size_t i(0), m(_attributeFields.size()); i < m; i++) {
        _attributeIds[_attributeFields[i].getName()] = i;
    }
    _fieldSetIds.clear();
    for (size_t i(0), m(_fieldSets.size()); i < m; i++) {
        _fieldSetIds[_fieldSets[i].getName()] = i;
    }
    _importedAttributeIds.clear();
    return true;
}

bool Schema::saveToFile(const std::string& fileName) const {
    vespalib::asciistream os;
    writeToStream(os, true);
    std::ofstream file(fileName.c_str());
    if (!file) {
        LOG(error, "Could not open output file '%s' as part of saveToFile()", fileName.c_str());
        return false;
    }
    file << os.view();
    file.close();
    if (file.fail()) {
        LOG(error, "Could not write to output file '%s' as part of saveToFile()", fileName.c_str());
        return false;
    }
    FastOS_File s;
    s.OpenReadWrite(fileName.c_str());
    if (!s.IsOpened()) {
        LOG(error, "Could not open schema file '%s' for fsync", fileName.c_str());
        return false;
    } else {
        if (!s.Sync()) {
            LOG(error, "Could not fsync schema file '%s'", fileName.c_str());
            return false;
        }
    }
    return true;
}

std::string Schema::toString() const {
    vespalib::asciistream os;
    writeToStream(os, false);
    return os.str();
}

namespace {
Schema::IndexField cloneIndexField(const Schema::IndexField& field, const std::string& suffix) {
    return Schema::IndexField(field.getName() + suffix, field.getDataType(), field.getCollectionType())
        .setAvgElemLen(field.getAvgElemLen());
}

template <typename T, typename M>
Schema& addField(const T& field, Schema& self, std::vector<T>& fields, M& name2id_map) {
    name2id_map[field.getName()] = fields.size();
    fields.push_back(field);
    return self;
}
} // namespace

Schema& Schema::addIndexField(const IndexField& field) {
    return addField(field, *this, _indexFields, _indexIds);
}

Schema& Schema::addUriIndexFields(const IndexField& field) {
    addIndexField(field);
    for (auto suffix : uri_suffixes) {
        addIndexField(cloneIndexField(field, "." + std::string(suffix)));
    }
    return add_uri_field(field.getName());
}

Schema& Schema::addAttributeField(const AttributeField& field) {
    return addField(field, *this, _attributeFields, _attributeIds);
}

Schema& Schema::addImportedAttributeField(const ImportedAttributeField& field) {
    return addField(field, *this, _importedAttributeFields, _importedAttributeIds);
}

Schema& Schema::addFieldSet(const FieldSet& fieldSet) {
    return addField(fieldSet, *this, _fieldSets, _fieldSetIds);
}

Schema& Schema::add_uri_field(std::string_view name) {
    if (std::find(_uriFields.begin(), _uriFields.end(), name) == _uriFields.end()) {
        _uriFields.emplace_back(name);
    }
    _explicitUriFields = true;
    return *this;
}

std::vector<std::string> Schema::get_uri_fields() const {
    if (_explicitUriFields) {
        return _uriFields;
    }
    // Legacy detection: there is a uri field 'x' if there is an index field named 'x.scheme'.
    std::vector<std::string> result;
    for (const auto& field : _indexFields) {
        std::string_view name = field.getName();
        size_t           dotPos = name.find('.');
        if ((dotPos != std::string_view::npos) && (name.substr(dotPos + 1) == "scheme")) {
            result.emplace_back(name.substr(0, dotPos));
        }
    }
    return result;
}

bool Schema::is_uri_part(std::string_view name) const {
    size_t dotPos = name.find('.');
    if (dotPos != std::string_view::npos) {
        std::string_view suffix = name.substr(dotPos + 1);
        if (std::find(uri_suffixes.begin(), uri_suffixes.end(), suffix) == uri_suffixes.end()) {
            return false;
        }
    }
    std::string_view base = name.substr(0, dotPos);
    if (_explicitUriFields) {
        return std::find(_uriFields.begin(), _uriFields.end(), base) != _uriFields.end();
    }
    auto uriFields = get_uri_fields();
    return std::find(uriFields.begin(), uriFields.end(), base) != uriFields.end();
}

uint32_t Schema::getIndexFieldId(std::string_view name) const noexcept {
    return getFieldId(name, _indexIds);
}

uint32_t Schema::getAttributeFieldId(std::string_view name) const noexcept {
    return getFieldId(name, _attributeIds);
}

uint32_t Schema::getFieldSetId(std::string_view name) const noexcept {
    return getFieldId(name, _fieldSetIds);
}

bool Schema::isIndexField(std::string_view name) const noexcept {
    return _indexIds.find(name) != _indexIds.end();
}

bool Schema::isAttributeField(std::string_view name) const noexcept {
    return _attributeIds.find(name) != _attributeIds.end();
}

void Schema::swap(Schema& rhs) {
    _indexFields.swap(rhs._indexFields);
    _attributeFields.swap(rhs._attributeFields);
    _fieldSets.swap(rhs._fieldSets);
    _importedAttributeFields.swap(rhs._importedAttributeFields);
    _uriFields.swap(rhs._uriFields);
    std::swap(_explicitUriFields, rhs._explicitUriFields);
    _indexIds.swap(rhs._indexIds);
    _attributeIds.swap(rhs._attributeIds);
    _fieldSetIds.swap(rhs._fieldSetIds);
    _importedAttributeIds.swap(rhs._importedAttributeIds);
}

void Schema::clear() {
    _indexFields.clear();
    _attributeFields.clear();
    _fieldSets.clear();
    _importedAttributeFields.clear();
    _uriFields.clear();
    _explicitUriFields = false;
    _indexIds.clear();
    _attributeIds.clear();
    _fieldSetIds.clear();
    _importedAttributeIds.clear();
}

namespace {
// Helper class allowing the is_matching specialization to access the schema.
struct IntersectHelper {
    Schema::UP schema;
    IntersectHelper() : schema(new Schema) {}

    template <typename T> bool is_matching(const T& t1, const T& t2) { return t1.matchingTypes(t2); }

    template <typename T, typename Map>
    void intersect(const std::vector<T>& set1, const std::vector<T>& set2, const Map& set2_map,
                   std::vector<T>& intersection, Map& intersection_map) {
        for (const auto& elem : set1) {
            auto it2 = set2_map.find(elem.getName());
            if (it2 != set2_map.end()) {
                if (is_matching(elem, set2[it2->second])) {
                    intersection_map[elem.getName()] = intersection.size();
                    intersection.push_back(elem);
                }
            }
        }
    }
};

template <> bool IntersectHelper::is_matching(const Schema::FieldSet& f1, const Schema::FieldSet& f2) {
    if (f1.getFields() != f2.getFields()) {
        return false;
    }
    for (const std::string& field : f1.getFields()) {
        if (schema->getIndexFieldId(field) == Schema::UNKNOWN_FIELD_ID) {
            return false;
        }
    }
    return true;
}

template <typename T, typename Map>
void addEntries(const std::vector<T>& entries, std::vector<T>& v, Map& name2id_map) {
    for (const T& key : entries) {
        if (name2id_map.find(key.getName()) == name2id_map.end()) {
            name2id_map[key.getName()] = v.size();
            v.push_back(key);
        }
    }
}

/*
 * Set the uri fields of the result of a set operation to the candidates that still have their main index field or
 * their scheme index field, the latter to keep a uri field without main index field recognized as broken (see
 * SchemaIndexFields). When neither input has explicit uri fields, the result does not either, and its uri fields are
 * detected as for the inputs.
 */
void set_uri_fields(Schema& result, const Schema& lhs, const Schema& rhs,
                    const std::vector<std::string>& candidates) {
    result.set_explicit_uri_fields(false);
    if (lhs.has_explicit_uri_fields() || rhs.has_explicit_uri_fields()) {
        result.set_explicit_uri_fields(true);
        for (const auto& name : candidates) {
            if (result.isIndexField(name) || result.isIndexField(name + ".scheme")) {
                result.add_uri_field(name);
            }
        }
    }
}

bool contains(const std::vector<std::string>& names, const std::string& name) {
    return std::find(names.begin(), names.end(), name) != names.end();
}

std::vector<std::string> sorted_uri_fields(const Schema& schema) {
    auto result = schema.get_uri_fields();
    std::sort(result.begin(), result.end());
    return result;
}

template <typename T, typename Map>
void difference(const std::vector<T>& minuend, const Map& subtrahend_map, std::vector<T>& diff, Map& diff_map) {
    for (const T& key : minuend) {
        if (subtrahend_map.find(key.getName()) == subtrahend_map.end()) {
            diff_map[key.getName()] = diff.size();
            diff.push_back(key);
        }
    }
}
} // namespace

Schema::UP Schema::intersect(const Schema& lhs, const Schema& rhs) {
    IntersectHelper h;
    h.intersect(lhs._indexFields, rhs._indexFields, rhs._indexIds, h.schema->_indexFields, h.schema->_indexIds);
    h.intersect(lhs._attributeFields, rhs._attributeFields, rhs._attributeIds, h.schema->_attributeFields,
                h.schema->_attributeIds);
    h.intersect(lhs._fieldSets, rhs._fieldSets, rhs._fieldSetIds, h.schema->_fieldSets, h.schema->_fieldSetIds);
    std::vector<std::string> uriFields;
    auto                     rhsUriFields = rhs.get_uri_fields();
    for (auto& name : lhs.get_uri_fields()) {
        if (contains(rhsUriFields, name)) {
            uriFields.push_back(std::move(name));
        }
    }
    set_uri_fields(*h.schema, lhs, rhs, uriFields);
    return std::move(h.schema);
}

Schema::UP Schema::make_union(const Schema& lhs, const Schema& rhs) {
    Schema::UP schema(new Schema(lhs));
    addEntries(rhs._indexFields, schema->_indexFields, schema->_indexIds);
    addEntries(rhs._attributeFields, schema->_attributeFields, schema->_attributeIds);
    addEntries(rhs._fieldSets, schema->_fieldSets, schema->_fieldSetIds);
    schema->_uriFields.clear();
    auto uriFields = lhs.get_uri_fields();
    for (auto& name : rhs.get_uri_fields()) {
        if (!contains(uriFields, name)) {
            uriFields.push_back(std::move(name));
        }
    }
    set_uri_fields(*schema, lhs, rhs, uriFields);
    return schema;
}

Schema::UP Schema::set_difference(const Schema& lhs, const Schema& rhs) {
    Schema::UP schema(new Schema);
    difference(lhs._indexFields, rhs._indexIds, schema->_indexFields, schema->_indexIds);
    difference(lhs._attributeFields, rhs._attributeIds, schema->_attributeFields, schema->_attributeIds);
    difference(lhs._fieldSets, rhs._fieldSetIds, schema->_fieldSets, schema->_fieldSetIds);
    set_uri_fields(*schema, lhs, rhs, lhs.get_uri_fields());
    return schema;
}

bool Schema::operator==(const Schema& rhs) const noexcept {
    // Uri fields are compared by content, as explicit uri fields are equal to the same detected uri fields.
    return _indexFields == rhs._indexFields && _attributeFields == rhs._attributeFields &&
           _fieldSets == rhs._fieldSets && _importedAttributeFields == rhs._importedAttributeFields &&
           sorted_uri_fields(*this) == sorted_uri_fields(rhs);
}

bool Schema::operator!=(const Schema& rhs) const noexcept {
    return !(*this == rhs);
}

bool Schema::empty() const noexcept {
    return _indexFields.empty() && _attributeFields.empty() && _fieldSets.empty() &&
           _importedAttributeFields.empty() && _uriFields.empty();
}

} // namespace search::index
