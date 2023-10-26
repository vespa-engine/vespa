// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <ostream>
#include <set>
#include <vector>

namespace std {

template <typename T>
std::ostream &
operator<<(std::ostream &os, const std::set<T> &set)
{
    os << "{";
    bool first = true;
    for (const auto &entry : set) {
        if (!first) {
            os << ",";
        }
        os << entry;
        first = false;
    }
    os << "}";
    return os;
}

template <typename T>
std::ostream &
operator<<(std::ostream &os, const std::vector<T> &set)
{
    os << "[";
    bool first = true;
    for (const auto &entry : set) {
        if (!first) {
            os << ",";
        }
        os << entry;
        first = false;
    }
    os << "]";
    return os;
}

template <typename K, typename V>
std::ostream &
operator<<(std::ostream &os, const std::map<K, V> &map)
{
    os << "{";
    bool first = true;
    for (const auto &entry : map) {
        if (!first) {
            os << ",";
        }
        os << "{" << entry.first << "," << entry.second << "}";
        first = false;
    }
    os << "}";
    return os;
}

template <typename T, typename U>
std::ostream &
operator<<(std::ostream &os, const std::pair<T,U> &pair)
{
    os << "{";
    os << pair.first;
    os << ",";
    os << pair.second;
    os << "}";
    return os;
}

} // namespace std

