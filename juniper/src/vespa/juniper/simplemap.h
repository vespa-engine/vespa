// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <cstddef>

template<typename _key, typename _val>
class simplemap
{
public:
    explicit simplemap() : _map() {}

    explicit simplemap(simplemap& m)
    {
        _map = m.map();
    }

    virtual ~simplemap() {}

    _val insert(_key key, _val val)
    {
        typename std::pair<typename std::map<_key,_val>::iterator, bool> p =
            _map.insert(std::make_pair(key, val));
        if (p.second) return p.first->second;
        return NULL;
    }

    _val find(_key key)
    {
        typename std::map<_key,_val>::iterator it = _map.find(key);
        if (it != _map.end())
            return it->second;
        else
            return NULL;
    }

    size_t size() { return _map.size(); }

    typename std::map<_key,_val>::iterator begin() { return _map.begin(); }
    typename std::map<_key,_val>::iterator end() { return _map.end(); }

    void delete_second()
    {
        typename std::map<_key,_val>::iterator it(_map.begin());
        for (;it != _map.end(); ++it)
        {
            delete(it->second);
            it->second = NULL;
        }
    }

    void clear()
    {
        _map.clear();
    }
protected:
    std::map<_key,_val>& map() { return _map; }
private:
    std::map<_key,_val> _map;
};


