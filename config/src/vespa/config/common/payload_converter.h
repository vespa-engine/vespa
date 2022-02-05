// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include <vespa/vespalib/data/slime/object_traverser.h>
#include <vespa/vespalib/data/slime/array_traverser.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

/**
 * Converts slime payload to cfg format.
 * XXX: Maps are not supported by this converter.
 */
class PayloadConverter : public vespalib::slime::ObjectTraverser, public vespalib::slime::ArrayTraverser {
public:
    PayloadConverter(const vespalib::slime::Inspector & inspector);
    ~PayloadConverter();
    const StringVector & convert();
    void field(const vespalib::Memory & symbol, const vespalib::slime::Inspector & inspector) override;
    void entry(size_t idx, const vespalib::slime::Inspector & inspector) override;
private:
    void printPrefix();
    void encode(const vespalib::slime::Inspector & inspector);
    void encode(const vespalib::Memory & symbol, const vespalib::slime::Inspector & inspector);
    void encodeObject(const vespalib::Memory & symbol, const vespalib::slime::Inspector & object);
    void encodeArray(const vespalib::Memory & symbol, const vespalib::slime::Inspector & object);
    void encodeValue(const vespalib::slime::Inspector & value);
    void encodeString(const vespalib::string & value);
    void encodeQuotedString(const vespalib::string & value);
    void encodeLong(long value);
    void encodeDouble(double value);
    void encodeBool(bool value);
    struct Node {
        vespalib::string name;
        int arrayIndex;
        Node(const vespalib::string & nm, int idx) : name(nm), arrayIndex(idx) {}
        Node(int idx) : name(""), arrayIndex(idx) {}
        Node(const vespalib::string & nm) : name(nm), arrayIndex(-1) {}
    };
    using NodeStack = std::vector<Node>;
    const vespalib::slime::Inspector & _inspector;
    StringVector                       _lines;
    NodeStack                          _nodeStack;
    vespalib::asciistream              _buf;
};

} // namespace config

