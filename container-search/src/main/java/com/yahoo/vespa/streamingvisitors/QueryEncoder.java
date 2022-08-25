// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.fs4.MapEncoder;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.query.Highlight;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.rpc.ProtobufSerialization;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes the query in binary form.
 *
 * @author bratseth
 */
class QueryEncoder {

    /**
     * Encodes properties of this query.
     *
     * @param buffer the buffer to encode to
     * @return the encoded length
     */
    static int encodeAsProperties(Query query, ByteBuffer buffer) {
        // Make sure we don't encode anything here if we have turned the property feature off
        // Due to sendQuery we sometimes end up turning this feature on and then encoding a 0 int as the number of
        // property maps - that's ok (probably we should simplify by just always turning the feature on)
        if (! hasEncodableProperties(query)) return 0;

        int start = buffer.position();
        int mapCountPosition = buffer.position();
        buffer.putInt(0); // map count will go here
        int mapCount = 0;
        mapCount += query.getRanking().getProperties().encode(buffer, true);
        mapCount += query.getRanking().getFeatures().encode(buffer);
        if (query.getPresentation().getHighlight() != null) {
            mapCount += MapEncoder.encodeMultiMap(Highlight.HIGHLIGHTTERMS,
                                                  query.getPresentation().getHighlight().getHighlightTerms(), buffer);
        }
        mapCount += MapEncoder.encodeMap("model", createModelMap(query), buffer);
        mapCount += MapEncoder.encodeSingleValue(DocumentDatabase.MATCH_PROPERTY, DocumentDatabase.SEARCH_DOC_TYPE_KEY,
                                                 query.getModel().getDocumentDb(), buffer);
        mapCount += MapEncoder.encodeMap("caches", createCacheSettingMap(query), buffer);
        buffer.putInt(mapCountPosition, mapCount);
        return buffer.position() - start;
    }

    static boolean hasEncodableProperties(Query query) {
        if ( ! query.getRanking().getProperties().isEmpty()) return true;
        if ( ! query.getRanking().getFeatures().isEmpty()) return true;
        if ( query.getRanking().getFreshness() != null) return true;
        if ( query.getModel().getSearchPath() != null) return true;
        if ( query.getModel().getDocumentDb() != null) return true;
        if ( query.getPresentation().getHighlight() != null &&
             ! query.getPresentation().getHighlight().getHighlightItems().isEmpty()) return true;
        return false;
    }

    private static Map<String, Boolean> createCacheSettingMap(Query query) {
        if (query.getGroupingSessionCache() && query.getRanking().getQueryCache()) {
            Map<String, Boolean> cacheSettingMap = new HashMap<>();
            cacheSettingMap.put("grouping", true);
            cacheSettingMap.put("query", true);
            return cacheSettingMap;
        }
        if (query.getGroupingSessionCache())
            return Collections.singletonMap("grouping", true);
        if (query.getRanking().getQueryCache())
            return Collections.singletonMap("query", true);
        return Collections.emptyMap();
    }

    private static Map<String, String> createModelMap(Query query) {
        Map<String, String> m = new HashMap<>();
        if (query.getModel().getSearchPath() != null) m.put("searchpath", query.getModel().getSearchPath());

        int traceLevel = ProtobufSerialization.getTraceLevelForBackend(query);
        if (traceLevel > 0) m.put("tracelevel", String.valueOf(traceLevel));

        return m;
    }

}
