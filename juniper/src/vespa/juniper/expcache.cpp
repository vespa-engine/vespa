// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "expcache.h"
#include "matchobject.h"

ExpansionCache::ExpansionCache(MatchObject* default_obj)
    : _default(default_obj), _cache()
{}


ExpansionCache::~ExpansionCache()
{
    // Delete all associated maps
    _cache.delete_second();
}


MatchObject* ExpansionCache::Lookup(uint32_t langid)
{
    MatchObject* m = _cache.find(langid);
    if (!m)
    {
        m = new MatchObject(_default->Query(), _default->HasReductions(), langid);
        _cache.insert(langid, m);
    }
    return m;
}
