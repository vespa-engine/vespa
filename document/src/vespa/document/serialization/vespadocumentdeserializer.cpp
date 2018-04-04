// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vespadocumentdeserializer.h"
#include "annotationdeserializer.h"
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespadocumentdeserializer");

using std::vector;
using vespalib::Slime;
using vespalib::asciistream;
using vespalib::nbostream;
using vespalib::Memory;
using vespalib::stringref;
using vespalib::compression::CompressionConfig;

namespace document {

namespace {
template <typename Input>
uint32_t readSize(Input &input, uint16_t version) {
    if (version < 7) {
        readValue<uint32_t>(input);  // type id
        return readValue<uint32_t>(input);
    } else {
        return getInt1_2_4Bytes(input);
    }
}

template <typename T, typename Input>
void skipIfOld(Input &input, uint16_t version) {
    if (version < 7) {
        readValue<T>(input);
    }
}

uint32_t
getChunkCount(uint8_t contentCode)
{
    uint8_t chunks = 0;
    if (contentCode & 0x02) {
        ++chunks;
    }
    if (contentCode & 0x04) {
        ++chunks;
    }
    return chunks;
}

}  // namespace

void VespaDocumentDeserializer::readDocument(Document &value) {
    read(value.getId());
    uint8_t content_code = readValue<uint8_t>(_stream);

    LOG(spam, "content_code is %u", content_code);
    const DocumentType *type = readDocType(value.getType());
    if (type) {
        Document newDoc(*type, value.getId(), true);
        value.swap(newDoc);
    }
    value.setRepo(_repo.getDocumentTypeRepo());

    FixedTypeRepo repo(_repo.getDocumentTypeRepo(), value.getType());
    VarScope<FixedTypeRepo> repo_scope(_repo, repo);
    uint32_t chunkCount = getChunkCount(content_code);
    value.getFields().reset();
    for (uint32_t i = 0; i < chunkCount; ++i) {
        readStructNoReset(value.getFields());
    }
}

void VespaDocumentDeserializer::read(FieldValue &value) {
    value.accept(*this);
}

const DocumentType*
VespaDocumentDeserializer::readDocType(const DocumentType &guess)
{
    stringref type_name(_stream.peek());

    _stream.adjustReadPos(type_name.size() + 1);
    readValue<uint16_t>(_stream);  // skip version

    if (guess.getName() != type_name) {
        const DocumentType *type =
            _repo.getDocumentTypeRepo().getDocumentType(type_name);
        if (!type) {
            throw DocumentTypeNotFoundException(type_name, VESPA_STRLOC);
        }
        return type;
    }
    return 0;
}

void VespaDocumentDeserializer::read(DocumentId &value) {
    stringref s(_stream.peek());
    value.set(s);
    _stream.adjustReadPos(s.size() + 1);
}

void VespaDocumentDeserializer::read(DocumentType &value) {
    const DocumentType *doc_type = readDocType(value);
    if (doc_type) {
        value = *doc_type;
    }
}

void VespaDocumentDeserializer::read(Document &value) {
    uint16_t version = readValue<uint16_t>(_stream);
    VarScope<uint16_t> version_scope(_version, version);

    if (version < 6 || version > 8) {
        asciistream msg;
        msg << "Unrecognized serialization version " << version;
        throw DeserializeException(msg.str(), VESPA_STRLOC);
    }

    if (version >= 7) {
        uint32_t data_size = readValue<uint32_t>(_stream);
        size_t data_start_size = _stream.size();
        readDocument(value);
        if (version == 7) {
            readValue<uint32_t>(_stream);  // Skip crc value.
        }
        if (data_start_size - _stream.size() != data_size) {
            asciistream msg;
            msg << "Length mismatch. Was "
                << data_start_size - _stream.size()
                << ", expected " << data_size << ".";
            throw DeserializeException(msg.str(), VESPA_STRLOC);
        }
    } else {  // version <= 6
        getInt2_4_8Bytes(_stream);  // skip document length
        readDocument(value);
        readValue<uint32_t>(_stream);  // Skip crc value.
    }
}

void VespaDocumentDeserializer::read(AnnotationReferenceFieldValue &value) {
    value.setAnnotationIndex(getInt1_2_4Bytes(_stream));
}

void VespaDocumentDeserializer::read(ArrayFieldValue &value) {
    uint32_t size = readSize(_stream, _version);
    value.clear();
    value.resize(size);
    for (uint32_t i = 0; i < size; ++i) {
        skipIfOld<uint32_t>(_stream, _version);  // element size
        value[i].accept(*this);  // Double dispatch to call the correct read()
    }
}

void VespaDocumentDeserializer::read(MapFieldValue &value) {
    value.clear();
    uint32_t size = readSize(_stream, _version);
    value.resize(size);
    for (auto & pair : value) {
        skipIfOld<uint32_t>(_stream, _version);  // element size
        pair.first->accept(*this);  // Double dispatch to call the correct read()
        pair.second->accept(*this);  // Double dispatch to call the correct read()
    }
}

namespace {
template <typename T> struct ValueType { typedef typename T::Number Type; };
template <> struct ValueType<ShortFieldValue> { typedef uint16_t Type; };
template <> struct ValueType<IntFieldValue> { typedef uint32_t Type; };
template <> struct ValueType<LongFieldValue> { typedef uint64_t Type; };
template <>
struct ValueType<RawFieldValue> { typedef vespalib::string Type; };

template <typename T>
void readFieldValue(nbostream &input, T &value) {
    typename ValueType<T>::Type val;
    input >> val;
    value.setValue(val);
}

template <typename Input>
stringref readAttributeString(Input &input) {
    uint8_t size;
    input >> size;
    stringref s(input.peek(), size);
    input.adjustReadPos(size + 1);
    return s;
}
}  // namespace

void VespaDocumentDeserializer::read(ByteFieldValue &value) {
    readFieldValue(_stream, value);
}

void VespaDocumentDeserializer::read(DoubleFieldValue &value) {
    readFieldValue(_stream, value);
}

void VespaDocumentDeserializer::read(FloatFieldValue &value) {
    readFieldValue(_stream, value);
}

void VespaDocumentDeserializer::read(IntFieldValue &value) {
    readFieldValue(_stream, value);
}

void VespaDocumentDeserializer::read(LongFieldValue &value) {
    readFieldValue(_stream, value);
}

void VespaDocumentDeserializer::read(PredicateFieldValue &value) {
    uint32_t stored_size = readValue<uint32_t>(_stream);
    Memory memory(_stream.peek(), _stream.size());
    std::unique_ptr<Slime> slime(new Slime);
    size_t size = vespalib::slime::BinaryFormat::decode(memory, *slime);
    if (size != stored_size) {
        throw DeserializeException("Specified slime size don't match actual "
                                   "slime size.", VESPA_STRLOC);
    }
    value = PredicateFieldValue(std::move(slime));
    _stream.adjustReadPos(size);
}

namespace {
template <typename FV>
void setValue(FV &field_value, const stringref &val, bool use_ref) {
    if (use_ref) {
        field_value.setValueRef(val);
    } else {
        field_value.setValue(val);
    }
}
}  // namespace

void VespaDocumentDeserializer::read(RawFieldValue &value) {
    uint32_t size = readValue<uint32_t>(_stream);
    stringref val(_stream.peek(), size);
    setValue(value, val, _stream.isLongLivedBuffer());
    _stream.adjustReadPos(size);
}

void VespaDocumentDeserializer::read(ShortFieldValue &value) {
    readFieldValue(_stream, value);
}

void VespaDocumentDeserializer::read(StringFieldValue &value) {
    uint8_t coding = readValue<uint8_t>(_stream);
    size_t size = getInt1_4Bytes(_stream);
    if (size == 0) {
        throw DeserializeException("invalid zero string length", VESPA_STRLOC);
    }
    stringref val(_stream.peek(), size - 1);
    _stream.adjustReadPos(size);
    setValue(value, val, _stream.isLongLivedBuffer());
    if (coding & 0x40) {
        uint32_t serializedAnnotationsSize = readValue<uint32_t>(_stream);
        value.setSpanTrees(vespalib::ConstBufferRef(_stream.peek(), serializedAnnotationsSize),
                           _repo, _version, _stream.isLongLivedBuffer());
        _stream.adjustReadPos(serializedAnnotationsSize);
    } else {
        value.clearSpanTrees();
    }
}

namespace {

typedef SerializableArray::EntryMap FieldInfo;

void readFieldInfo(nbostream& input, SerializableArray::EntryMap & field_info) __attribute__((noinline));

void readFieldInfo(nbostream& input, SerializableArray::EntryMap & field_info) {
    size_t field_count = getInt1_4Bytes(input);
    field_info.reserve(field_count);
    uint32_t offset = 0;
    for (size_t i = 0; i < field_count; ++i) {
        const uint32_t id = getInt1_4Bytes(input);
        const uint32_t size = getInt2_4_8Bytes(input);
        field_info.emplace_back(id, size, offset);
        offset += size;
    }
}
}  // namespace

void VespaDocumentDeserializer::readStructNoReset(StructFieldValue &value) {
    size_t start_size = _stream.size();
    size_t data_size;
    if (_version < 6) {
        throw DeserializeException("Illegal struct serialization version.", VESPA_STRLOC);
    } else if (_version < 7) {
        data_size = getInt2_4_8Bytes(_stream);
    } else {
        data_size = readValue<uint32_t>(_stream);
    }

    CompressionConfig::Type compression_type =
        CompressionConfig::Type(readValue<uint8_t>(_stream));

    SerializableArray::EntryMap  field_info;
    size_t uncompressed_size = 0;
    if (CompressionConfig::isCompressed(compression_type)) {
        uncompressed_size = getInt2_4_8Bytes(_stream);
    }
    readFieldInfo(_stream, field_info);
    if (_version < 7) {
        data_size -= (start_size - _stream.size());
    }
    if (CompressionConfig::isCompressed(compression_type)) {
        if ((compression_type != CompressionConfig::LZ4)) {
            throw DeserializeException("Unsupported compression type.", VESPA_STRLOC);
        } else if (data_size > _stream.size()) {
            throw DeserializeException("Invalid compressed struct data.", VESPA_STRLOC);
        }
    }

    if (data_size > 0) {
        ByteBuffer::UP buffer(_stream.isLongLivedBuffer()
                          ? new ByteBuffer(_stream.peek(), data_size)
                          : ByteBuffer::copyBuffer(_stream.peek(), data_size));
        LOG(spam, "Lazy deserializing into %s with _version %u",
            value.getDataType()->getName().c_str(), _version);
        value.lazyDeserialize(_repo, _version, std::move(field_info),
                              std::move(buffer), compression_type, uncompressed_size);
        _stream.adjustReadPos(data_size);
    }
}

void
VespaDocumentDeserializer::read(StructFieldValue& value)
{
    value.reset();
    readStructNoReset(value);
}

void VespaDocumentDeserializer::read(WeightedSetFieldValue &value) {
    value.clear();
    readValue<uint32_t>(_stream);  // skip type id
    uint32_t size = readValue<uint32_t>(_stream);
    value.reserve(size);
    for (uint32_t i = 0; i < size; ++i) {
        readValue<uint32_t>(_stream);  // skip element size
        FieldValue::UP child = value.createNested();
        child->accept(*this);  // Double dispatch to call the correct read()
        uint32_t weight = readValue<uint32_t>(_stream);
        value.push_back(std::move(child), weight);
    }
}


void
VespaDocumentDeserializer::read(TensorFieldValue &value)
{
    size_t length = _stream.getInt1_4Bytes();
    if (length > _stream.size()) {
        throw DeserializeException(vespalib::make_string("Stream failed size(%zu), needed(%zu) to deserialize tensor field value", _stream.size(), length),
                                   VESPA_STRLOC);
    }
    std::unique_ptr<vespalib::tensor::Tensor> tensor;
    if (length != 0) {
        nbostream wrapStream(_stream.peek(), length);
        tensor = vespalib::tensor::TypedBinaryFormat::deserialize(wrapStream);
        if (wrapStream.size() != 0) {
            throw DeserializeException("Leftover bytes deserializing "
                                       "tensor field value.",
                                       VESPA_STRLOC);
        }
    }
    value.assignDeserialized(std::move(tensor));
    _stream.adjustReadPos(length);
}

void VespaDocumentDeserializer::read(ReferenceFieldValue& value) {
    const bool hasId(readValue<uint8_t>(_stream) == 1);
    if (hasId) {
        DocumentId id;
        read(id);
        value.setDeserializedDocumentId(id);
    } else {
        value.clearChanged();
    }
}

}  // document
