// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationserializer.h"
#include "util.h"
#include "vespadocumentserializer.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spannode.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::nbostream;

namespace document {

AnnotationSerializer::AnnotationSerializer(nbostream &stream)
    : _stream(stream),
      _span_node_map() {
}

void AnnotationSerializer::write(const SpanTree &tree) {
    _span_node_map.clear();
    StringFieldValue name(tree.getName());
    VespaDocumentSerializer serializer(_stream);
    serializer.write(name);
    write(tree.getRoot());
    putInt1_2_4Bytes(_stream, tree.numAnnotations());
    for (const Annotation & a : tree) {
        write(a);
    }
}

void AnnotationSerializer::write(const SpanNode &node) {
    size_t node_id = _span_node_map.size();
    _span_node_map[&node] = node_id;
    node.accept(*this);
}

void AnnotationSerializer::writeSpan(const Span &node) {
    _stream << static_cast<uint8_t>(1);  // Span.ID
    putInt1_2_4Bytes(_stream, node.from());
    putInt1_2_4Bytes(_stream, node.length());
}

namespace {
void writeSpanList(const SpanList &list, nbostream &stream,
                   AnnotationSerializer &serializer) {
    putInt1_2_4Bytes(stream, list.size());
    for (const SpanNode * node : list) {
        serializer.write(*node);
    }
}
void writeSpanList(const SimpleSpanList &list, nbostream &stream,
                   AnnotationSerializer &serializer) {
    putInt1_2_4Bytes(stream, list.size());
    for (const SpanNode & node : list) {
        serializer.write(node);
    }
}
}  // namespace

void AnnotationSerializer::writeList(const SpanList &list) {
    _stream << static_cast<uint8_t>(2);  // SpanList.ID
    writeSpanList(list, _stream, *this);
}

void AnnotationSerializer::writeList(const SimpleSpanList &list) {
    _stream << static_cast<uint8_t>(2);  // SpanList.ID
    writeSpanList(list, _stream, *this);
}

void AnnotationSerializer::writeList(const AlternateSpanList &list) {
    _stream << static_cast<uint8_t>(4);  // AlternateSpanList.ID
    putInt1_2_4Bytes(_stream, list.getNumSubtrees());
    for (size_t i = 0; i < list.getNumSubtrees(); ++i) {
        _stream << list.getProbability(i);
        writeSpanList(list.getSubtree(i), _stream, *this);
    }
}

namespace {
uint8_t getAnnotationFeatures(const Annotation &annotation) {
    uint8_t features = annotation.getSpanNode() ? 1 : 0;
    features |= annotation.getFieldValue() ? 2 : 0;
    return features;
}
}  // namespace

void AnnotationSerializer::write(const Annotation &annotation) {
    _stream << annotation.getTypeId();
    _stream << getAnnotationFeatures(annotation);

    nbostream tmp_stream;
    if (annotation.getSpanNode()) {
        size_t node_index = _span_node_map[annotation.getSpanNode()];
        putInt1_2_4Bytes(tmp_stream, node_index);
    }
    if (annotation.getFieldValue()) {
        uint32_t type_id = annotation.getFieldValue()->getDataType()->getId();
        tmp_stream << type_id;
        VespaDocumentSerializer serializer(tmp_stream);
        serializer.write(*annotation.getFieldValue());
    }

    putInt1_2_4BytesAs4(_stream, tmp_stream.size());
    _stream.write(tmp_stream.peek(), tmp_stream.size());
}

}  // namespace document
