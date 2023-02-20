// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationdeserializer.h"
#include "vespadocumentdeserializer.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP(".annotationdeserializer");

using std::unique_ptr;

namespace document {
namespace {
[[noreturn]] void fail(const char *message) {
    throw DeserializeException(message);
}
}

AnnotationDeserializer::AnnotationDeserializer(const FixedTypeRepo &repo,
                                               vespalib::nbostream &stream,
                                               uint16_t version)
    : _repo(repo),
      _stream(stream),
      _version(version),
      _nodes() {
}

unique_ptr<SpanTree> AnnotationDeserializer::readSpanTree() {
    VespaDocumentDeserializer deserializer(_repo, _stream, _version);

    StringFieldValue tree_name;
    deserializer.read(tree_name);
    _nodes.clear();
    SpanNode::UP root = readSpanNode();
    unique_ptr<SpanTree> span_tree(new SpanTree(tree_name.getValue(), std::move(root)));

    uint32_t annotation_count = getInt1_2_4Bytes(_stream);
    span_tree->reserveAnnotations(annotation_count);
    for (uint32_t i = 0; i < annotation_count; ++i) {
        readAnnotation(span_tree->annotation(i));
    }

    return span_tree;
}

unique_ptr<SpanNode> AnnotationDeserializer::readSpanNode() {
    uint8_t type = readValue<uint8_t>(_stream);
    unique_ptr<SpanNode> node;
    size_t node_index = _nodes.size();
    _nodes.push_back(0);
    if (type == 1) {  // Span.ID
        Span * span = new Span();
        node.reset(span);
        readSpan(*span);
    } else if (type == 2) {  // SpanList.ID
        node = readSimpleSpanList();
        if (node.get() == nullptr) {
            node = readSpanList();
        }
    } else if (type == 4) {  // AlternateSpanList.ID
        node = readAlternateSpanList();
    } else {
        LOG(warning, "Cannot read SpanNode of type %u.", type);
        fail("Annotation data contains SpanNode with bad type");
    }
    _nodes[node_index] = node.get();
    return node;
}

unique_ptr<SpanList> AnnotationDeserializer::readSpanList() {
    uint32_t child_count = getInt1_2_4Bytes(_stream);
    unique_ptr<SpanList> span_list(new SpanList);
    span_list->reserve(child_count);
    _nodes.reserve(vespalib::roundUp2inN(_nodes.size() + child_count));
    for (uint32_t i = 0; i < child_count; ++i) {
        span_list->add(readSpanNode());
    }
    return span_list;
}

unique_ptr<SimpleSpanList> AnnotationDeserializer::readSimpleSpanList() {
    size_t pos = _stream.rp();
    uint32_t child_count = getInt1_2_4Bytes(_stream);
    unique_ptr<SimpleSpanList> span_list(new SimpleSpanList(child_count));
    _nodes.reserve(vespalib::roundUp2inN(_nodes.size() + child_count));
    for (uint32_t i = 0; i < child_count; ++i) {
        uint8_t type = readValue<uint8_t>(_stream);
        if (type != 1) {
            _stream.rp(pos);
            return unique_ptr<SimpleSpanList>();
        }
        readSpan((*span_list)[i]);
    }
    for (uint32_t i = 0; i < child_count; ++i) {
        _nodes.push_back(&(*span_list)[i]);
    }
    return span_list;
}

void AnnotationDeserializer::readAnnotation(Annotation & annotation) {
    uint32_t type_id = readValue<uint32_t>(_stream);
    uint8_t features = readValue<uint8_t>(_stream);
    uint32_t size = getInt1_2_4Bytes(_stream);
    if (size > _stream.size()) {
        LOG(warning, "Annotation of type %u claims size %u > available %zd", type_id, size, _stream.size());
        fail("Annotation contains SpanNode with bad size");
        return;
    }

    const AnnotationType *type = _repo.getAnnotationType(type_id);
    if (!type) {
        LOG(warning, "Skipping unknown annotation of type %u", type_id);
        _stream.adjustReadPos(size);
        return;
    }
    annotation.setType(type);

    SpanNode *span_node = 0;
    if (features & 1) {  // has span node
        uint32_t span_node_id = getInt1_2_4Bytes(_stream);
        if (span_node_id > _nodes.size()) {
            LOG(warning, "Annotation of type %u has node_id %u > #nodes %zd", type_id, span_node_id, _nodes.size());
            fail("Annotation refers to out-of-bounds span node");
        } else {
            span_node = _nodes[span_node_id];
        }
    }
    if (features & 2) {  // has value
        uint32_t data_type_id = readValue<uint32_t>(_stream);

        const DataType *data_type = type->getDataType();
        if (!data_type) {
            LOG(warning, "Bad data type %d for annotation type %s",
                data_type_id, type->getName().c_str());
            fail("Annotation with bad datatype for its value");
        } else {
            FieldValue::UP value(data_type->createFieldValue());
            VespaDocumentDeserializer deserializer(_repo, _stream, _version);
            deserializer.read(*value);
            annotation.setFieldValue(std::move(value));
        }
    }
    if (span_node) {
        annotation.setSpanNode(*span_node);
    }
}

unique_ptr<AlternateSpanList> AnnotationDeserializer::readAlternateSpanList() {
    unique_ptr<AlternateSpanList> span_list(new AlternateSpanList);
    uint32_t tree_count = getInt1_2_4Bytes(_stream);
    for (uint32_t i = 0; i < tree_count; ++i) {
        span_list->setProbability(i, readValue<double>(_stream));
        span_list->setSubtree(i, readSpanList());
    }
    return span_list;
}

}  // namespace document
