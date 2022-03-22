// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringfieldvalue.h"
#include "literalfieldvalue.hpp"

#include <vespa/document/annotation/spantree.h>
#include <vespa/document/serialization/annotationserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/document/serialization/util.h>
#include <vespa/document/serialization/annotationdeserializer.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <ostream>

using vespalib::nbostream;
using vespalib::ConstBufferRef;
using vespalib::stringref;

namespace document {

StringFieldValue::StringFieldValue(const StringFieldValue & rhs)
    : Parent(rhs),
      _annotationData(rhs.copyAnnotationData())
{
}

StringFieldValue::~StringFieldValue() = default;

StringFieldValue &
StringFieldValue::operator=(const StringFieldValue & rhs)
{
    if (&rhs != this) {
        Parent::operator=(rhs);
        _annotationData = rhs.copyAnnotationData();
    }
    return *this;
}

int
StringFieldValue::compare(const FieldValue& other) const {
    if (other.isA(Type::STRING)) {
        const StringFieldValue &other_s(static_cast<const StringFieldValue &>(other));
        return _value.compare(other_s._value);
    } else {
        return Parent::compare(other);
    }
}

void
StringFieldValue::print(std::ostream& out, bool verbose, const std::string& indent) const {
    if ( ! hasSpanTrees()) {
        Parent::print(out, verbose, indent);
    } else {
        out << "StringFieldValue(\"";
        Parent::print(out, verbose, indent);
        ConstBufferRef buf = getSerializedAnnotations();
        out << "\"\n" << indent << " " << vespalib::HexDump(buf.data(), buf.size());
        out << ")";
    }
}

void StringFieldValue::setSpanTrees(ConstBufferRef serialized, const FixedTypeRepo & repo, uint8_t version, bool isSerializedDataLongLived)
{
    _annotationData = std::make_unique<AnnotationData>(serialized, repo, version, isSerializedDataLongLived);
}


void StringFieldValue::setSpanTrees(const SpanTrees & trees, const FixedTypeRepo & repo)
{
    nbostream os;
    putInt1_2_4Bytes(os, trees.size());
    AnnotationSerializer serializer(os);
    for (const auto & tree : trees) {
        serializer.write(*tree);
    }
    setSpanTrees(ConstBufferRef(os.peek(), os.size()), repo, VespaDocumentSerializer::getCurrentVersion(), false);
}
StringFieldValue::SpanTrees StringFieldValue::getSpanTrees() const {
    SpanTrees trees;
    if (hasSpanTrees()) {
        trees = _annotationData->getSpanTrees();
    }
    return trees;
}

void
StringFieldValue::doClearSpanTrees() {
    _annotationData.reset();
}

const SpanTree *
StringFieldValue::findTree(const SpanTrees & trees, stringref name)
{
    for(const auto & tree : trees) {
        if (tree->getName() == name) {
            return tree.get();
        }
    }
    return nullptr;
}

StringFieldValue &
StringFieldValue::operator=(stringref value)
{
    setValue(value);
    _annotationData.reset();
    return *this;
}

FieldValue &
StringFieldValue::assign(const FieldValue & rhs)
{
    if (rhs.isA(Type::STRING)) {
        *this = static_cast<const StringFieldValue &>(rhs);
    } else {
        *this = rhs.getAsString().operator stringref();
    }
    return *this;
}

StringFieldValue::AnnotationData::UP
StringFieldValue::copyAnnotationData() const {
    return hasSpanTrees()
           ? std::make_unique<AnnotationData>(*_annotationData)
           : AnnotationData::UP();
}

StringFieldValue::AnnotationData::AnnotationData(vespalib::ConstBufferRef serialized, const FixedTypeRepo &repo,
                                                 uint8_t version, bool isSerializedDataLongLived)
        : _serialized(serialized),
          _repo(repo.getDocumentTypeRepo()),
          _docType(repo.getDocumentType()),
          _version(version)
{
    if ( ! isSerializedDataLongLived) {
        _backingBlob.assign(serialized.c_str(), serialized.c_str() + serialized.size());
        _serialized = ConstBufferRef(&_backingBlob[0], _backingBlob.size());
    }
}

StringFieldValue::SpanTrees StringFieldValue::AnnotationData::getSpanTrees() const
{
    SpanTrees trees;
    if (hasSpanTrees()) {
        nbostream is(_serialized.data(), _serialized.size());
        size_t tree_count = getInt1_2_4Bytes(is);
        FixedTypeRepo repo(_repo, _docType);
        AnnotationDeserializer deserializer(repo, is, _version);
        for (size_t i = 0; i < tree_count; ++i) {
            trees.emplace_back(deserializer.readSpanTree());
        }
    }
    return trees;
}

StringFieldValue::AnnotationData::AnnotationData(const StringFieldValue::AnnotationData & rhs) :
        AnnotationData(rhs._serialized, FixedTypeRepo(rhs._repo, rhs._docType), rhs._version, false)
{
}

} // document
