// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".document.structfieldvalue");

using std::vector;
using vespalib::nbostream;
using vespalib::nbostream_longlivedbuf;
using vespalib::make_string;
using namespace vespalib::xml;

namespace document {

StructFieldValue::StructFieldValue(const DataType &type)
    : StructuredFieldValue(Type::STRUCT, type),
      _fields(),
      _repo(nullptr),
      _doc_type(nullptr),
      _version(Document::getNewestSerializationVersion()),
      _hasChanged(true)
{
}

StructFieldValue::StructFieldValue(const StructFieldValue & rhs) = default;
StructFieldValue & StructFieldValue::operator = (const StructFieldValue & rhs) = default;

StructFieldValue::~StructFieldValue() noexcept = default;

const StructDataType &
StructFieldValue::getStructType() const {
    return static_cast<const StructDataType &>(getType());
}

void
StructFieldValue::lazyDeserialize(const FixedTypeRepo &repo, uint16_t version, SerializableArray::EntryMap && fm, ByteBuffer buffer)
{
    _repo = repo.getDocumentTypeRepoPtr();
    _doc_type = repo.getDocumentTypePtr();
    _version = version;

    _fields.set(std::move(fm), std::move(buffer));
    _hasChanged = false;
}

bool StructFieldValue::serializeField(int field_id, uint16_t version, FieldValueWriter &writer) const {
    if (version == _version) {
        vespalib::ConstBufferRef buf = _fields.get(field_id);
        if ( buf.size() != 0) {
            writer.writeSerializedData(buf.data(), buf.size());
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

vector<int>
StructFieldValue::getRawFieldIds() const {
    vector<int> raw_ids;
    raw_ids.reserve(_fields.getEntries().size());
    for (const SerializableArray::Entry & entry : _fields.getEntries()) {
        raw_ids.emplace_back(entry.id());
    }
    sort(raw_ids.begin(), raw_ids.end());
    raw_ids.erase(unique(raw_ids.begin(), raw_ids.end()), raw_ids.end());
    return raw_ids;
}

void
StructFieldValue::getRawFieldIds(vector<int> &raw_ids,const FieldSet& fieldSet) const {
    raw_ids.clear();

    for (const SerializableArray::Entry & entry : _fields.getEntries()) {
        if (fieldSet.contains(getStructType().getField(entry.id()))) {
            raw_ids.emplace_back(entry.id());
        }
    }
    sort(raw_ids.begin(), raw_ids.end());
    raw_ids.erase(unique(raw_ids.begin(), raw_ids.end()), raw_ids.end());
}

bool
StructFieldValue::hasField(vespalib::stringref name) const
{
    return getStructType().hasField(name);
}

const Field&
StructFieldValue::getField(vespalib::stringref name) const
{
    return getStructType().getField(name);
}

namespace {

void
createFV(FieldValue & value, const DocumentTypeRepo * repo, nbostream & stream, const DocumentType * doc_type, uint32_t version)
{
    FixedTypeRepo frepo(repo, doc_type);
    try {
        VespaDocumentDeserializer deserializer(frepo, stream, version);
        deserializer.read(value);
    } catch (WrongTensorTypeException &) {
        // A tensor field will appear to have no tensor if the stored tensor
        // cannot be assigned to the tensor field.
    }
}

}

FieldValue::UP
StructFieldValue::getFieldValue(const Field& field) const
{
    int fieldId = field.getId();

    vespalib::ConstBufferRef buf = _fields.get(fieldId);
    if (buf.size() != 0) {
        nbostream stream(buf.c_str(), buf.size());
        FieldValue::UP value(field.getDataType().createFieldValue());
        if ((_repo == nullptr) && (_doc_type != nullptr)) {
            DocumentTypeRepo tmpRepo(*_doc_type);
            createFV(*value, &tmpRepo, stream, _doc_type, _version);
        } else {
            createFV(*value, _repo, stream, _doc_type, _version);
        }
        return value;
    }
    return FieldValue::UP();
}

vespalib::ConstBufferRef
StructFieldValue::getRawField(uint32_t id) const
{
    vespalib::ConstBufferRef buf = _fields.get(id);
    if (buf.size() > 0) {
        return buf;
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
        if ((_repo == nullptr) && (_doc_type != nullptr)) {
            DocumentTypeRepo tmpRepo(*_doc_type);
            createFV(value, &tmpRepo, stream, _doc_type, _version);
        } else {
            createFV(value, _repo, stream, _doc_type, _version);
        }
        return true;
    }
    return false;
}

bool
StructFieldValue::hasFieldValue(const Field& field) const
{
    return _fields.has(field.getId());
}

namespace {

std::unique_ptr<ByteBuffer>
serializeDoc(const FieldValue & fv) {
    nbostream stream = fv.serialize();
    nbostream::Buffer buf;
    stream.swap(buf);
    size_t sz = buf.size();
    return std::make_unique<ByteBuffer>(nbostream::Buffer::stealAlloc(std::move(buf)), sz);
}

}
void
StructFieldValue::setFieldValue(const Field& field, FieldValue::UP value)
{
    int fieldId = field.getId();

    std::unique_ptr<ByteBuffer> serialized = serializeDoc(*value);

    _fields.set(fieldId, std::move(*serialized));

    _hasChanged = true;
}

void
StructFieldValue::removeFieldValue(const Field& field)
{
    _fields.clear(field.getId());
    _hasChanged = true;
}

void
StructFieldValue::clear()
{
    _fields.clear();
    _hasChanged = true;
}

// FieldValue implementation.
FieldValue&
StructFieldValue::assign(const FieldValue& value)
{
    const auto & other(dynamic_cast<const StructFieldValue&>(value));
    return operator=(other);
}

int
StructFieldValue::compare(const FieldValue& otherOrg) const
{
    int comp = StructuredFieldValue::compare(otherOrg);
    if (comp != 0) {
        return comp;
    }
    const auto & other = static_cast<const StructFieldValue&>(otherOrg);

    std::vector<int> a = getRawFieldIds();
    std::vector<int> b = other.getRawFieldIds();

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
    nbostream buffer(serialize());
    vespalib::crc_32_type calculator;
    calculator.process_bytes(buffer.peek(), buffer.size());
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
    return _fields.empty();
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

    explicit FieldIterator(const StructFieldValue& s)
        : _struct(s),
          _ids(s.getRawFieldIds()),
          _cur(_ids.begin())
    { }

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
        return nullptr;
    }
};

StructuredFieldValue::StructuredIterator::UP
StructFieldValue::getIterator(const Field* toFind) const
{
    auto fi = std::make_unique<FieldIterator>(*this);

    if (toFind != nullptr) {
        fi->skipTo(toFind->getId());
    }
    return fi;
}

void
StructFieldValue::setType(const DataType& type)
{
    reset();
    StructuredFieldValue::setType(type);
}

} // document
