// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_path.h"
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".fdispatch.search_path");

namespace fdispatch {

SearchPath::Element::Element()
    : _nodes(),
      _row(std::numeric_limits<size_t>::max())
{
}

vespalib::stringref
SearchPath::parseElement(const vespalib::stringref &spec, size_t numNodes)
{
    _elements.push_back(Element());
    vespalib::string::size_type specSepPos(spec.find('/'));
    parsePartList(spec.substr(0, specSepPos), numNodes);

    vespalib::stringref remaining = spec.substr(specSepPos + 1);
    vespalib::string::size_type elementSepPos = remaining.find(';');
    parseRow(remaining.substr(0, elementSepPos));

    if (elementSepPos != vespalib::string::npos) {
        return remaining.substr(elementSepPos + 1);
    }
    return vespalib::stringref();
}

void
SearchPath::parsePartList(const vespalib::stringref &partSpec, size_t numNodes)
{
    try {
        if (!partSpec.empty() && (partSpec[0] != '*')) {
            vespalib::asciistream is(partSpec);
            is.eatWhite();
            parsePartList(is, numNodes);
        } else {
            for (size_t i(0); i < numNodes; i++) {
                _elements.back().addPart(i);
            }
        }
    } catch (const std::exception & e) {
        LOG(warning, "Failed parsing part of searchpath='%s' with error '%s'. Result might be mumbo jumbo.",
            partSpec.c_str(), e.what());
    }
}

void
SearchPath::parsePartList(vespalib::asciistream &spec, size_t numNodes)
{
    spec.eatWhite();
    if ( !spec.empty() ) {
        char c(spec.c_str()[0]);
        if (c == '[') {
            parsePartRange(spec, numNodes);
        } else {
            size_t num(0);
            spec >> num;
            _elements.back().addPart(num);
        }
        if ( ! spec.eof() ) {
            spec >> c;
            if (c == ',') {
                parsePartList(spec, numNodes);
            }
        }
    } else {
        throw std::runtime_error("Expected either '[' or a number, got EOF");
    }
}

void
SearchPath::parsePartRange(vespalib::asciistream &spec, size_t numNodes)
{
    size_t from(0);
    size_t to(numNodes);
    char s(0), c(0), e(0);
    spec >> s >> from >> c >> to >> e;
    if (c != ',') {
        throw std::runtime_error("Expected ','");
    }
    if (e != '>') {
        throw std::runtime_error("Expected '>'");
    }
    to = std::min(numNodes, to);
    for (size_t i(from); i < to; i++) {
        _elements.back().addPart(i);
    }
}

void
SearchPath::parseRow(const vespalib::stringref &rowSpec)
{
    if (!rowSpec.empty()) {
        _elements.back().setRow(strtoul(rowSpec.c_str(), NULL, 0));
    }
}

SearchPath::SearchPath(const vespalib::string &spec, size_t numNodes)
    : _elements()
{
    vespalib::stringref specBuf = spec;
    while (!specBuf.empty()) {
        specBuf = parseElement(specBuf, numNodes);
    }
}

} // namespace fdispatch
