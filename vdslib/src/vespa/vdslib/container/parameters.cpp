// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parameters.hpp"
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <ostream>
#include <charconv>

using namespace vdslib;

Parameters::Parameters() = default;

Parameters::Parameters(document::ByteBuffer& buffer)
    : _parameters()
{
    deserialize(buffer);
}

Parameters::Parameters(const Parameters&) = default;
Parameters& Parameters::operator=(const Parameters&) = default;

Parameters::Parameters(Parameters&&) noexcept = default;
Parameters& Parameters::operator=(Parameters&&) noexcept = default;

Parameters::~Parameters() = default;

size_t Parameters::getSerializedSize() const
{
    size_t mysize = sizeof(int32_t);
    for (const auto & entry : _parameters) {
        mysize += entry.first.size() + 4 + 4 + entry.second.size();
    }
    return mysize;
}

void Parameters::serialize(vespalib::GrowableByteBuffer& buffer) const
{
    buffer.putInt(_parameters.size());
    for (const auto & entry : _parameters) {
        buffer.putInt(entry.first.size());
        buffer.putBytes(entry.first.c_str(), entry.first.size());
        buffer.putInt(entry.second.size());
        buffer.putBytes(entry.second.c_str(), entry.second.size());
    }
}

void Parameters::deserialize(document::ByteBuffer& buffer)
{
    _parameters.clear();
    int32_t mysize;
    buffer.getIntNetwork(mysize);
    _parameters.resize(mysize);
    for (int i=0; i<mysize; i++) {
        int32_t keylen = 0;
        buffer.getIntNetwork(keylen);
        vespalib::stringref key(buffer.getBufferAtPos(), keylen);
        buffer.incPos(keylen);
        int32_t sz(0);
        buffer.getIntNetwork(sz);
        _parameters[key] = Value(buffer.getBufferAtPos(), sz);
        buffer.incPos(sz);
    }
}

void
Parameters::printXml(vespalib::xml::XmlOutputStream& xos) const
{
    using namespace vespalib::xml;
    xos << XmlTag("parameters");
    for (const auto & entry : _parameters) {
        xos << XmlTag("item")
                << XmlTag("name") << entry.first << XmlEndTag()
                << XmlTag("value") << entry.second << XmlEndTag()
            << XmlEndTag();
    }
    xos << XmlEndTag();
}

bool
Parameters::operator==(const Parameters &other) const
{
    if (size() != other.size()) {
        return false;
    }

    for (ParametersMap::const_iterator a(_parameters.begin()), b(other._parameters.begin()), am(_parameters.begin()); (a != am); ++a, ++b) {
        if ((a->first != b->first)) {
            return false;
        }
    }

    return true;
}

vespalib::stringref Parameters::get(vespalib::stringref id, vespalib::stringref def) const
{
    ParametersMap::const_iterator it = _parameters.find(id);
    if (it == _parameters.end()) return def;
    return it->second;
}

bool Parameters::lookup(KeyT id, ValueRef & v ) const
{
    ParametersMap::const_iterator it = _parameters.find(id);
    if (it == _parameters.end()) return false;
    v = ValueRef(it->second.c_str(), it->second.size());
    return true;
}

void Parameters::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "Parameters(";
    if (!verbose) {
        out << _parameters.size() << " values";
    } else {
        for (const auto & entry : _parameters) {
            bool isPrintable(true);
            for (size_t i(0), m(entry.second.size()); isPrintable && (i < m); i++) {
                isPrintable = isprint(entry.second[i]);
            }
            out << "\n" << indent << "           " << entry.first << " = ";
            if (!entry.second.empty() && isPrintable && (entry.second[entry.second.size()-1] == 0)) {
                out  << entry.second.c_str();
            } else {
                out  << vespalib::HexDump(entry.second.c_str(), entry.second.size());
            }
        }
    }
    out << ")";
}

vespalib::string Parameters::toString() const
{
    vespalib::string ret;
    for (const auto & entry : _parameters) {
        ret += entry.first;
        ret += '=';
        ret += entry.second;
        ret += '|';
    }
    return ret;
}

void
Parameters::set(KeyT id, int32_t value) {
    char tmp[16];
    auto res = std::to_chars(tmp, tmp + sizeof(tmp), value, 10);
    _parameters[id] = Value(tmp, size_t(res.ptr - tmp));
}

void
Parameters::set(KeyT id, int64_t value) {
    char tmp[32];
    auto res = std::to_chars(tmp, tmp + sizeof(tmp), value, 10);
    _parameters[id] = Value(tmp, size_t(res.ptr - tmp));
}

void
Parameters::set(KeyT id, uint64_t value) {
    char tmp[32];
    auto res = std::to_chars(tmp, tmp + sizeof(tmp), value, 10);
    _parameters[id] = Value(tmp, size_t(res.ptr - tmp));
}

void
Parameters::set(KeyT id, double value) {
    vespalib::asciistream ost;
    ost << value;
    _parameters[id] = Value(ost.str());
}


template int32_t vdslib::Parameters::get(vespalib::stringref , int32_t) const;
template int64_t vdslib::Parameters::get(vespalib::stringref , int64_t) const;
template uint64_t vdslib::Parameters::get(vespalib::stringref , uint64_t) const;
template double vdslib::Parameters::get(vespalib::stringref , double) const;
template std::string vdslib::Parameters::get(vespalib::stringref , std::string) const;

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, vdslib::Parameters::Value);
