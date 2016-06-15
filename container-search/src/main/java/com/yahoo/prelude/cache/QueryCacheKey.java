// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cache;

import com.yahoo.search.Query;

public class QueryCacheKey {
    private Query query;
    private int offset;
    private int hits;

    public QueryCacheKey(Query query) {
        this.query = query;
        this.offset = query.getOffset();
        this.hits = query.getHits();
    }

    public boolean equals(Object key) {
        if (key==null) {
            return false;
        }
        if (query==null) {
            return false;
        }
        if (key instanceof QueryCacheKey) {
            QueryCacheKey ckey = (QueryCacheKey)key;
            boolean res = equalQueryWith(ckey) && equalPathWith(ckey);
            return res;
        }
        return false;
    }

    private boolean equalQueryWith(QueryCacheKey other) {
        return query.equals(other.getQuery());
    }

    private boolean equalPathWith(QueryCacheKey other) {
        if (other == null) return false;
        if (other.getQuery() == null) return false;

        return query.getHttpRequest().getUri().getPath().equals(other.getQuery().getHttpRequest().getUri().getPath());
    }

    public int getHits() {
        return hits;
    }

    public int getOffset() {
        return offset;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query newQuery) {
        query = newQuery;
    }

    public String toString() {
        if (query==null) {
            return super.toString();
        }
        return query.toString();
    }

    public int hashCode() {
        if (query==null) {
            return super.hashCode();
        }
        int ret = query.hashCode();
        return ret;
    }
}
