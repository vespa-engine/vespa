// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packets.h"
#include "mapnames.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/databuffer.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.fs4packets");

using vespalib::make_string;
using std::string_view;

namespace search::fs4transport {

/**
 * Persistent packet streamer.
 **/
FS4PersistentPacketStreamer FS4PersistentPacketStreamer::Instance;

//============================================================

FS4PersistentPacketStreamer::
FS4PersistentPacketStreamer()
    : _compressionLimit(0),
      _compressionLevel(9),
      _compressionType(CompressionConfig::LZ4)
{ }

//============================================================

FS4Properties::FS4Properties()
    : _entries(),
      _name(),
      _backing()
{ }

FS4Properties::FS4Properties(FS4Properties && rhs) noexcept
    : _entries(std::move(rhs._entries)),
      _name(std::move(rhs._name)),
      _backing(std::move(rhs._backing))
{ }

FS4Properties &
FS4Properties::operator=(FS4Properties && rhs) noexcept
{
    _entries = std::move(rhs._entries);
    _name = std::move(rhs._name);
    _backing = std::move(rhs._backing);
    return *this;
}

FS4Properties::~FS4Properties() = default;

void FS4Properties::set(StringRef & e, std::string_view s)
{
    e.first = _backing.size();
    e.second = s.size();
    _backing.append(s.data(), s.size());
}

void
FS4Properties::setKey(uint32_t entry, const char *key, uint32_t keySize)
{
    set(_entries[entry].first, std::string_view(key, keySize));
}

void
FS4Properties::setValue(uint32_t entry, const char *value, uint32_t valueSize)
{
    set(_entries[entry].second, std::string_view(value, valueSize));
}

uint32_t
FS4Properties::getLength() const noexcept
{
    uint32_t len = sizeof(uint32_t) * 2 + name().size();
    len += _backing.size();
    len += _entries.size() * sizeof(uint32_t) * 2;
    return len;
}

std::string_view
FS4Properties::key(uint32_t entry) const noexcept {
    auto pair = _entries[entry].first;
    return {c_str(pair.first), pair.second};
}
std::string_view
FS4Properties::value(uint32_t entry) const noexcept {
    auto pair = _entries[entry].second;
    return {c_str(pair.first), pair.second};
}

vespalib::string
FS4Properties::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sProperties {\n", indent, "");
    s += make_string("%*s  name: ", indent, "");
    s += _name;
    s += "\n";
    for (uint32_t i = 0; i < size(); ++i) {
        s += make_string("%*s  Entry[%d] {\n", indent, "", i);
        s += make_string("%*s    key  : %s\n", indent, "", string(key(i)).c_str());
        s += make_string("%*s    value: %s\n", indent, "", string(value(i)).c_str());
        s += make_string("%*s  }\n", indent, "");
    }
    s += make_string("%*s}\n", indent, "");
    return s;
}

bool
FS4Properties::decode(FNET_DataBuffer &src, uint32_t &len)
{
    if (len < sizeof(uint32_t)) return false;
    uint32_t strLen = src.ReadInt32();
    len -= sizeof(uint32_t);
    if (len < strLen) return false;
    setName(src.GetData(), strLen);
    src.DataToDead(strLen);
    len -= strLen;
    if (len < sizeof(uint32_t)) return false;
    uint32_t cnt = src.ReadInt32();
    len -= sizeof(uint32_t);
    allocEntries(cnt);
    for (uint32_t i = 0; i < cnt; ++i) {
        if (len < sizeof(uint32_t)) return false;
        strLen = src.ReadInt32();
        len -= sizeof(uint32_t);
        if (len < strLen) return false;
        setKey(i, src.GetData(), strLen);
        src.DataToDead(strLen);
        len -= strLen;
        if (len < sizeof(uint32_t)) return false;
        strLen = src.ReadInt32();
        len -= sizeof(uint32_t);
        if (len < strLen) return false;
        setValue(i, src.GetData(), strLen);
        src.DataToDead(strLen);
        len -= strLen;
    }
    return true;
}

void
FS4Properties::allocEntries(uint32_t cnt)
{
    _entries.resize(cnt);
    _backing.reserve(cnt*2*40); // Assume strings are average 40 bytes
}

}
