// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vespadocumentdeserializer.h"
#include "annotationdeserializer.h"
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>
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
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/base/idstringexception.h>


#include <vespa/log/log.h>
LOG_SETUP(".vespadocumentdeserializer");

using std::vector;
using vespalib::Slime;
using vespalib::asciistream;
using vespalib::nbostream;
using vespalib::Memory;
using vespalib::stringref;
using vespalib::compression::CompressionConfig;
using vespalib::ConstBufferRef;
using vespalib::make_string_short::fmt;
using vespalib::eval::FastValueBuilderFactory;

namespace document {

namespace {
template <typename Input>
uint32_t readSize(Input &input) {
        return getInt1_2_4Bytes(input);
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

void
VespaDocumentDeserializer::readDocument(Document &value) {
    read(value.getId());
    uint8_t content_code = readValue<uint8_t>(_stream);

    LOG(spam, "content_code is %u", content_code);
    const DocumentType *type = readDocType(value.getType());
    if (type) {
        Document::verifyIdAndType(value.getId(), type);
        value.setType(*type);
        value.setLastModified(0);
    } else {
        value.getFields().reset();
    }
    value.setRepo(_repo.getDocumentTypeRepo());
    value.getFields().setDocumentType(value.getType());

    FixedTypeRepo repo(_repo.getDocumentTypeRepo(), value.getType());
    VarScope<FixedTypeRepo> repo_scope(_repo, repo);
    uint32_t chunkCount = getChunkCount(content_code);
    for (uint32_t i = 0; i < chunkCount; ++i) {
        readStructNoReset(value.getFields());
    }
}

void
VespaDocumentDeserializer::read(FieldValue &value) {
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

void
VespaDocumentDeserializer::read(DocumentId &value) {
    stringref s(_stream.peek());
    value.set(s);
    _stream.adjustReadPos(s.size() + 1);
}

void
VespaDocumentDeserializer::read(DocumentType &value) {
    const DocumentType *doc_type = readDocType(value);
    if (doc_type) {
        value = *doc_type;
    }
}

void
VespaDocumentDeserializer::read(Document &value) {
    uint16_t version = readValue<uint16_t>(_stream);
    VarScope<uint16_t> version_scope(_version, version);

    if (version < 8 || version > 8) {
        asciistream msg;
        msg << "Unrecognized serialization version " << version;
        throw DeserializeException(msg.str(), VESPA_STRLOC);
    }

    uint32_t data_size = readValue<uint32_t>(_stream);
    size_t data_start_size = _stream.size();
    readDocument(value);

    if (data_start_size - _stream.size() != data_size) {
        asciistream msg;
        msg << "Length mismatch. Was "
            << data_start_size - _stream.size()
            << ", expected " << data_size << ".";
        throw DeserializeException(msg.str(), VESPA_STRLOC);
    }

}

void
VespaDocumentDeserializer::read(AnnotationReferenceFieldValue &value) {
    value.setAnnotationIndex(getInt1_2_4Bytes(_stream));
}

void
VespaDocumentDeserializer::read(ArrayFieldValue &value) {
    uint32_t size = readSize(_stream);
    value.clear();
    value.resize(size);
    for (uint32_t i = 0; i < size; ++i) {
        value[i].accept(*this);  // Double dispatch to call the correct read()
    }
}

void
VespaDocumentDeserializer::read(MapFieldValue &value) {
    value.clear();
    uint32_t size = readSize(_stream);
    value.resize(size);
    for (auto & pair : value) {
        pair.first->accept(*this);  // Double dispatch to call the correct read()
        pair.second->accept(*this);  // Double dispatch to call the correct read()
    }
}

namespace {
template <typename T> struct ValueType { using Type = typename T::Number; };
template <> struct ValueType<BoolFieldValue> { using Type = bool; };
template <> struct ValueType<ShortFieldValue> { using Type = uint16_t; };
template <> struct ValueType<IntFieldValue> { using Type = uint32_t; };
template <> struct ValueType<LongFieldValue> { using Type = uint64_t; };
template <> struct ValueType<RawFieldValue> { using Type = vespalib::string; };

template <typename T>
void readFieldValue(nbostream &input, T &value) {
    typename ValueType<T>::Type val;
    input >> val;
    value.setValue(val);
}

}  // namespace

void
VespaDocumentDeserializer::read(BoolFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(ByteFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(DoubleFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(FloatFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(IntFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(LongFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(PredicateFieldValue &value) {
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
void setValue(FV &field_value, stringref val, bool use_ref) {
    if (use_ref) {
        field_value.setValueRef(val);
    } else {
        field_value.setValue(val);
    }
}
}  // namespace

void
VespaDocumentDeserializer::read(RawFieldValue &value) {
    uint32_t size = readValue<uint32_t>(_stream);
    stringref val(_stream.peek(), size);
    setValue(value, val, _stream.isLongLivedBuffer());
    _stream.adjustReadPos(size);
}

void
VespaDocumentDeserializer::read(ShortFieldValue &value) {
    readFieldValue(_stream, value);
}

void
VespaDocumentDeserializer::read(StringFieldValue &value) {
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
        auto span_buf = vespalib::ConstBufferRef(_stream.peek(), serializedAnnotationsSize);
        _stream.adjustReadPos(serializedAnnotationsSize); // Trigger any out-of-bounds before using buffer range.
        value.setSpanTrees(span_buf, _repo, _version, _stream.isLongLivedBuffer());
    } else {
        value.clearSpanTrees();
    }
}

namespace {

using FieldInfo = SerializableArray::EntryMap;

void readFieldInfo(nbostream& input, SerializableArray::EntryMap & field_info, size_t max_buffer_extent) __attribute__((noinline));

void
readFieldInfo(nbostream& input, SerializableArray::EntryMap & field_info, size_t max_buffer_extent) {
    size_t field_count = getInt1_4Bytes(input);
    field_info.reserve(field_count);
    uint32_t offset = 0;
    for (size_t i = 0; i < field_count; ++i) {
        const uint32_t id = getInt1_4Bytes(input);
        const uint32_t size = getInt2_4_8Bytes(input);
        if (size_t(offset) + size > max_buffer_extent) [[unlikely]] {
            throw DeserializeException(fmt("Field (id=%u, offset=%u, size=%u) extends beyond max buffer extent (%zu)",
                                           id, offset, size, max_buffer_extent), VESPA_STRLOC);
        }
        field_info.emplace_back(id, size, offset);
        offset += size;
    }
}

ByteBuffer
deCompress(CompressionConfig::Type compression, uint32_t uncompressedLength, vespalib::ConstBufferRef compressed) __attribute__((noinline));

ByteBuffer
deCompress(CompressionConfig::Type compression, uint32_t uncompressedLength, vespalib::ConstBufferRef compressed)
{
    using vespalib::compression::decompress;

    assert(compressed.size() != 0);

    ByteBuffer newSerialization(vespalib::alloc::Alloc::alloc(uncompressedLength), uncompressedLength);
    vespalib::DataBuffer unCompressed(newSerialization.getBuffer(), newSerialization.getLength());
    unCompressed.clear();
    try {
        decompress(compression, uncompressedLength, compressed,unCompressed,false);
    } catch (const std::runtime_error & e) {
        throw DeserializeException(fmt( "Document was compressed with code unknown code %d", compression), VESPA_STRLOC);
    }

    if (unCompressed.getDataLen() != (size_t)uncompressedLength) {
        throw DeserializeException(fmt("Did not decompress to the expected length: had %lu, wanted %d, got %zu",
                                       compressed.size(), uncompressedLength, unCompressed.getDataLen()),
                                   VESPA_STRLOC);
    }
    assert(newSerialization.getBuffer() == unCompressed.getData());
    LOG_ASSERT(uncompressedLength == newSerialization.getRemaining());
    return newSerialization;
}

}  // namespace

void
VespaDocumentDeserializer::readStructNoReset(StructFieldValue &value) {
    size_t data_size = readValue<uint32_t>(_stream);

    const auto compression_type = CompressionConfig::Type(readValue<uint8_t>(_stream));
    const bool is_compressed    = CompressionConfig::isCompressed(compression_type);

    SerializableArray::EntryMap field_info;
    size_t uncompressed_size = 0;
    if (is_compressed) {
        uncompressed_size = getInt2_4_8Bytes(_stream);
    }
    if (is_compressed && (compression_type != CompressionConfig::LZ4)) [[unlikely]] {
        throw DeserializeException(fmt("Unsupported compression type: %u", static_cast<uint8_t>(compression_type)), VESPA_STRLOC);
    }
    // Must read field info _prior_ to checking remaining stream size against
    // data_size, as the field info size is not counted as part of data_size.
    readFieldInfo(_stream, field_info, is_compressed ? uncompressed_size : data_size);
    if (data_size > _stream.size()) [[unlikely]] {
        throw DeserializeException(fmt("Struct size (%zu) is greater than remaining buffer size (%zu)",
                                       data_size, _stream.size()), VESPA_STRLOC);
    }
    if (data_size > 0) {
        ByteBuffer buffer = is_compressed
                            ? deCompress(compression_type, uncompressed_size, ConstBufferRef(_stream.peek(), data_size))
                            : (_stream.isLongLivedBuffer()
                                ? ByteBuffer(_stream.peek(), data_size)
                                : ByteBuffer::copyBuffer(_stream.peek(), data_size));
        if (value.getFields().empty()) {
            LOG(spam, "Lazy deserializing into %s with _version %u", value.getDataType()->getName().c_str(), _version);
            value.lazyDeserialize(_repo, _version, std::move(field_info), std::move(buffer));
        } else {
            LOG(debug, "Legacy dual header/body format. -> Merging.");
            StructFieldValue tmp(*value.getDataType());
            tmp.lazyDeserialize(_repo, _version, std::move(field_info), std::move(buffer));
            for (const auto & entry : tmp) {
                try {
                    FieldValue::UP decoded = tmp.getValue(entry);
                    if (decoded) {
                        value.setValue(entry, std::move(decoded));
                    }
                } catch (const vespalib::Exception & e) {
                    LOG(warning, "Failed decoding field '%s' in legacy bodyfield -> Skipping it: %s",
                                 entry.getName().data(), e.what());
                }
            }
        }
        _stream.adjustReadPos(data_size);
    }
}

void
VespaDocumentDeserializer::read(StructFieldValue& value)
{
    value.reset();
    readStructNoReset(value);
}

void
VespaDocumentDeserializer::read(WeightedSetFieldValue &value) {
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
    value.assignDeserialized(readTensor());
}

std::unique_ptr<vespalib::eval::Value>
VespaDocumentDeserializer::readTensor()
{
    size_t length = _stream.getInt1_4Bytes();
    if (length > _stream.size()) {
        throw DeserializeException(fmt("Stream failed size(%zu), needed(%zu) to deserialize tensor field value", _stream.size(), length),
                                   VESPA_STRLOC);
    }
    std::unique_ptr<vespalib::eval::Value> tensor;
    if (length != 0) {
        nbostream wrapStream(_stream.peek(), length);
        try {
            tensor = vespalib::eval::decode_value(wrapStream, FastValueBuilderFactory::get());
        } catch (const vespalib::eval::DecodeValueException &e) {
            throw DeserializeException("tensor value decode failed", e, VESPA_STRLOC);
        }
        if (wrapStream.size() != 0) {
            throw DeserializeException("Leftover bytes deserializing tensor field value.", VESPA_STRLOC);
        }
    }
    _stream.adjustReadPos(length);
    return tensor;
}

void
VespaDocumentDeserializer::read(ReferenceFieldValue& value) {
    const bool hasId(readValue<uint8_t>(_stream) == 1);
    if (hasId) {
        DocumentId id;
        read(id);
        value.setDeserializedDocumentId(id);
    }
}

}  // document
