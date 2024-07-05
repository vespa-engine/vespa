// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schema.h"
#include <fstream>
#include <vespa/config/common/configparser.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/fastos/file.h>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".index.schema");

using namespace config;
using namespace search::index;

namespace {

template <typename T>
void
writeFields(vespalib::asciistream & os,
            std::string_view prefix,
            const std::vector<T> & fields)
{
    os << prefix << "[" << fields.size() << "]\n";
    for (size_t i = 0; i < fields.size(); ++i) {
        fields[i].write(os, vespalib::make_string("%s[%zu].", prefix.data(), i));
    }
}

void
writeFieldSets(vespalib::asciistream &os,
               const vespalib::string &name,
               const std::vector<Schema::FieldSet> &fss)
{
    vespalib::string prefix(name);
    prefix += "[";
    os << prefix << fss.size() << "]\n";
    for (size_t i = 0; i < fss.size(); ++i) {
        os << prefix << i << "].name " << fss[i].getName() << "\n";
        os << prefix << i << "].field[" << fss[i].getFields().size() << "]\n";
        vespalib::asciistream tmp;
        tmp << prefix << i << "].field[";
        for (size_t j = 0; j < fss[i].getFields().size(); ++j) {
            os << tmp.str() << j << "].name " << fss[i].getFields()[j] << "\n";
        }
    }
}

struct FieldName {
    vespalib::string name;
    explicit FieldName(const config::StringVector & lines)
        : name(ConfigParser::parse<vespalib::string>("name", lines))
    {
    }
};

template <typename T>
uint32_t
getFieldId(std::string_view name, const T &map) noexcept
{
    auto it = map.find(name);
    return (it != map.end()) ? it->second : Schema::UNKNOWN_FIELD_ID;
}

}  // namespace

namespace search::index {

const uint32_t Schema::UNKNOWN_FIELD_ID(std::numeric_limits<uint32_t>::max());

Schema::Field::Field(std::string_view n, DataType dt) noexcept
    : Field(n, dt, schema::CollectionType::SINGLE, "")
{
}

Schema::Field::Field(std::string_view n, DataType dt, CollectionType ct) noexcept
    : Field(n, dt, ct, "")
{
}

Schema::Field::Field(std::string_view n, DataType dt, CollectionType ct, std::string_view tensor_spec) noexcept
    : _name(n),
      _dataType(dt),
      _collectionType(ct),
      _tensor_spec(tensor_spec)
{
}

// XXX: Resource leak if exception is thrown.
Schema::Field::Field(const config::StringVector & lines)
    : _name(ConfigParser::parse<vespalib::string>("name", lines)),
      _dataType(schema::dataTypeFromName(ConfigParser::parse<vespalib::string>("datatype", lines))),
      _collectionType(schema::collectionTypeFromName(ConfigParser::parse<vespalib::string>("collectiontype", lines)))
{
}

Schema::Field::Field(const Field &) noexcept = default;
Schema::Field & Schema::Field::operator = (const Field &) noexcept = default;
Schema::Field::Field(Field &&) noexcept = default;
Schema::Field & Schema::Field::operator = (Field &&) noexcept = default;

Schema::Field::~Field() = default;

void
Schema::Field::write(vespalib::asciistream & os, std::string_view prefix) const
{
    os << prefix << "name " << _name << "\n";
    os << prefix << "datatype " << getTypeName(_dataType) << "\n";
    os << prefix << "collectiontype " << getTypeName(_collectionType) << "\n";
}

bool
Schema::Field::operator==(const Field &rhs) const noexcept
{
    return _name == rhs._name &&
           _dataType == rhs._dataType &&
           _collectionType == rhs._collectionType &&
           _tensor_spec == rhs._tensor_spec;
}

bool
Schema::Field::operator!=(const Field &rhs) const noexcept
{
    return !((*this) == rhs);
}

Schema::IndexField::IndexField(std::string_view name, DataType dt) noexcept
    : Field(name, dt),
      _avgElemLen(512),
      _interleaved_features(false)
{
}

Schema::IndexField::IndexField(std::string_view name, DataType dt,
                               CollectionType ct) noexcept
    : Field(name, dt, ct),
      _avgElemLen(512),
      _interleaved_features(false)
{
}

Schema::IndexField::IndexField(const config::StringVector &lines)
    : Field(lines),
      _avgElemLen(ConfigParser::parse<int32_t>("averageelementlen", lines, 512)),
      _interleaved_features(ConfigParser::parse<bool>("interleavedfeatures", lines, false))
{
}

Schema::IndexField::IndexField(const IndexField &) noexcept = default;
Schema::IndexField & Schema::IndexField::operator = (const IndexField &) noexcept = default;
Schema::IndexField::IndexField(IndexField &&) noexcept = default;
Schema::IndexField & Schema::IndexField::operator = (IndexField &&) noexcept = default;

void
Schema::IndexField::write(vespalib::asciistream & os, std::string_view prefix) const
{
    Field::write(os, prefix);
    os << prefix << "averageelementlen " << static_cast<int32_t>(_avgElemLen) << "\n";
    os << prefix << "interleavedfeatures " << (_interleaved_features ? "true" : "false") << "\n";

    // TODO: Remove prefix, phrases and positions when breaking downgrade is no longer an issue.
    os << prefix << "prefix false" << "\n";
    os << prefix << "phrases false" << "\n";
    os << prefix << "positions true" << "\n";
}

bool
Schema::IndexField::operator==(const IndexField &rhs) const noexcept
{
    return Field::operator==(rhs) &&
            _avgElemLen == rhs._avgElemLen &&
            _interleaved_features == rhs._interleaved_features;
}

bool
Schema::IndexField::operator!=(const IndexField &rhs) const noexcept
{
    return Field::operator!=(rhs) ||
            _avgElemLen != rhs._avgElemLen ||
            _interleaved_features != rhs._interleaved_features;
}

Schema::FieldSet::FieldSet(const config::StringVector & lines) :
    _name(ConfigParser::parse<vespalib::string>("name", lines)),
    _fields()
{
    auto fn = ConfigParser::parseArray<std::vector<FieldName>>("field", lines);
    for (const auto & fname : fn) {
        _fields.push_back(fname.name);
    }
}

Schema::FieldSet::FieldSet(const FieldSet &) = default;
Schema::FieldSet & Schema::FieldSet::operator = (const FieldSet &) = default;

Schema::FieldSet::~FieldSet() = default;

bool
Schema::FieldSet::operator==(const FieldSet &rhs) const noexcept
{
    return _name == rhs._name &&
         _fields == rhs._fields;
}

bool
Schema::FieldSet::operator!=(const FieldSet &rhs) const noexcept
{
    return _name != rhs._name ||
         _fields != rhs._fields;
}

void
Schema::writeToStream(vespalib::asciistream &os, bool saveToDisk) const
{
    writeFields(os, "attributefield", _attributeFields);
    writeFieldSets(os, "fieldset", _fieldSets);
    writeFields(os, "indexfield", _indexFields);
    if (!saveToDisk) {
        writeFields(os, "importedattributefields", _importedAttributeFields);
    }
}

Schema::Schema() = default;

Schema::Schema(const Schema & rhs) = default;
Schema & Schema::operator=(const Schema & rhs) = default;
Schema::Schema(Schema && rhs) noexcept = default;
Schema & Schema::operator=(Schema && rhs) noexcept = default;
Schema::~Schema() = default;

bool
Schema::loadFromFile(const vespalib::string & fileName)
{
    std::ifstream file(fileName.c_str());
    if (!file) {
        LOG(warning, "Could not open input file '%s' as part of loadFromFile()", fileName.c_str());
        return false;
    }
    config::StringVector lines;
    std::string tmpLine;
    while (file) {
        getline(file, tmpLine);
        lines.push_back(tmpLine);
    }
    _indexFields = ConfigParser::parseArray<std::vector<IndexField>>("indexfield", lines);
    _attributeFields = ConfigParser::parseArray<std::vector<AttributeField>>("attributefield", lines);
    _fieldSets = ConfigParser::parseArray<std::vector<FieldSet>>("fieldset", lines);
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

bool
Schema::saveToFile(const vespalib::string & fileName) const
{
    vespalib::asciistream os;
    writeToStream(os, true);
    std::ofstream file(fileName.c_str());
    if (!file) {
        LOG(warning, "Could not open output file '%s' as part of saveToFile()", fileName.c_str());
        return false;
    }
    file << os.str();
    file.close();
    if (file.fail()) {
        LOG(warning,
            "Could not write to output file '%s' as part of saveToFile()",
            fileName.c_str());
        return false;
    }
    FastOS_File s;
    s.OpenReadWrite(fileName.c_str());
    if (!s.IsOpened()) {
        LOG(warning, "Could not open schema file '%s' for fsync", fileName.c_str());
        return false;
    } else {
        if (!s.Sync()) {
            LOG(warning, "Could not fsync schema file '%s'", fileName.c_str());
            return false;
        }
    }
    return true;
}

vespalib::string
Schema::toString() const
{
    vespalib::asciistream os;
    writeToStream(os, false);
    return os.str();
}

namespace {
Schema::IndexField
cloneIndexField(const Schema::IndexField &field,
                const vespalib::string &suffix)
{
    return Schema::IndexField(field.getName() + suffix,
                              field.getDataType(),
                              field.getCollectionType()).
        setAvgElemLen(field.getAvgElemLen());
}

template <typename T, typename M>
Schema &
addField(const T &field, Schema &self,
         std::vector<T> &fields, M &name2id_map)
{
    name2id_map[field.getName()] = fields.size();
    fields.push_back(field);
    return self;
}
}  // namespace

Schema &
Schema::addIndexField(const IndexField &field)
{
    return addField(field, *this, _indexFields, _indexIds);
}

Schema &
Schema::addUriIndexFields(const IndexField &field)
{
    addIndexField(field);
    addIndexField(cloneIndexField(field, ".scheme"));
    addIndexField(cloneIndexField(field, ".host"));
    addIndexField(cloneIndexField(field, ".port"));
    addIndexField(cloneIndexField(field, ".path"));
    addIndexField(cloneIndexField(field, ".query"));
    addIndexField(cloneIndexField(field, ".fragment"));
    addIndexField(cloneIndexField(field, ".hostname"));
    return *this;
}

Schema &
Schema::addAttributeField(const AttributeField &field)
{
    return addField(field, *this, _attributeFields, _attributeIds);
}

Schema &
Schema::addImportedAttributeField(const ImportedAttributeField &field)
{
    return addField(field, *this, _importedAttributeFields, _importedAttributeIds);
}

Schema &
Schema::addFieldSet(const FieldSet &fieldSet)
{
    return addField(fieldSet, *this, _fieldSets, _fieldSetIds);
}

uint32_t
Schema::getIndexFieldId(std::string_view name) const noexcept
{
    return getFieldId(name, _indexIds);
}

uint32_t
Schema::getAttributeFieldId(std::string_view name) const noexcept
{
    return getFieldId(name, _attributeIds);
}

uint32_t
Schema::getFieldSetId(std::string_view name) const noexcept
{
    return getFieldId(name, _fieldSetIds);
}

bool
Schema::isIndexField(std::string_view name) const noexcept
{
    return _indexIds.find(name) != _indexIds.end();
}

bool
Schema::isAttributeField(std::string_view name) const noexcept
{
    return _attributeIds.find(name) != _attributeIds.end();
}


void
Schema::swap(Schema &rhs)
{
    _indexFields.swap(rhs._indexFields);
    _attributeFields.swap(rhs._attributeFields);
    _fieldSets.swap(rhs._fieldSets);
    _importedAttributeFields.swap(rhs._importedAttributeFields);
    _indexIds.swap(rhs._indexIds);
    _attributeIds.swap(rhs._attributeIds);
    _fieldSetIds.swap(rhs._fieldSetIds);
    _importedAttributeIds.swap(rhs._importedAttributeIds);
}

void
Schema::clear()
{
    _indexFields.clear();
    _attributeFields.clear();
    _fieldSets.clear();
    _importedAttributeFields.clear();
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

    template <typename T>
    bool is_matching(const T &t1, const T &t2) { return t1.matchingTypes(t2); }

    template <typename T, typename Map>
    void intersect(const std::vector<T> &set1, const std::vector<T> &set2,
                   const Map &set2_map,
                   std::vector<T> &intersection, Map &intersection_map) {
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

template <>
bool IntersectHelper::is_matching(const Schema::FieldSet &f1, const Schema::FieldSet &f2) {
    if (f1.getFields() != f2.getFields())
        return false;
    for (const vespalib::string & field : f1.getFields()) {
        if (schema->getIndexFieldId(field) == Schema::UNKNOWN_FIELD_ID) {
            return false;
        }
    }
    return true;
}

template <typename T, typename Map>
void addEntries(const std::vector<T> &entries, std::vector<T> &v, Map &name2id_map) {
    for (const T & key : entries) {
        if (name2id_map.find(key.getName()) == name2id_map.end()) {
            name2id_map[key.getName()] = v.size();
            v.push_back(key);
        }
    }
}

template <typename T, typename Map>
void difference(const std::vector<T> &minuend, const Map &subtrahend_map,
                std::vector<T> &diff, Map &diff_map) {
    for (const T & key : minuend){
        if (subtrahend_map.find(key.getName()) == subtrahend_map.end()) {
            diff_map[key.getName()] = diff.size();
            diff.push_back(key);
        }
    }
}
}  // namespace

Schema::UP
Schema::intersect(const Schema &lhs, const Schema &rhs)
{
    IntersectHelper h;
    h.intersect(lhs._indexFields, rhs._indexFields, rhs._indexIds,
                h.schema->_indexFields, h.schema->_indexIds);
    h.intersect(lhs._attributeFields, rhs._attributeFields, rhs._attributeIds,
                h.schema->_attributeFields, h.schema->_attributeIds);
    h.intersect(lhs._fieldSets, rhs._fieldSets, rhs._fieldSetIds,
                h.schema->_fieldSets, h.schema->_fieldSetIds);
    return std::move(h.schema);
}

Schema::UP
Schema::make_union(const Schema &lhs, const Schema &rhs)
{
    Schema::UP schema(new Schema(lhs));
    addEntries(rhs._indexFields, schema->_indexFields, schema->_indexIds);
    addEntries(rhs._attributeFields, schema->_attributeFields, schema->_attributeIds);
    addEntries(rhs._fieldSets, schema->_fieldSets, schema->_fieldSetIds);
    return schema;
}

Schema::UP
Schema::set_difference(const Schema &lhs, const Schema &rhs)
{
    Schema::UP schema(new Schema);
    difference(lhs._indexFields, rhs._indexIds,
               schema->_indexFields, schema->_indexIds);
    difference(lhs._attributeFields, rhs._attributeIds,
               schema->_attributeFields, schema->_attributeIds);
    difference(lhs._fieldSets, rhs._fieldSetIds,
               schema->_fieldSets, schema->_fieldSetIds);
    return schema;
}

bool
Schema::operator==(const Schema &rhs) const noexcept
{
    return _indexFields == rhs._indexFields &&
            _attributeFields == rhs._attributeFields &&
            _fieldSets == rhs._fieldSets &&
            _importedAttributeFields == rhs._importedAttributeFields;
}

bool
Schema::operator!=(const Schema &rhs) const noexcept
{
    return _indexFields != rhs._indexFields ||
            _attributeFields != rhs._attributeFields ||
            _fieldSets != rhs._fieldSets ||
            _importedAttributeFields != rhs._importedAttributeFields;
}

bool
Schema::empty() const noexcept
{
    return _indexFields.empty() &&
            _attributeFields.empty() &&
            _fieldSets.empty() &&
            _importedAttributeFields.empty();
}

}
