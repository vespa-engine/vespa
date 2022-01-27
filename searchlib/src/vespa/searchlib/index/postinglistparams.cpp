// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistparams.h"
#include <sstream>

namespace {

vespalib::string empty;

}

namespace search::index {

bool
PostingListParams::isSet(const vespalib::string &key) const
{
    Map::const_iterator it;

    it = _map.find(key);
    if (it != _map.end()) {
        return true;
    }
    return false;
}

void
PostingListParams::setStr(const vespalib::string &key,
                          const vespalib::string &val)
{
    _map[key] = val;
}

const vespalib::string &
PostingListParams::getStr(const vespalib::string &key) const
{
    Map::const_iterator it;

    it = _map.find(key);
    if (it != _map.end()) {
        return it->second;
    }
    return empty;
}

void
PostingListParams::clear()
{
    _map.clear();
}

void
PostingListParams::add(const PostingListParams & toAdd)
{
    _map.insert(toAdd._map.begin(), toAdd._map.end());
}

void
PostingListParams::erase(const vespalib::string &key)
{
    _map.erase(key);
}

bool
PostingListParams::operator!=(const PostingListParams &rhs) const
{
    return _map != rhs._map;
}

template <typename TYPE>
void
PostingListParams::set(const vespalib::string &key, const TYPE &val)
{
    std::ostringstream os;

    os << val;
    _map[key] = os.str();
}

template <typename TYPE>
void
PostingListParams::get(const vespalib::string &key, TYPE &val) const
{
    std::istringstream is;
    Map::const_iterator it;

    it = _map.find(key);
    if (it != _map.end()) {
        is.str(it->second);
        is >> val;
    }
}

template void
PostingListParams::set<bool>(const vespalib::string &key, const bool &val);

template void
PostingListParams::get<bool>(const vespalib::string &key, bool &val) const;

template void
PostingListParams::set<int32_t>(const vespalib::string &key, const int32_t &val);

template void
PostingListParams::get<int32_t>(const vespalib::string &key, int32_t &val) const;

template void
PostingListParams::set<uint32_t>(const vespalib::string &key, const uint32_t &val);

template void
PostingListParams::get<uint32_t>(const vespalib::string &key, uint32_t &val) const;

template void
PostingListParams::set<uint64_t>(const vespalib::string &key, const uint64_t &val);

template void
PostingListParams::get<uint64_t>(const vespalib::string &key, uint64_t &val) const;

}
