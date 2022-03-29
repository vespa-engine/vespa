// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationserializer.h"
#include "slime_output_to_vector.h"
#include "util.h"
#include "vespadocumentserializer.h"
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/update/updates.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
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

void
VespaDocumentSerializer::writeFieldValue(const FieldValue &value) {
    write(value);
}

void
VespaDocumentSerializer::writeSerializedData(const void *buf, size_t length) {
    _stream.write(buf, length);
}

void
VespaDocumentSerializer::write(const ValueUpdate &value) {
    value.accept(*this);
}

void
VespaDocumentSerializer::write(const FieldPathUpdate &value) {
    value.accept(*this);
}

void
VespaDocumentSerializer::write(const FieldValue &value) {
    value.accept(*this);
}

void
VespaDocumentSerializer::write(const DocumentId &value) {
    string id_string = value.getScheme().toString();
    _stream.write(id_string.data(), id_string.size());
    _stream << static_cast<uint8_t>(0);
}

void VespaDocumentSerializer::write(const DocumentType &value) {
    _stream.write(value.getName().data(), value.getName().size());

    _stream << static_cast<uint8_t>(0)
            << static_cast<uint16_t>(0);  // version
}

namespace {

uint8_t
getContentCode(bool hasContent)
{
    return 0x01u |  // Document type is always present
           (hasContent ? 0x02u : 0x00u);   // Payload ?
}

}

void
VespaDocumentSerializer::write(const Document &value) {
    nbostream doc_stream;
    VespaDocumentSerializer doc_serializer(doc_stream);
    doc_serializer.write(value.getId());

    bool hasContent = ! value.getFields().empty();
    doc_stream << getContentCode(hasContent);
    doc_serializer.write(value.getType());

    if ( hasContent ) {
        if (!structNeedsReserialization(value.getFields())) {
            doc_serializer.writeUnchanged(value.getFields().getFields());
        } else {
            doc_serializer.write(value.getFields(), AllFields());
        }
    }

    const uint16_t version = serialize_version;
    _stream << version << static_cast<uint32_t>(doc_stream.size());
    _stream.write(doc_stream.peek(), doc_stream.size());
}

void
VespaDocumentSerializer::visit(const StructFieldValue &value)
{
    if (!structNeedsReserialization(value)) {
        writeUnchanged(value.getFields());
    } else {
        write(value, AllFields());
    }
}

void
VespaDocumentSerializer::write(const AnnotationReferenceFieldValue &value)
{
    putInt1_2_4Bytes(_stream, value.getAnnotationIndex());
}

void VespaDocumentSerializer::write(const ArrayFieldValue &value) {
    putInt1_2_4Bytes(_stream, value.size());
    for (const auto & item : value) {
       item.accept(*this);
    }
}

void
VespaDocumentSerializer::write(const MapFieldValue &value) {
    putInt1_2_4Bytes(_stream, value.size());
    for (const auto & entry : value) {
        (*entry.first).accept(*this);
        (*entry.second).accept(*this);
    }
}

void
VespaDocumentSerializer::write(const BoolFieldValue &value) {
    _stream << value.getValue();
}

void
VespaDocumentSerializer::write(const ByteFieldValue &value) {
    _stream << value.getValue();
}

void
VespaDocumentSerializer::write(const DoubleFieldValue &value) {
    _stream << value.getValue();
}

void
VespaDocumentSerializer::write(const FloatFieldValue &value) {
    _stream << value.getValue();
}

void
VespaDocumentSerializer::write(const IntFieldValue &value) {
    _stream << static_cast<uint32_t>(value.getValue());
}

void
VespaDocumentSerializer::write(const LongFieldValue &value) {
    _stream << static_cast<uint64_t>(value.getValue());
}

void
VespaDocumentSerializer::write(const PredicateFieldValue &value) {
    SlimeOutputToVector output;
    vespalib::slime::BinaryFormat::encode(value.getSlime(), output);
    _stream << static_cast<uint32_t>(output.size());
    _stream.write(output.data(), output.size());
}

void
VespaDocumentSerializer::write(const RawFieldValue &value) {
    _stream << static_cast<uint32_t>(value.getValueRef().size());
    _stream.write(value.getValueRef().data(), value.getValueRef().size());
}

void
VespaDocumentSerializer::write(const ShortFieldValue &value) {
    _stream << static_cast<uint16_t>(value.getValue());
}

void
VespaDocumentSerializer::write(const StringFieldValue &value) {
    uint8_t coding = (value.hasSpanTrees() << 6u);
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
void
serializeFields(const StructFieldValue &value, nbostream &stream,
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
        field_info.emplace_back(it.field().getId(), field_size);
    }
}

void
putFieldInfo(nbostream &output, const vector<pair<uint32_t, uint32_t> > &field_info) {
    putInt1_4Bytes(output, field_info.size());
    for (const auto & field : field_info) {
        putInt1_4Bytes(output, field.first);
        putInt2_4_8Bytes(output, field.second);
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
bool
VespaDocumentSerializer::structNeedsReserialization(const StructFieldValue &value)
{
    if (value.hasChanged()) {
        return true;
    }

    if (value.getVersion() != serialize_version) {
        return true;
    }

    return false;
}

void
VespaDocumentSerializer::writeUnchanged(const SerializableArray &value) {
    vector<pair<uint32_t, uint32_t> > field_info;
    const std::vector<SerializableArray::Entry>& entries = value.getEntries();

    field_info.reserve(entries.size());
    for(const auto & entry : entries) {
        field_info.emplace_back(entry.id(), entry.size());
    }

    const ByteBuffer* buffer = value.getSerializedBuffer();
    uint32_t sz = (buffer != nullptr) ? buffer->getLength() : 0;
    size_t estimatedRequiredSpace = sz + 4 + 1 + 8 + 4 + field_info.size()*12;
    _stream.reserve(_stream.size() + estimatedRequiredSpace);
    _stream << sz;
    _stream << static_cast<uint8_t>(CompressionConfig::NONE);
    putFieldInfo(_stream, field_info);
    if (sz) {
        _stream.write(buffer->getBuffer(), buffer->getLength());
    }
}

void
VespaDocumentSerializer::write(const StructFieldValue &value, const FieldSet& fieldSet)
{
    nbostream value_stream;
    vector<pair<uint32_t, uint32_t> > field_info;
    serializeFields(value, value_stream, field_info, fieldSet);

    _stream << static_cast<uint32_t>(value_stream.size());
    _stream << static_cast<uint8_t>(CompressionConfig::NONE);
    putFieldInfo(_stream, field_info);
    _stream.write(value_stream.data(), value_stream.size());
}

void
VespaDocumentSerializer::write(const WeightedSetFieldValue &value) {
    auto type = static_cast<const WeightedSetDataType *>(value.getDataType());
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
    auto tensor = value.getAsTensorPtr();
    if (tensor) {
        encode_value(*tensor, tmpStream);
        assert( ! tmpStream.empty());
        _stream.putInt1_4Bytes(tmpStream.size());
        _stream.write(tmpStream.peek(), tmpStream.size());
    } else {
        _stream.putInt1_4Bytes(0);
    }
}

void
VespaDocumentSerializer::write(const ReferenceFieldValue& value) {
    _stream << static_cast<uint8_t>(value.hasValidDocumentId() ? 1 : 0);
    if (value.hasValidDocumentId()) {
       write(value.getDocumentId());
    }
}

namespace {
    const uint8_t CONTENT_HASVALUE(0x01);
}

void
VespaDocumentSerializer::writeHEAD(const DocumentUpdate &value)
{
    if (!value._needHardReserialize) {
        _stream.write(value._backing.peek(), value._backing.size());
        return;
    }
    write(value.getId());
    _stream.write(value.getType().getName().c_str(), value.getType().getName().size() + 1);
    _stream << static_cast<uint16_t>(0);
    _stream << static_cast<uint32_t>(value._updates.size());
    for (const auto & update : value._updates) {
        write(update);
    }
    _stream << static_cast<uint32_t>(value.serializeFlags(value._fieldPathUpdates.size()));
    for (const auto & update : value._fieldPathUpdates) {
        _stream << update->getSerializedType();
        write(*update);
    }
}

void
VespaDocumentSerializer::write(const FieldUpdate &value)
{
    _stream << static_cast<int32_t>(value.getField().getId());
    _stream << static_cast<int32_t>(value.size());
    for (size_t i(0), m(value.size()); i < m; i++) {
        write(value[i]);
    }
}

void
VespaDocumentSerializer::write(const RemoveValueUpdate &value)
{
    _stream << uint32_t(ValueUpdate::Remove);
    write(value.getKey());
}


void
VespaDocumentSerializer::write(const AddValueUpdate &value)
{
    _stream << uint32_t(ValueUpdate::Add);
    write(value.getValue());
    _stream << static_cast<int32_t>(value.getWeight());
}

void
VespaDocumentSerializer::write(const ArithmeticValueUpdate &value)
{
    _stream << uint32_t(ValueUpdate::Arithmetic);
    _stream << static_cast<uint32_t>(value.getOperator());
    _stream << static_cast<double>(value.getOperand());
}

void
VespaDocumentSerializer::write(const AssignValueUpdate &value)
{
    _stream << uint32_t(ValueUpdate::Assign);
    if (value.hasValue()) {
        _stream << static_cast<uint8_t>(CONTENT_HASVALUE);
        write(value.getValue());
    } else {
        _stream << static_cast<uint8_t>(0);
    }
}

void
VespaDocumentSerializer::write(const ClearValueUpdate &value)
{
    (void) value;
    _stream << uint32_t(ValueUpdate::Clear);
}

void VespaDocumentSerializer::write(const MapValueUpdate &value)
{
    _stream << uint32_t(ValueUpdate::Map);
    write(value.getKey());
    write(value.getUpdate());
}

namespace {

// We must ensure that string passed is always zero-terminated, so take in
// string instead of stringref. No extra allocs; function only ever called with
// string arguments.
void
writeStringWithZeroTermination(nbostream & os, const vespalib::string& s)
{
    uint32_t sz(s.size() + 1);
    os << sz;
    os.write(s.c_str(), sz);
}

void
writeFieldPath(nbostream & os, const FieldPathUpdate & value)
{
    writeStringWithZeroTermination(os, value.getOriginalFieldPath());
    writeStringWithZeroTermination(os, value.getOriginalWhereClause());
}

}

void
VespaDocumentSerializer::write(const AddFieldPathUpdate &value)
{
    writeFieldPath(_stream, value);
    write(value.getValues());
}

void
VespaDocumentSerializer::write(const AssignFieldPathUpdate &value)
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

void
VespaDocumentSerializer::write(const RemoveFieldPathUpdate &value)
{
    writeFieldPath(_stream, value);
}

void
VespaDocumentSerializer::write(const TensorModifyUpdate &value)
{
    _stream << uint32_t(ValueUpdate::TensorModify);
    _stream << static_cast<uint8_t>(value.getOperation());
    write(value.getTensor());
}

void
VespaDocumentSerializer::visit(const TensorModifyUpdate &value)
{
    write(value);
}

void
VespaDocumentSerializer::write(const TensorAddUpdate &value)
{
    _stream << uint32_t(ValueUpdate::TensorAdd);
    write(value.getTensor());
}

void
VespaDocumentSerializer::visit(const TensorAddUpdate &value)
{
    write(value);
}

void
VespaDocumentSerializer::write(const TensorRemoveUpdate &value)
{
    _stream << uint32_t(ValueUpdate::TensorRemove);
    write(value.getTensor());
}

void
VespaDocumentSerializer::visit(const TensorRemoveUpdate &value)
{
    write(value);
}

}  // namespace document
