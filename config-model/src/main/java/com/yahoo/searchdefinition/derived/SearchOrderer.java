// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataTypeName;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.Search;

import java.util.*;

/**
 * <p>A class which can reorder a list of search definitions such that any supertype
 * always preceed any subtype. Subject to this condition the given order
 * is preserved (the minimal reordering is done).</p>
 *
 * <p>This class is <b>not</b> multithread safe. Only one ordering must be done
 * at the time in any instance.</p>
 *
 * @author bratseth
 * @author bjorncs
 */
public class SearchOrderer {

    /** A map from DataTypeName to the Search defining them */
    private final Map<DataTypeName, Search> documentNameToSearch = new HashMap<>();

    /**
     * Reorders the given list of search definitions such that any supertype
     * always preceed any subtype. Subject to this condition the given order
     * is preserved (the minimal reordering is done).
     *
     * @return a new list containing the same search instances in the right order
     */
    public List<Search> order(List<Search> unordered) {
        // Description above state that the original order should be preserved, except for the dependency constraint.
        // Yet we botch that guarantee by sorting the list...
        unordered.sort(Comparator.comparing(Search::getName));

        // No, this is not a fast algorithm...
        indexOnDocumentName(unordered);
        List<Search> ordered = new ArrayList<>(unordered.size());
        List<Search> moveOutwards = new ArrayList<>();
        for (Search search : unordered) {
            if (allDependenciesAlreadyEmitted(ordered, search)) {
                addOrdered(ordered, search, moveOutwards);
            }
            else {
                moveOutwards.add(search);
            }
        }

        // Any leftovers means we have search definitions with undefined inheritants.
        // This is warned about elsewhere.
        ordered.addAll(moveOutwards);

        documentNameToSearch.clear();
        return ordered;
    }

    private void addOrdered(List<Search> ordered, Search search, List<Search> moveOutwards) {
        ordered.add(search);
        Search eligibleMove;
        do {
            eligibleMove = removeFirstEntryWithFullyEmittedDependencies(moveOutwards, ordered);
            if (eligibleMove != null) {
                ordered.add(eligibleMove);
            }
        } while (eligibleMove != null);
    }

    /** Removes and returns the first search from the move list which can now be added, or null if none */
    private Search removeFirstEntryWithFullyEmittedDependencies(List<Search> moveOutwards, List<Search> ordered) {
        for (Search move : moveOutwards) {
            if (allDependenciesAlreadyEmitted(ordered, move)) {
                moveOutwards.remove(move);
                return move;
            }
        }
        return null;
    }

    private boolean allDependenciesAlreadyEmitted(List<Search> alreadyOrdered, Search search) {
        if (search.getDocument() == null) {
            return true;
        }
        SDDocumentType document = search.getDocument();
        return allInheritedDependenciesEmitted(alreadyOrdered, document) && allReferenceDependenciesEmitted(alreadyOrdered, document);
    }

    private boolean allInheritedDependenciesEmitted(List<Search> alreadyOrdered, SDDocumentType document) {
        for (SDDocumentType sdoc : document.getInheritedTypes() ) {
            DataTypeName inheritedName = sdoc.getDocumentName();
            if ("document".equals(inheritedName.getName())) {
                continue;
            }
            Search inheritedSearch = documentNameToSearch.get(inheritedName);
            if (!alreadyOrdered.contains(inheritedSearch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allReferenceDependenciesEmitted(List<Search> alreadyOrdered, SDDocumentType document) {
        DocumentReferences documentReferences = document.getDocumentReferences()
                .orElseThrow(() -> new IllegalStateException("Missing document references. Should have been processed by now."));
        return documentReferences.stream()
                .map(Map.Entry::getValue)
                .map(DocumentReference::targetSearch)
                .allMatch(alreadyOrdered::contains);
    }

    private void indexOnDocumentName(List<Search> searches) {
        documentNameToSearch.clear();
        for (Search search : searches) {
            if (search.getDocument() != null) {
                documentNameToSearch.put(search.getDocument().getDocumentName(),search);
            }
        }
    }

}
