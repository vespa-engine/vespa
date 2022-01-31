// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/vespalib/stllike/string.h>

namespace search::index {

class PostingListParams {
    using Map = std::map<vespalib::string, vespalib::string>;
    Map _map;
public:
    template <typename TYPE>
    void set(const vespalib::string &key, const TYPE &val);

    template <typename TYPE>
    void get(const vespalib::string &key, TYPE &val) const;

    bool isSet(const vespalib::string &key) const;
    void setStr(const vespalib::string &key, const vespalib::string &val);
    const vespalib::string & getStr(const vespalib::string &key) const;
    void clear();
    void erase(const vespalib::string &key);
    bool operator!=(const PostingListParams &rhs) const;
    void add(const PostingListParams & toAdd);
};

}
