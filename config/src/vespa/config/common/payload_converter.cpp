// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "payload_converter.h"
#include <vespa/vespalib/data/memory.h>

using namespace vespalib::slime;
using vespalib::Memory;

namespace config {

PayloadConverter::PayloadConverter(const Inspector & inspector)
    : _inspector(inspector),
      _lines()
{}

PayloadConverter::~PayloadConverter() = default;

const StringVector &
PayloadConverter::convert()
{
    _lines.clear();
    ObjectTraverser & traverser(*this);
    _inspector.traverse(traverser);
    return _lines;
}

void
PayloadConverter::encodeObject(const Memory & symbol, const Inspector & object)
{
    _nodeStack.push_back(Node(symbol.make_string()));
    ObjectTraverser & traverser(*this);
    object.traverse(traverser);
    _nodeStack.pop_back();
}

void
PayloadConverter::encodeArray(const Memory & symbol, const Inspector & array)
{
    _nodeStack.push_back(Node(symbol.make_string()));
    ArrayTraverser & traverser(*this);
    array.traverse(traverser);
    _nodeStack.pop_back();
}

void
PayloadConverter::encode(const Inspector & inspector)
{
    ObjectTraverser & traverser(*this);
    switch (inspector.type().getId()) {
        case OBJECT::ID:
            inspector.traverse(traverser);
            break;
        default:
            encodeValue(inspector);
            break;
    }
}

void
PayloadConverter::encode(const Memory & symbol, const Inspector & inspector)
{
    switch (inspector.type().getId()) {
        case OBJECT::ID:
            encodeObject(symbol, inspector);
            break;
        case ARRAY::ID:
            encodeArray(symbol, inspector);
            break;
        default:
            _nodeStack.push_back(Node(symbol.make_string()));
            encodeValue(inspector);
            _nodeStack.pop_back();
            break;
    }
}

void
PayloadConverter::field(const Memory& symbol, const Inspector & inspector)
{
    encode(symbol, inspector);
}

void
PayloadConverter::entry(size_t idx, const Inspector & inspector)
{
    _nodeStack.push_back(Node(idx));
    encode(inspector);
    _nodeStack.pop_back();
}

void
PayloadConverter::printPrefix()
{
    for (size_t i = 0; i < _nodeStack.size(); i++) {
        bool first = (i == 0);
        Node & node(_nodeStack[i]);
        if (node.arrayIndex >= 0) {
            encodeString("[");
            encodeLong(node.arrayIndex);
            encodeString("]");
        } else {
            if (!first) {
                encodeString(".");
            }
            encodeString(node.name);
        }
    }
    encodeString(" ");
}

void
PayloadConverter::encodeValue(const Inspector & value)
{
    printPrefix();
    switch (value.type().getId()) {
        case STRING::ID:
            encodeQuotedString(value.asString().make_string());
            break;
        case LONG::ID:
            encodeLong(value.asLong());
            break;
        case DOUBLE::ID:
            encodeDouble(value.asDouble());
            break;
        case BOOL::ID:
            encodeBool(value.asBool());
            break;
    }
    _lines.push_back(_buf.str());
    _buf.clear();
}

void
PayloadConverter::encodeString(const vespalib::string & value)
{
    _buf << value;
}

void PayloadConverter::encodeLong(long value) { _buf << value; }
void PayloadConverter::encodeDouble(double value) { _buf << value; }
void PayloadConverter::encodeBool(bool value) { _buf << (value ? "true" : "false"); }

void
PayloadConverter::encodeQuotedString(const vespalib::string & value)
{
    encodeString("\"");
    encodeString(value);
    encodeString("\"");
}


} // namespace config
