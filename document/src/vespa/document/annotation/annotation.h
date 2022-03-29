// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/fieldvalue/fieldvalue.h>

namespace document {
struct SpanNode;

class Annotation {
    const AnnotationType * _type;
    const SpanNode *_node;
    std::unique_ptr<FieldValue> _value;

public:
    template <typename T>
    Annotation(const AnnotationType & type, std::unique_ptr<T> value)
        : _type(&type), _node(nullptr), _value(std::move(value)) {}

    Annotation(const AnnotationType &annotation) : _type(&annotation), _node(nullptr), _value(nullptr) { }
    Annotation() noexcept : _type(nullptr), _node(nullptr), _value(nullptr) { }
    Annotation(const Annotation &) = delete;
    Annotation & operator = (const Annotation &) = delete;
    Annotation(Annotation &&) = default;
    Annotation & operator = (Annotation &&) = delete;
    ~Annotation();

    void setType(const AnnotationType * v) { _type = v; }
    void setSpanNode(const SpanNode &node) { _node = &node; }
    template <typename T>
    void setFieldValue(std::unique_ptr<T> value) { _value = std::move(value); }
    bool operator==(const Annotation &a2) const;

    const SpanNode *getSpanNode() const { return _node; }
    const AnnotationType &getType() const { return *_type; }
    bool valid() const { return _type != nullptr; }
    int32_t getTypeId() const { return _type->getId(); }
    const FieldValue *getFieldValue() const { return _value.get(); }
    vespalib::string toString() const;
};

std::ostream & operator << (std::ostream & os, const Annotation & span);

}  // namespace document

