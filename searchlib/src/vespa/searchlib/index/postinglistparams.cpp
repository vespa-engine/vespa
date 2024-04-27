// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistparams.h"
#include <sstream>

namespace {

vespalib::string empty;

}

namespace search::index {

bool
PostingListParams::isSet(const vespalib::string &key) const
{
    auto it = _map.find(key);
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
    auto it = _map.find(key);
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
    auto it = _map.find(key);
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
PostingListParams::set<int>(const vespalib::string& key, const int& val);

template void
PostingListParams::get<int>(const vespalib::string& key, int& val) const;

template void
PostingListParams::set<unsigned int>(const vespalib::string& key, const unsigned int& val);

template void
PostingListParams::get<unsigned int>(const vespalib::string& key, unsigned int& val) const;

template void
PostingListParams::set<unsigned long>(const vespalib::string& key, const unsigned long& val);

template void
PostingListParams::get<unsigned long>(const vespalib::string& key, unsigned long& val) const;

template void
PostingListParams::set<unsigned long long>(const vespalib::string& key, const unsigned long long& val);

template void
PostingListParams::get<unsigned long long>(const vespalib::string& key, unsigned long long& val) const;

}
