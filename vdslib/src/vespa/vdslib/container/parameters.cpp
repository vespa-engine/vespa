// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parameters.hpp"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/xmlstream.h>

using namespace vdslib;

Parameters::Parameters() : _parameters() { }

Parameters::Parameters(const document::DocumentTypeRepo &repo, document::ByteBuffer& buffer)
    : _parameters()
{
    deserialize(repo, buffer);
}

Parameters::~Parameters()
{
}

size_t Parameters::getSerializedSize() const
{
    size_t mysize = sizeof(int32_t);
    for (const auto & entry : _parameters) {
        mysize += entry.first.size() + 4 + 4 + entry.second.size();
    }
    return mysize;
}

void Parameters::onSerialize(document::ByteBuffer& buffer) const
{
    buffer.putIntNetwork(_parameters.size());
    for (const auto & entry : _parameters) {
        buffer.putIntNetwork(entry.first.size());
        buffer.putBytes(entry.first.c_str(), entry.first.size());
        buffer.putIntNetwork(entry.second.size());
        buffer.putBytes(entry.second.c_str(), entry.second.size());
    }
}

void Parameters::onDeserialize(const document::DocumentTypeRepo &repo, document::ByteBuffer& buffer)
{
    (void) repo;
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
Parameters::printXml(document::XmlOutputStream& xos) const
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

/*
void
Parameters::deserializeXml(const document::XmlElement & xml)
{
    ParametersMap params;
    for(document::XmlElement::ElementList::const_iterator it = xml.elements().begin(), mt = xml.elements().end(); it != mt; it++) {
        const document::XmlElement & elem = *it;
        assert( elem.elements().size() == 2);

        const document::XmlElement & name = (*it).elements()[0];
        const document::XmlElement & value = (*it).elements()[1];
        params[name.value()]  = Value(value.value().c_str(), value.value().size());
    }
    _parameters.swap(params);
}
*/

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

Parameters* Parameters::clone() const
{
    return new Parameters(*this);
}

vespalib::stringref Parameters::get(const vespalib::stringref& id, const vespalib::stringref& def) const
{
    ParametersMap::const_iterator it = _parameters.find(id);
    if (it == _parameters.end()) return def;
    return it->second;
}

bool Parameters::get(const KeyT & id, ValueRef & v ) const
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

std::string Parameters::toString() const
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

template void vdslib::Parameters::set(const vespalib::stringref &, int32_t);
template void vdslib::Parameters::set(const vespalib::stringref &, int64_t);
template void vdslib::Parameters::set(const vespalib::stringref &, uint64_t);
template void vdslib::Parameters::set(const vespalib::stringref &, double);
template void vdslib::Parameters::set(const vespalib::stringref &, const char *);
template void vdslib::Parameters::set(const vespalib::stringref &, vespalib::string);
template void vdslib::Parameters::set(const vespalib::stringref &, std::string);
template int32_t vdslib::Parameters::get(const vespalib::stringref &, int32_t) const;
template int64_t vdslib::Parameters::get(const vespalib::stringref &, int64_t) const;
template uint64_t vdslib::Parameters::get(const vespalib::stringref &, uint64_t) const;
template double vdslib::Parameters::get(const vespalib::stringref &, double) const;
template std::string vdslib::Parameters::get(const vespalib::stringref &, std::string) const;

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, vdslib::Parameters::Value);
