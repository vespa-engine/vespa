// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.google.common.collect.ImmutableList;
import com.yahoo.search.Query;

import java.util.*;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * A central repository for information about indices. Standard usage is
 *
 * <pre><code>
 * IndexFacts.Session session = indexFacts.newSession(query); // once when starting to process a query
 * session.getIndex(indexName).[get index info]
 * </code></pre>
 *
 * @author Steinar Knutsen
 */
// TODO: We should replace this with a better representation of search definitions
//       which is immutable, models clusters and search definitions inside clusters properly,
//       and uses better names. -bratseth
public class IndexFacts {

    private Map<String, List<String>> clusterByDocument;

    private static class DocumentTypeListOffset {
        public final int offset;
        public final SearchDefinition searchDefinition;

        public DocumentTypeListOffset(int offset, SearchDefinition searchDefinition) {
            this.offset = offset;
            this.searchDefinition = searchDefinition;
        }
    }

    /** A Map of all known search definitions indexed by name */
    private Map<String, SearchDefinition> searchDefinitions = new LinkedHashMap<>();

    /** A map of document types contained in each cluster indexed by cluster name */
    private Map<String, List<String>> clusters = new LinkedHashMap<>();

    /**
     * The name of the default search definition, which is the union of all
     * known document types.
     * 
     * @deprecated do not use
     */
    // TODO: Make this package private in Vespa 7
    @Deprecated
    public static final String unionName = "unionOfAllKnown";

    /** A search definition which contains the union of all settings. */
    @SuppressWarnings("deprecation")
    private SearchDefinition unionSearchDefinition = new SearchDefinition(unionName);

    private boolean frozen;

    /** Whether this has (any) NGram indexes. Calculated at freeze time. */
    private boolean hasNGramIndices;

    public IndexFacts() {}

    @SuppressWarnings({"deprecation"})
    public IndexFacts(IndexModel indexModel) {
        if (indexModel.getSearchDefinitions() != null && indexModel.getUnionSearchDefinition() != null) {
            setSearchDefinitions(indexModel.getSearchDefinitions(), indexModel.getUnionSearchDefinition());
        }
        if (indexModel.getMasterClusters() != null) {
            setMasterClusters(indexModel.getMasterClusters());
        }
    }

    private void setMasterClusters(Map<String, List<String>> clusters) {
        // TODO: clusters should probably be a separate class
        this.clusters = clusters;
        clusterByDocument = invert(clusters);
    }

    private static Map<String, List<String>> invert(Map<String, List<String>> clusters) {
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String,List<String>> entry : clusters.entrySet()) {
            for (String value : entry.getValue()) {
                addEntry(result, value, entry.getKey());
            }
        }
        return result;
    }

    private static void addEntry(Map<String, List<String>> result, String key, String value) {
        List<String> values = result.get(key);
        if (values == null) {
            values = new ArrayList<>();
            result.put(key, values);
        }
        values.add(value);
    }

    // Assumes that document names are equal to the search definition that contain them.
    public List<String> clustersHavingSearchDefinition(String searchDefinitionName) {
        if (clusterByDocument == null) return Collections.emptyList();

        List<String> clusters = clusterByDocument.get(searchDefinitionName);
        return clusters != null ? clusters : Collections.<String>emptyList();
    }

    /**
     * Public only for testing.
     */
    public void setClusters(Map<String, List<String>> clusters) {
        ensureNotFrozen();
        this.clusters = clusters;
        clusterByDocument = invert(clusters);
    }

    /**
     * @deprecated set indexes at creation time instead
     */
    // TODO: Remove on Vespa 7
    @Deprecated
    public void setSearchDefinitions(Map<String, SearchDefinition> searchDefinitions,
                                     SearchDefinition unionSearchDefinition) {
        ensureNotFrozen();
        this.searchDefinitions = searchDefinitions;
        this.unionSearchDefinition = unionSearchDefinition;
    }

    private boolean isInitialized() {
        return searchDefinitions.size() > 0;
    }

    private boolean isIndexFromDocumentTypes(String indexName, List<String> documentTypes) {
        if ( ! isInitialized()) return true;

        if (documentTypes.isEmpty()) {
            return unionSearchDefinition.getIndex(indexName) != null;
        }

        DocumentTypeListOffset sd = chooseSearchDefinition(documentTypes, 0);
        while (sd != null) {
            Index index = sd.searchDefinition.getIndex(indexName);
            if (index != null) {
                return true;
            }
            sd = chooseSearchDefinition(documentTypes, sd.offset);
        }

        return false;
    }

    private String getCanonicNameFromDocumentTypes(String indexName, List<String> documentTypes) {
        if (!isInitialized()) return indexName;

        if (documentTypes.isEmpty()) {
            Index index = unionSearchDefinition.getIndexByLowerCase(toLowerCase(indexName));
            return index == null ? indexName : index.getName();
        }
        DocumentTypeListOffset sd = chooseSearchDefinition(documentTypes, 0);
        while (sd != null) {
            Index index = sd.searchDefinition.getIndexByLowerCase(toLowerCase(indexName));
            if (index != null) return index.getName();
            sd = chooseSearchDefinition(documentTypes, sd.offset);
        }
        return indexName;
    }

    private Index getIndexFromDocumentTypes(String indexName, List<String> documentTypes) {
        if (indexName == null || indexName.isEmpty())
            indexName = "default";

        return getIndexByCanonicNameFromDocumentTypes(indexName, documentTypes);
    }

    private Index getIndexByCanonicNameFromDocumentTypes(String canonicName, List<String> documentTypes) {
        if ( ! isInitialized()) return Index.nullIndex;

        if (documentTypes.isEmpty()) {
            Index index = unionSearchDefinition.getIndex(canonicName);
            if (index == null) return Index.nullIndex;
            return index;
        }

        DocumentTypeListOffset sd = chooseSearchDefinition(documentTypes, 0);
        while (sd != null) {
            Index index = sd.searchDefinition.getIndex(canonicName);

            if (index != null) return index;
            sd = chooseSearchDefinition(documentTypes, sd.offset);
        }
        return Index.nullIndex;
    }

    private Collection<Index> getIndexes(String documentType) {
        if ( ! isInitialized()) return Collections.emptyList();
        SearchDefinition sd = searchDefinitions.get(documentType);
        if (sd == null) return Collections.emptyList();
        return sd.indices().values();
    }

    /** Calls resolveDocumentTypes(query.getModel().getSources(), query.getModel().getRestrict()) */
    private Set<String> resolveDocumentTypes(Query query) {
        // Assumption: Search definition name equals document name.
        return resolveDocumentTypes(query.getModel().getSources(), query.getModel().getRestrict(),
                                    searchDefinitions.keySet());
    }

    /**
     * Given a search list which is a mixture of document types and cluster
     * names, and a restrict list which is a list of document types, return a
     * set of all valid document types for this combination. Most use-cases for
     * fetching index settings will involve calling this method with the the
     * incoming query's {@link com.yahoo.search.query.Model#getSources()} and
     * {@link com.yahoo.search.query.Model#getRestrict()} as input parameters
     * before calling any other method of this class.
     *
     * @param sources the search list for a query
     * @param restrict the restrict list for a query
     * @return a (possibly empty) set of valid document types
     */
    private Set<String> resolveDocumentTypes(Collection<String> sources, Collection<String> restrict,
                                             Set<String> candidateDocumentTypes) {
        sources = emptyCollectionIfNull(sources);
        restrict = emptyCollectionIfNull(restrict);

        if (sources.isEmpty()) {
            if ( ! restrict.isEmpty()) {
                return new TreeSet<>(restrict);
            } else {
                return candidateDocumentTypes;
            }
        }

        Set<String> toSearch = new TreeSet<>();
        for (String source : sources) { // source: a document type or a cluster containing them
            List<String> clusterDocTypes = clusters.get(source);
            if (clusterDocTypes == null) { // source was a document type
                if (candidateDocumentTypes.contains(source)) {
                    toSearch.add(source);
                }
            } else { // source was a cluster, having document types
                for (String documentType : clusterDocTypes) {
                    if (candidateDocumentTypes.contains(documentType)) {
                        toSearch.add(documentType);
                    }
                }
            }
        }

        if ( ! restrict.isEmpty()) {
            toSearch.retainAll(restrict);
        }

        return toSearch;
    }

    private Collection<String> emptyCollectionIfNull(Collection<String> collection) {
        return collection == null ? Collections.<String>emptyList() : collection;
    }

    /**
     * Chooses the correct search definition, default if in doubt.
     *
     * @return the search definition to use
     */
    private DocumentTypeListOffset chooseSearchDefinition(List<String> documentTypes, int index) {
        while (index < documentTypes.size()) {
            String docName = documentTypes.get(index++);
            SearchDefinition sd = searchDefinitions.get(docName);
            if (sd != null) {
                return new DocumentTypeListOffset(index, sd);
            }
        }
        return null;
    }

    /**
     * Freeze this to prevent further changes.
     */
    public void freeze() {
        hasNGramIndices = hasNGramIndices();
        // TODO: Freeze content!
        frozen = true;
    }

    /** Whether this contains any index which has isNGram()==true. This is free to ask on a frozen instance. */
    public boolean hasNGramIndices() {
        if (frozen) return hasNGramIndices;
        for (Map.Entry<String,SearchDefinition> searchDefinition : searchDefinitions.entrySet()) {
            for (Index index : searchDefinition.getValue().indices().values())
                if (index.isNGram()) return true;
        }
        return false;
    }

    /**
     * @return whether it is permissible to update this object
     */
    public boolean isFrozen() {
        return frozen;
    }

    private void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Tried to modify frozen IndexFacts instance.");
        }
    }


    /**
     * Add a string to be accepted as an index name when parsing a
     * query.
     *
     * For testing only.
     *
     * @param sdName name of search definition containing index, if null, modify default set
     * @param indexName name of index, actual or otherwise
     * @deprecated set indexes at creation time instead
     */
    // TODO: Remove on Vespa 7
    @Deprecated
    public void addIndex(String sdName, String indexName) {
        ensureNotFrozen();

        SearchDefinition sd;
        if (sdName == null) {
            sd = unionSearchDefinition;
        } else if (searchDefinitions.containsKey(sdName)) {
            sd = searchDefinitions.get(sdName);
        } else {
            sd = new SearchDefinition(sdName);
            searchDefinitions.put(sdName, sd);
        }
        sd.getOrCreateIndex(indexName);
        unionSearchDefinition.getOrCreateIndex(indexName);
    }

    /**
     * Adds an index to the specified index, and the default index settings,
     * overriding any current settings for this index
     * @deprecated set indexes at creation time instead
     */
    // TODO: Remove on Vespa 7
    @Deprecated
    public void addIndex(String sdName, Index index) {
        ensureNotFrozen();

        SearchDefinition sd;
        if (sdName == null) {
            sd = unionSearchDefinition;
        } else if (searchDefinitions.containsKey(sdName)) {
            sd = searchDefinitions.get(sdName);
        } else {
            sd = new SearchDefinition(sdName);
            searchDefinitions.put(sdName, sd);
        }
        sd.addIndex(index);
        unionSearchDefinition.addIndex(index);
    }

    public String getDefaultPosition(String sdName) {
        SearchDefinition sd;
        if (sdName == null) {
            sd = unionSearchDefinition;
        } else if (searchDefinitions.containsKey(sdName)) {
            sd = searchDefinitions.get(sdName);
        } else {
            return null;
        }

        return sd.getDefaultPosition();
    }

    public Session newSession(Query query) {
        return new Session(query);
    }

    public Session newSession(Collection<String> sources, Collection<String> restrict) {
        return new Session(sources, restrict);
    }

    public Session newSession(Collection<String> sources, Collection<String> restrict,
                              Set<String> candidateDocumentTypes) {
        return new Session(sources, restrict, candidateDocumentTypes);
    }

    /**
     * Create an instance of this to look up index facts with a given query.
     * Note that if the model.source or model.restrict parameters of the query
     * is changed another session should be created. This is immutable.
     */
    public class Session {

        private final List<String> documentTypes;

        private Session(Query query) {
            documentTypes = ImmutableList.copyOf(resolveDocumentTypes(query));
        }

        private Session(Collection<String> sources, Collection<String> restrict) {
            // Assumption: Search definition name equals document name.
            documentTypes = ImmutableList.copyOf(resolveDocumentTypes(sources, restrict, searchDefinitions.keySet()));
        }

        private Session(Collection<String> sources, Collection<String> restrict, Set<String> candidateDocumentTypes) {
            documentTypes = ImmutableList.copyOf(resolveDocumentTypes(sources, restrict, candidateDocumentTypes));
        }

        /**
         * Returns the index for this name.
         *
         * @param indexName the name of the index. If this is null or empty the index
         *                  named "default" is returned
         * @return the index best matching the input parameters or the nullIndex
         *         (never null) if none is found
         */
        public Index getIndex(String indexName) {
            return IndexFacts.this.getIndexFromDocumentTypes(indexName, documentTypes);
        }

        /** Returns an index given from a given search definition */
        // Note: This does not take the context into account currently.
        // Ideally, we should be able to resolve the right search definition name
        // in the context of the searched clusters, but this cannot be modelled
        // currently by the flat structure in IndexFacts.
        // That can be fixed without changing this API.
        public Index getIndex(String indexName, String documentType) {
            return IndexFacts.this.getIndexFromDocumentTypes(indexName, Collections.singletonList(documentType));
        }

        /** Returns all the indexes of a given search definition */
        public Collection<Index> getIndexes(String documentType) {
            return IndexFacts.this.getIndexes(documentType);
        }

        /**
         * Returns the canonical form of the index name (Which may be the same as
         * the input).
         *
         * @param indexName index name or alias
         */
        public String getCanonicName(String indexName) {
            return IndexFacts.this.getCanonicNameFromDocumentTypes(indexName, documentTypes);
        }

        /**
         * Returns whether the given name is an index.
         *
         * @param indexName index name candidate
         */
        public boolean isIndex(String indexName) {
            return IndexFacts.this.isIndexFromDocumentTypes(indexName, documentTypes);
        }

        /** Returns an immutable list of the document types this has resolved to */
        public List<String> documentTypes() { return documentTypes; }

        @Override
        public String toString() {
            return "index facts for search definitions " + documentTypes;
        }

    }

}
