// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structfieldvalue.h"
#include "fieldvaluewriter.h"
#include "document.h"
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/base/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".document.structfieldvalue");

using std::vector;
using vespalib::nbostream;
using vespalib::nbostream_longlivedbuf;

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(StructFieldValue, StructuredFieldValue);

StructFieldValue::StructFieldValue(const DataType &type)
    : StructuredFieldValue(type),
      _repo(NULL),
      _doc_type(NULL),
      _version(Document::getNewestSerializationVersion()),
      _hasChanged(true)
{
    if (!type.getClass().inherits(StructDataType::classId)) {
        throw vespalib::IllegalArgumentException(
                "Cannot generate a struct value with non-struct type "
                + type.toString() + ".", VESPA_STRLOC);
    }
}

StructFieldValue::~StructFieldValue() { }

StructFieldValue::Chunks::~Chunks() { }

void
StructFieldValue::swap(StructFieldValue & rhs)
{
    StructuredFieldValue::swap(rhs);
    std::swap(_chunks, rhs._chunks);
    std::swap(_hasChanged, rhs._hasChanged);
    std::swap(_repo, rhs._repo);
    std::swap(_doc_type, rhs._doc_type);
    std::swap(_version, _version);
}

void
StructFieldValue::lazyDeserialize(const FixedTypeRepo &repo,
                                  uint16_t version,
                                  SerializableArray::EntryMap && fm,
                                  ByteBuffer::UP buffer,
                                  CompressionConfig::Type comp_type,
                                  int32_t uncompressed_length)
{
    _repo = &repo.getDocumentTypeRepo();
    _doc_type = &repo.getDocumentType();
    _version = version;

    _chunks.push_back(SerializableArray::UP(new SerializableArray()));
    _chunks.back().assign(fm, std::move(buffer), comp_type, uncompressed_length);
    _hasChanged = false;
}

bool StructFieldValue::serializeField(int field_id, uint16_t version,
                                      FieldValueWriter &writer) const {
    if (version == _version) {
        for (int i = _chunks.size() - 1; i >= 0; --i) {
            vespalib::ConstBufferRef buf = _chunks[i].get(field_id);
            if ( buf.size() != 0) {
                writer.writeSerializedData(buf.data(), buf.size());
                break;
            }
        }

        return true;
    }
    try {
        const Field &f = getStructType().getField(field_id, _version);
        writer.writeFieldValue(*getFieldValue(f));
        return true;
    } catch (FieldNotFoundException &) {
        LOG(info, "Dropping field %d when serializing to a newer version", field_id);
        return false;
    }
}

int StructFieldValue::fieldIdFromRawId(int raw_id, uint16_t version) const {
    if (version != _version) {
        return getStructType().getField(raw_id, _version).getId(version);
    }
    return raw_id;
}

void StructFieldValue::getRawFieldIds(vector<int> &raw_ids) const {
    raw_ids.clear();

    size_t count(0);
    for (uint32_t i = 0; i < _chunks.size(); ++i) {
        count += _chunks[i].getEntries().size();
    }
    raw_ids.reserve(count);
    for (uint32_t i = 0; i < _chunks.size(); ++i) {
        const SerializableArray::EntryMap & entries = _chunks[i].getEntries();
        for (const SerializableArray::Entry & entry : entries) {
            raw_ids.emplace_back(entry.id());
        }
    }
    sort(raw_ids.begin(), raw_ids.end());
    raw_ids.erase(unique(raw_ids.begin(), raw_ids.end()), raw_ids.end());
}

void
StructFieldValue::getRawFieldIds(vector<int> &raw_ids,
                                 const FieldSet& fieldSet) const {
    raw_ids.clear();

    for (uint32_t i = 0; i < _chunks.size(); ++i) {
        const SerializableArray::EntryMap & entries = _chunks[i].getEntries();
        for (const SerializableArray::Entry & entry : entries) {
            if (fieldSet.contains(getStructType().getField(entry.id(), _version))) {
                raw_ids.push_back(entry.id());
            }
        }
    }
    sort(raw_ids.begin(), raw_ids.end());
    raw_ids.erase(unique(raw_ids.begin(), raw_ids.end()), raw_ids.end());
}

bool
StructFieldValue::hasField(const vespalib::stringref & name) const
{
    return getStructType().hasField(name);
}

const Field&
StructFieldValue::getField(const vespalib::stringref & name) const
{
    return getStructType().getField(name);
}

namespace {

void
createFV(FieldValue & value, const DocumentTypeRepo & repo, nbostream & stream, const DocumentType & doc_type, uint32_t version)
{
    FixedTypeRepo frepo(repo, doc_type);
    VespaDocumentDeserializer deserializer(frepo, stream, version);
    deserializer.read(value);
}

}

FieldValue::UP
StructFieldValue::getFieldValue(const Field& field) const
{
    int fieldId = field.getId(_version);

    for (int i = _chunks.size() - 1; i >= 0; --i) {
        vespalib::ConstBufferRef buf = _chunks[i].get(fieldId);
        if (buf.size() != 0) {
            nbostream stream(buf.c_str(), buf.size());
            FieldValue::UP value(field.getDataType().createFieldValue());
            if ((_repo == NULL) && (_doc_type != NULL)) {
                DocumentTypeRepo::UP tmpRepo(new DocumentTypeRepo(*_doc_type));
                createFV(*value, *tmpRepo, stream, *_doc_type, _version);
            } else {
                createFV(*value, *_repo, stream, *_doc_type, _version);
            }
            return value;
        }
    }
    return FieldValue::UP();
}

vespalib::ConstBufferRef
StructFieldValue::getRawField(uint32_t id) const
{
    for (int i = _chunks.size() - 1; i >= 0; --i) {
        vespalib::ConstBufferRef buf = _chunks[i].get(id);
        if (buf.size() > 0) {
            return buf;
        }
    }

    return vespalib::ConstBufferRef();
}

bool
StructFieldValue::getFieldValue(const Field& field, FieldValue& value) const
{
    int fieldId = field.getId(_version);

    vespalib::ConstBufferRef buf = getRawField(fieldId);
    if (buf.size() > 0) {
        nbostream_longlivedbuf stream(buf.c_str(), buf.size());
        if ((_repo == NULL) && (_doc_type != NULL)) {
            DocumentTypeRepo::UP tmpRepo(new DocumentTypeRepo(*_doc_type));
            createFV(value, *tmpRepo, stream, *_doc_type, _version);
        } else {
            createFV(value, *_repo, stream, *_doc_type, _version);
        }
        return true;
    }
    return false;
}

bool
StructFieldValue::hasFieldValue(const Field& field) const
{
    for (int i = _chunks.size() - 1; i >= 0; --i) {
        if (_chunks[i].has(field.getId(_version))) {
            return true;
        }
    }

    return false;
}

void
StructFieldValue::setFieldValue(const Field& field, FieldValue::UP value)
{
    int fieldId = field.getId(_version);
    std::unique_ptr<ByteBuffer> serialized(value->serialize());
    if (serialized->getLength() >= 0x4000000) { // Max 64 MB fields.
        throw SerializeException(vespalib::make_string(
                        "Field value for field %i larger than 64 MB",
                        fieldId), VESPA_STRLOC);
    }
    serialized->flip();
    if (_chunks.empty()) {
        _chunks.push_back(SerializableArray::UP(new SerializableArray()));
    }

    _chunks.back().set(fieldId, std::move(serialized));

    _hasChanged = true;
}

void
StructFieldValue::removeFieldValue(const Field& field)
{
    for (uint32_t i = 0; i < _chunks.size(); ++i) {
        _chunks[i].clear(field.getId(_version));
    }
    _hasChanged = true;
}

void
StructFieldValue::clear()
{
    _chunks.clear();
    _hasChanged = true;
}

// FieldValue implementation.
FieldValue&
StructFieldValue::assign(const FieldValue& value)
{
    const StructFieldValue& other(dynamic_cast<const StructFieldValue&>(value));
    return operator=(other);
}

int
StructFieldValue::compare(const FieldValue& otherOrg) const
{
    int comp = StructuredFieldValue::compare(otherOrg);
    if (comp != 0) {
        return comp;
    }
    const StructFieldValue& other = static_cast<const StructFieldValue&>(otherOrg);

    std::vector<int> a;
    getRawFieldIds(a);
    std::vector<int> b;
    other.getRawFieldIds(b);

    for (size_t i(0); i < std::min(a.size(), b.size()); i++) {
        if (a[i] != b[i]) {
            return a[i] < b[i] ? -1 : 1;
        }
        vespalib::ConstBufferRef ar = getRawField(a[i]);
        vespalib::ConstBufferRef br = other.getRawField(a[i]);
        comp = memcmp(ar.c_str(), br.c_str(), std::min(ar.size(), br.size()));
        if (comp != 0) {
            return comp;
        }
        if ( ar.size() != br.size()) {
            return ar.size() < br.size() ? -1 : 1;
        }
    }
    if (a.size() != b.size()) {
        return a.size() < b.size() ? -1 : 1;
    }
    return 0;
}

uint32_t
StructFieldValue::calculateChecksum() const
{
    ByteBuffer::UP buffer(serialize());
    vespalib::crc_32_type calculator;
    calculator.process_bytes(buffer->getBuffer(), buffer->getPos());
    return calculator.checksum();
}

void
StructFieldValue::printXml(XmlOutputStream& xos) const
{
    if (getType() == PositionDataType::getInstance()
        && getFieldValue(getField(PositionDataType::FIELD_Y))
        && getFieldValue(getField(PositionDataType::FIELD_X)))
    {
        double ns = getFieldValue(getField(PositionDataType::FIELD_Y))->getAsInt() / 1.0e6;
        double ew = getFieldValue(getField(PositionDataType::FIELD_X))->getAsInt() / 1.0e6;
        vespalib::string buf = vespalib::make_vespa_string("%s%.6f;%s%.6f",
                (ns < 0 ? "S" : "N"),
                (ns < 0 ? (-ns) : ns),
                (ew < 0 ? "W" : "E"),
                (ew < 0 ? (-ew) : ew));
        xos << buf;
        return;
    }
    for (const_iterator it = begin(); it != end(); ++it) {
        xos << XmlTag(it.field().getName());
        getValue(it.field())->printXml(xos);
        xos << XmlEndTag();
    }
}

void
StructFieldValue::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    out << "Struct " << getDataType()->getName() << "(";
    int count = 0;
    for (const_iterator it = begin(); it != end(); ++it) {
        if (count++ != 0) {
            out << ",";
        }
        out << "\n" << indent << "  " << it.field().getName() << " - ";
        getValue(it.field())->print(out, verbose, indent + "  ");
    }
    if (count > 0) out << "\n" << indent;
    out << ")";
}

bool
StructFieldValue::empty() const
{
    for (uint32_t i = 0; i < _chunks.size(); ++i) {
        if (!_chunks[i].empty()) {
            return false;
        }
    }

    return true;
}

void
StructFieldValue::reset()
{
    clear();
    _hasChanged = false;
}

struct StructFieldValue::FieldIterator : public StructuredIterator {
    const StructFieldValue& _struct;
    std::vector<int> _ids;
    std::vector<int>::iterator _cur;
    int _version;

    FieldIterator(const StructFieldValue& s, int version)
        : _struct(s),
          _ids(),
          _cur(_ids.begin()),
          _version(version)
    {
        s.getRawFieldIds(_ids);
        _cur = _ids.begin();
    }

    void skipTo(int fieldId) {
        while (_cur != _ids.end() && fieldId != *_cur) {
            ++_cur;
        }
    }

    virtual const Field* getNextField() {
        while (_cur != _ids.end()) {
            int id = *_cur++;
            try {
                return &_struct.getStructType().getField(id, _version);
            } catch (FieldNotFoundException& e) {
                // Should not get this exception until after we've moved the iterator.
                LOG(debug, "exception for id %d version %d", id, _version);
                LOG(debug, "struct data type: %s", _struct.getType().toString(true).c_str());
            }
        }
        return 0;
    }
};

StructuredFieldValue::StructuredIterator::UP
StructFieldValue::getIterator(const Field* toFind) const
{
    StructuredIterator::UP ret;

    FieldIterator *fi = new FieldIterator(*this, _version);
    ret.reset(fi);

    if (toFind != NULL) {
        fi->skipTo(toFind->getId(_version));
    }
    return ret;
}

void
StructFieldValue::setType(const DataType& type)
{
    clear();
    StructuredFieldValue::setType(type);
}

} // document
