// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <string>

namespace search::index {

class PostingListParams {
    using Map = std::map<std::string, std::string>;
    Map _map;
public:
    template <typename TYPE>
    void set(const std::string &key, const TYPE &val);

    template <typename TYPE>
    void get(const std::string &key, TYPE &val) const;

    bool isSet(const std::string &key) const;
    void setStr(const std::string &key, const std::string &val);
    const std::string & getStr(const std::string &key) const;
    void clear();
    void erase(const std::string &key);
    bool operator!=(const PostingListParams &rhs) const;
    void add(const PostingListParams & toAdd);
};

}
