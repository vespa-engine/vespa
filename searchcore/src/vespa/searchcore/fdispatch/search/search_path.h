// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <set>
#include <vector>

namespace vespalib {
    class asciistream;
}
namespace fdispatch {

class SearchPath
{
public:
    typedef std::set<size_t> NodeList;

    class Element
    {
    private:
        NodeList _nodes;
        size_t   _row;

    public:
        Element();
        Element &addPart(size_t part) {
            _nodes.insert(part);
            return *this;
        }
        Element &setRow(size_t row_) {
            _row = row_;
            return *this;
        }
        bool hasRow() const { return _row != std::numeric_limits<size_t>::max(); }
        size_t row() const { return _row; }
        const NodeList &nodes() const { return _nodes; }
    };

    typedef std::vector<Element> ElementVector;

private:
    ElementVector _elements;

    vespalib::stringref parseElement(const vespalib::stringref &spec, size_t numNodes);
    void parsePartList(const vespalib::stringref &partSpec, size_t numNodes);
    void parsePartList(vespalib::asciistream &spec, size_t numNodes);
    void parsePartRange(vespalib::asciistream &spec, size_t numNodes);
    void parseRow(const vespalib::stringref &rowSpec);

public:
    SearchPath(const vespalib::string &spec, size_t numNodes);
    const ElementVector &elements() const { return _elements; }
};

} // namespace fdispatch

