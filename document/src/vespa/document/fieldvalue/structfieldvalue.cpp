// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structfieldvalue.h"
#include "fieldvaluewriter.h"
#include "document.h"
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".document.structfieldvalue");

using std::vector;
using vespalib::nbostream;
using vespalib::nbostream_longlivedbuf;
using vespalib::make_string;
using vespalib::compression::CompressionConfig;
using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(StructFieldValue, StructuredFieldValue);

StructFieldValue::StructFieldValue(const DataType &type)
    : StructuredFieldValue(type),
      _repo(NULL),
      _doc_type(NULL),
      _version(Document::getNewestSerializationVersion()),
      _hasChanged(true)
{
}

StructFieldValue::~StructFieldValue() { }

StructFieldValue::Chunks::~Chunks() { }

void
StructFieldValue::Chunks::push_back(SerializableArray::UP item) {
    assert(_sz < 2);
    _chunks[_sz++].reset(item.release());
}

void
StructFieldValue::Chunks::clear() {
    _chunks[0].reset();
    _chunks[1].reset();
    _sz = 0;
}

void
StructFieldValue::Chunks::swap(Chunks & rhs) {
    _chunks[0].swap(rhs._chunks[0]);
    _chunks[1].swap(rhs._chunks[1]);
    std::swap(_sz, rhs._sz);
}

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

const StructDataType &
StructFieldValue::getStructType() const {
    return static_cast<const StructDataType &>(getType());
}

const CompressionConfig &
StructFieldValue::getCompressionConfig() const {
    return getStructType().getCompressionConfig();
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
        const Field &f = getStructType().getField(field_id);
        writer.writeFieldValue(*getFieldValue(f));
        return true;
    } catch (FieldNotFoundException &) {
        LOG(info, "Dropping field %d when serializing to a newer version", field_id);
        return false;
    }
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
            if (fieldSet.contains(getStructType().getField(entry.id()))) {
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
    int fieldId = field.getId();

    for (int i = _chunks.size() - 1; i >= 0; --i) {
        vespalib::ConstBufferRef buf = _chunks[i].get(fieldId);
        if (buf.size() != 0) {
            nbostream stream(buf.c_str(), buf.size());
            FieldValue::UP value(field.getDataType().createFieldValue());
            if ((_repo == NULL) && (_doc_type != NULL)) {
                std::unique_ptr<const DocumentTypeRepo> tmpRepo(new DocumentTypeRepo(*_doc_type));
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
    int fieldId = field.getId();

    vespalib::ConstBufferRef buf = getRawField(fieldId);
    if (buf.size() > 0) {
        nbostream_longlivedbuf stream(buf.c_str(), buf.size());
        if ((_repo == NULL) && (_doc_type != NULL)) {
            std::unique_ptr<const DocumentTypeRepo> tmpRepo(new DocumentTypeRepo(*_doc_type));
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
        if (_chunks[i].has(field.getId())) {
            return true;
        }
    }

    return false;
}

void
StructFieldValue::setFieldValue(const Field& field, FieldValue::UP value)
{
    int fieldId = field.getId();
    std::unique_ptr<ByteBuffer> serialized(value->serialize());
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
        _chunks[i].clear(field.getId());
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
        vespalib::string buf = make_string("%s%.6f;%s%.6f",
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

    FieldIterator(const StructFieldValue& s)
        : _struct(s),
          _ids(),
          _cur(_ids.begin())
    {
        s.getRawFieldIds(_ids);
        _cur = _ids.begin();
    }

    void skipTo(int fieldId) {
        while (_cur != _ids.end() && fieldId != *_cur) {
            ++_cur;
        }
    }

    const Field* getNextField() override {
        while (_cur != _ids.end()) {
            int id = *_cur++;
            try {
                return &_struct.getStructType().getField(id);
            } catch (FieldNotFoundException& e) {
                // Should not get this exception until after we've moved the iterator.
                LOG(debug, "exception for id %d", id);
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

    FieldIterator *fi = new FieldIterator(*this);
    ret.reset(fi);

    if (toFind != NULL) {
        fi->skipTo(toFind->getId());
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
