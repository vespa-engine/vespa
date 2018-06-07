// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vespadocumentserializer.h"
#include "annotationserializer.h"
#include "slime_output_to_vector.h"
#include "util.h"
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/update/updates.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/util/compressor.h>

using std::make_pair;
using std::pair;
using std::vector;
using vespalib::nbostream;
using vespalib::stringref;
using vespalib::string;
using vespalib::slime::BinaryFormat;
using vespalib::compression::CompressionConfig;

namespace document {

VespaDocumentSerializer::VespaDocumentSerializer(nbostream &stream)
    : _stream(stream) {
}

void VespaDocumentSerializer::writeFieldValue(const FieldValue &value) {
    write(value);
}

void VespaDocumentSerializer::writeSerializedData(const void *buf, size_t length) {
    _stream.write(buf, length);
}

void VespaDocumentSerializer::write(const ValueUpdate &value) {
    value.accept(*this);
}

void VespaDocumentSerializer::write(const FieldPathUpdate &value) {
    value.accept(*this);
}

void VespaDocumentSerializer::write(const FieldValue &value) {
    value.accept(*this);
}

void VespaDocumentSerializer::write(const DocumentId &value) {
    string id_string = value.getScheme().toString();
    _stream.write(id_string.data(), id_string.size());
    _stream << static_cast<uint8_t>(0);
}

void VespaDocumentSerializer::write(const DocumentType &value) {
    _stream.write(value.getName().data(), value.getName().size());

    _stream << static_cast<uint8_t>(0)
            << static_cast<uint16_t>(0);  // version
}

uint8_t
VespaDocumentSerializer::getContentCode(bool hasHeader, bool hasBody) const
{
    uint8_t content = 0x01;  // Document type is always present.
    if (hasHeader) {
        content |= 0x02;  // Header is present.
    }
    if (hasBody) {
        content |= 0x04;  // Body is present.
    }
    return content;
}

void VespaDocumentSerializer::write(const Document &value,
                                    DocSerializationMode mode) {
    nbostream doc_stream;
    VespaDocumentSerializer doc_serializer(doc_stream);
    doc_serializer.write(value.getId());

    bool hasHeader = false;
    bool hasBody = false;

    for (const Field & field : value.getFields()) {
        if (field.isHeaderField()) {
            hasHeader = true;
        } else {
            hasBody = true;
        }

        if (hasHeader && hasBody) {
            break;
        }
    }

    if (mode != COMPLETE) {
        hasBody = false;
    }

    doc_stream << getContentCode(hasHeader, hasBody);
    doc_serializer.write(value.getType());

    if (!structNeedsReserialization(value.getFields())) {
        // FIXME(vekterli):
        // Currently assume legacy serialization; a chunk will only ever contain fields
        // _either_ for the header _or_ for the body, never a mixture!
        // This is to avoid horrible breakage whilst ripping out old guts.
        const StructFieldValue::Chunks & chunks = value.getFields().getChunks();
        if (hasHeader) {
            assert(chunks.size() >= 1);
            doc_serializer.writeUnchanged(chunks[0]);
            if (hasBody) {
                assert(chunks.size() == 2);
                doc_serializer.writeUnchanged(chunks[1]);
            }
        } else if (hasBody) {
            assert(chunks.size() == 1);
            doc_serializer.writeUnchanged(chunks[0]);
        }
    } else {
        if (hasHeader) {
            doc_serializer.write(value.getFields(), HeaderFields());
        }
        if (hasBody) {
            doc_serializer.write(value.getFields(), BodyFields());
        }
    }

    const uint16_t version = serialize_version;
    _stream << version
            << static_cast<uint32_t>(doc_stream.size());
    _stream.write(doc_stream.peek(), doc_stream.size());
}

void VespaDocumentSerializer::visit(const StructFieldValue &value)
{
    if (!structNeedsReserialization(value)) {
        const StructFieldValue::Chunks & chunks = value.getChunks();
        assert(chunks.size() == 1);
        writeUnchanged(chunks[0]);
    } else {
        write(value, AllFields());
    }
}

void VespaDocumentSerializer::write(const AnnotationReferenceFieldValue &value)
{
    putInt1_2_4Bytes(_stream, value.getAnnotationIndex());
}

void VespaDocumentSerializer::write(const ArrayFieldValue &value) {
    putInt1_2_4Bytes(_stream, value.size());
    for (size_t i(0), m(value.size()); i < m; ++i) {
        value[i].accept(*this);
    }
}

void VespaDocumentSerializer::write(const MapFieldValue &value) {
    putInt1_2_4Bytes(_stream, value.size());
    for (const auto & entry : value) {
        (*entry.first).accept(*this);
        (*entry.second).accept(*this);
    }
}

void VespaDocumentSerializer::write(const ByteFieldValue &value) {
    _stream << value.getValue();
}

void VespaDocumentSerializer::write(const DoubleFieldValue &value) {
    _stream << value.getValue();
}

void VespaDocumentSerializer::write(const FloatFieldValue &value) {
    _stream << value.getValue();
}

void VespaDocumentSerializer::write(const IntFieldValue &value) {
    _stream << static_cast<uint32_t>(value.getValue());
}

void VespaDocumentSerializer::write(const LongFieldValue &value) {
    _stream << static_cast<uint64_t>(value.getValue());
}

void VespaDocumentSerializer::write(const PredicateFieldValue &value) {
    SlimeOutputToVector output;
    vespalib::slime::BinaryFormat::encode(value.getSlime(), output);
    _stream << static_cast<uint32_t>(output.size());
    _stream.write(output.data(), output.size());
}

void VespaDocumentSerializer::write(const RawFieldValue &value) {
    _stream << static_cast<uint32_t>(value.getValueRef().size());
    _stream.write(value.getValueRef().data(), value.getValueRef().size());
}

void VespaDocumentSerializer::write(const ShortFieldValue &value) {
    _stream << static_cast<uint16_t>(value.getValue());
}

namespace {
template <typename Map>
void writeAnnotations(AnnotationSerializer &serializer, const Map &m) {
    for (const auto & annotation : m) {
        serializer.write(*annotation.second);
    }
}
}  // namespace

void VespaDocumentSerializer::write(const StringFieldValue &value) {
    uint8_t coding = (value.hasSpanTrees() << 6);
    _stream << coding;
    putInt1_4Bytes(_stream, value.getValueRef().size() + 1);
    _stream.write(value.getValueRef().data(), value.getValueRef().size());
    _stream << static_cast<uint8_t>(0);  // add null-termination.
    if (value.hasSpanTrees()) {
        vespalib::ConstBufferRef buffer = value.getSerializedAnnotations();
        _stream << static_cast<uint32_t>(buffer.size());
        _stream.write(buffer.data(), buffer.size());
    }
}

namespace {
void serializeFields(const StructFieldValue &value, nbostream &stream,
                     vector<pair<uint32_t, uint32_t> > &field_info, const FieldSet& fieldSet)
{
    VespaDocumentSerializer serializer(stream);
    for (StructuredFieldValue::const_iterator it(value.begin()), e(value.end()); it != e; ++it) {
        if (!fieldSet.contains(it.field())) {
            continue;
        }
        size_t original_size = stream.size();
        int id = it.field().getId();
        if (!value.serializeField(id, VespaDocumentSerializer::getCurrentVersion(), serializer)) {
            continue;
        }
        size_t field_size = stream.size() - original_size;
        field_info.push_back(make_pair(it.field().getId(), field_size));
    }
}

bool compressionSufficient(const CompressionConfig &config, uint64_t old_size, size_t new_size)
{
    return (new_size + 8) < (old_size * config.threshold / 100);
}

bool bigEnough(size_t size, const CompressionConfig &config)
{
    return (size >= config.minSize);
}

vespalib::ConstBufferRef
compressStream(const CompressionConfig &config, nbostream &stream, vespalib::DataBuffer & compressed_data)
{
    using vespalib::compression::compress;
    vespalib::ConstBufferRef buf(stream.c_str(), stream.size());
    if (config.useCompression() && bigEnough(stream.size(), config)) {
        CompressionConfig::Type compressedType = compress(config,
                                                          vespalib::ConstBufferRef(stream.c_str(), stream.size()),
                                                          compressed_data, false);
        if (compressedType != config.type ||
            ! compressionSufficient(config, stream.size(), compressed_data.getDataLen()))
        {
            compressed_data.clear();
        } else {
            buf = vespalib::ConstBufferRef(compressed_data.getData(), compressed_data.getDataLen());
        }
    }
    return buf;
}

void putFieldInfo(nbostream &output, const vector<pair<uint32_t, uint32_t> > &field_info) {
    putInt1_4Bytes(output, field_info.size());
    for (size_t i = 0; i < field_info.size(); ++i) {
        putInt1_4Bytes(output, field_info[i].first);
        putInt2_4_8Bytes(output, field_info[i].second);
    }
}
}  // namespace

/**
 * Reserialize if value has been modified since deserialization
 * or we are bumping version
 * or compression type has changed AND config says compress.
 * The last and is to make sure that we do not decompress a document
 * unintentionally.
 */
bool VespaDocumentSerializer::structNeedsReserialization(
        const StructFieldValue &value)
{
    if (value.hasChanged()) {
        return true;
    }

    if (value.getVersion() != serialize_version) {
        return true;
    }

    if (value.getCompressionConfig().type == CompressionConfig::NONE) {
        return false;
    }

    const StructFieldValue::Chunks & chunks = value.getChunks();

    for (uint32_t i = 0; i < chunks.size(); ++i) {
        if (chunks[i].getCompression() != value.getCompressionConfig().type &&
            chunks[i].getCompression() != CompressionConfig::UNCOMPRESSABLE)
        {
            return true;
        }
    }

    return false;
}

void VespaDocumentSerializer::writeUnchanged(const SerializableArray &value) {
    vector<pair<uint32_t, uint32_t> > field_info;
    const std::vector<SerializableArray::Entry>& entries = value.getEntries();

    field_info.reserve(entries.size());
    for(const auto & entry : entries) {
        field_info.emplace_back(entry.id(), entry.size());
    }

    const ByteBuffer* buffer = value.getSerializedBuffer();
    uint32_t sz = (buffer != NULL) ? buffer->getLength() : 0;
    size_t estimatedRequiredSpace = sz + 4 + 1 + 8 + 4 + field_info.size()*12;
    _stream.reserve(_stream.size() + estimatedRequiredSpace);
    _stream << sz;
    _stream << static_cast<uint8_t>(value.getCompression());
    if (CompressionConfig::isCompressed(value.getCompression())) {
        putInt2_4_8Bytes(_stream, value.getCompressionInfo().getUncompressedSize());
    }
    putFieldInfo(_stream, field_info);
    if (sz) {
        _stream.write(buffer->getBuffer(), buffer->getLength());
    }
}

void VespaDocumentSerializer::write(const StructFieldValue &value,
                                    const FieldSet& fieldSet)
{
    nbostream value_stream;
    vector<pair<uint32_t, uint32_t> > field_info;
    serializeFields(value, value_stream, field_info, fieldSet);

    const CompressionConfig &comp_config = value.getCompressionConfig();
    vespalib::DataBuffer compressed_data;
    vespalib::ConstBufferRef toSerialize = compressStream(comp_config, value_stream, compressed_data);

    uint8_t comp_type = (compressed_data.getDataLen() == 0)
                        ? (comp_config.type == CompressionConfig::NONE
                           ? CompressionConfig::NONE
                           : CompressionConfig::UNCOMPRESSABLE)
                        : comp_config.type;
    _stream << static_cast<uint32_t>(toSerialize.size());
    _stream << comp_type;
    if (compressed_data.getDataLen() != 0) {
        putInt2_4_8Bytes(_stream, value_stream.size());
    }
    putFieldInfo(_stream, field_info);
    _stream.write(toSerialize.c_str(), toSerialize.size());
}

void VespaDocumentSerializer::write(const WeightedSetFieldValue &value) {
    const WeightedSetDataType *type = static_cast<const WeightedSetDataType *>(value.getDataType());
    _stream << static_cast<uint32_t>(type->getNestedType().getId());
    _stream << static_cast<uint32_t>(value.size());
    for (const auto & entry : value) {
        nbostream stream;
        VespaDocumentSerializer serializer(stream);
        serializer.write(*entry.first);
        serializer.write(*entry.second);
        _stream << static_cast<uint32_t>(stream.size());  // This is unused
        _stream.write(stream.peek(), stream.size());
    }
}


void
VespaDocumentSerializer::write(const TensorFieldValue &value) {
    vespalib::nbostream tmpStream;
    auto &tensor = value.getAsTensorPtr();
    if (tensor) {
        vespalib::tensor::TypedBinaryFormat::serialize(tmpStream, *tensor);
        assert(tmpStream.size() != 0);
        _stream.putInt1_4Bytes(tmpStream.size());
        _stream.write(tmpStream.peek(), tmpStream.size());
    } else {
        _stream.putInt1_4Bytes(0);
    }
}

void VespaDocumentSerializer::write(const ReferenceFieldValue& value) {
    _stream << static_cast<uint8_t>(value.hasValidDocumentId() ? 1 : 0);
    if (value.hasValidDocumentId()) {
       write(value.getDocumentId());
    }
}

namespace {
    const uint8_t CONTENT_HASTYPE(0x01);
    const uint8_t CONTENT_HASVALUE(0x01);
}

void VespaDocumentSerializer::write42(const DocumentUpdate &value)
{
    _stream << static_cast<uint16_t>(value.getVersion());
    write(value.getId());
    _stream << static_cast<uint8_t>(CONTENT_HASTYPE);
    _stream.write(value.getType().getName().c_str(), value.getType().getName().size() + 1);
    _stream << static_cast<uint16_t>(0);
    const DocumentUpdate::FieldUpdateV & updates(value.getUpdates());
    _stream << static_cast<uint32_t>(value.serializeFlags(updates.size()));
    for (const auto & update : updates) {
        write(update);
    }
}

void VespaDocumentSerializer::writeHEAD(const DocumentUpdate &value)
{
    write(value.getId());
    _stream.write(value.getType().getName().c_str(), value.getType().getName().size() + 1);
    _stream << static_cast<uint16_t>(0);
    const DocumentUpdate::FieldUpdateV & updates(value.getUpdates());
    _stream << static_cast<uint32_t>(updates.size());
    for (const auto & update : updates) {
        write(update);
    }
    const DocumentUpdate::FieldPathUpdateV & fieldPathUpdates(value.getFieldPathUpdates());
    _stream << static_cast<uint32_t>(value.serializeFlags(fieldPathUpdates.size()));
    for (const auto & update : fieldPathUpdates) {
        _stream << update->getSerializedType();
        write(*update);
    }
}

void VespaDocumentSerializer::write(const FieldUpdate &value)
{
    _stream << static_cast<int32_t>(value.getField().getId());
    _stream << static_cast<int32_t>(value.size());
    for (size_t i(0), m(value.size()); i < m; i++) {
        write(value[i]);
    }
}

void VespaDocumentSerializer::write(const RemoveValueUpdate &value)
{
    _stream << RemoveValueUpdate::classId;
    write(value.getKey());
}


void VespaDocumentSerializer::write(const AddValueUpdate &value)
{
    _stream << AddValueUpdate::classId;
    write(value.getValue());
    _stream << static_cast<int32_t>(value.getWeight());
}

void VespaDocumentSerializer::write(const ArithmeticValueUpdate &value)
{
    _stream << ArithmeticValueUpdate::classId;
    _stream << static_cast<uint32_t>(value.getOperator());
    _stream << static_cast<double>(value.getOperand());
}

void VespaDocumentSerializer::write(const AssignValueUpdate &value)
{
    _stream << AssignValueUpdate::classId;
    if (value.hasValue()) {
        _stream << static_cast<uint8_t>(CONTENT_HASVALUE);
        write(value.getValue());
    } else {
        _stream << static_cast<uint8_t>(0);
    }
}

void VespaDocumentSerializer::write(const ClearValueUpdate &value)
{
    (void) value;
    _stream << ClearValueUpdate::classId;
}

void VespaDocumentSerializer::write(const MapValueUpdate &value)
{
    _stream << MapValueUpdate::classId;
    write(value.getKey());
    write(value.getUpdate());
}

namespace {

void writeStringWithZeroTermination(nbostream & os, stringref s)
{
    uint32_t sz(s.size() + 1);
    os << sz;
    os.write(s.c_str(), sz);
}

void writeFieldPath(nbostream & os, const FieldPathUpdate & value)
{
    writeStringWithZeroTermination(os, value.getOriginalFieldPath());
    writeStringWithZeroTermination(os, value.getOriginalWhereClause());
}

}

void VespaDocumentSerializer::write(const AddFieldPathUpdate &value)
{
    writeFieldPath(_stream, value);
    write(value.getValues());
}

void VespaDocumentSerializer::write(const AssignFieldPathUpdate &value)
{
    writeFieldPath(_stream, value);
    uint8_t flags = 0;
    flags |= value.getRemoveIfZero() ? AssignFieldPathUpdate::REMOVE_IF_ZERO : 0;
    flags |= value.getCreateMissingPath() ? AssignFieldPathUpdate::CREATE_MISSING_PATH : 0;
    flags |= (! value.hasValue()) ?  AssignFieldPathUpdate::ARITHMETIC_EXPRESSION : 0;
    _stream << flags;
    if (value.hasValue()) {
        write(value.getValue());
    } else {
        writeStringWithZeroTermination(_stream, value.getExpression());
    }

}

void VespaDocumentSerializer::write(const RemoveFieldPathUpdate &value)
{
    writeFieldPath(_stream, value);
}

}  // namespace document
