// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistparams.h"
#include <sstream>

namespace {

std::string empty;

}

namespace search::index {

bool
PostingListParams::isSet(const std::string &key) const
{
    auto it = _map.find(key);
    if (it != _map.end()) {
        return true;
    }
    return false;
}

void
PostingListParams::setStr(const std::string &key,
                          const std::string &val)
{
    _map[key] = val;
}

const std::string &
PostingListParams::getStr(const std::string &key) const
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
PostingListParams::erase(const std::string &key)
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
PostingListParams::set(const std::string &key, const TYPE &val)
{
    std::ostringstream os;

    os << val;
    _map[key] = os.str();
}

template <typename TYPE>
void
PostingListParams::get(const std::string &key, TYPE &val) const
{
    std::istringstream is;
    auto it = _map.find(key);
    if (it != _map.end()) {
        is.str(it->second);
        is >> val;
    }
}

template void
PostingListParams::set<bool>(const std::string &key, const bool &val);

template void
PostingListParams::get<bool>(const std::string &key, bool &val) const;

template void
PostingListParams::set<int>(const std::string& key, const int& val);

template void
PostingListParams::get<int>(const std::string& key, int& val) const;

template void
PostingListParams::set<unsigned int>(const std::string& key, const unsigned int& val);

template void
PostingListParams::get<unsigned int>(const std::string& key, unsigned int& val) const;

template void
PostingListParams::set<unsigned long>(const std::string& key, const unsigned long& val);

template void
PostingListParams::get<unsigned long>(const std::string& key, unsigned long& val) const;

template void
PostingListParams::set<unsigned long long>(const std::string& key, const unsigned long long& val);

template void
PostingListParams::get<unsigned long long>(const std::string& key, unsigned long long& val) const;

}
