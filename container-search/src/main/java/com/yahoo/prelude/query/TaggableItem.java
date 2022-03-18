// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * An interface used for anything which may be addressed using an external,
 * unique ID in the query tree in the backend.
 *
 * @author Steinar Knutsen
 */
public interface TaggableItem {

    int getUniqueID();
    void setUniqueID(int id);
    boolean hasUniqueID();

    /**
     * Set the connectivity to another term in the same query tree.
     * This is used to influence ranking features taking proximity into account: nativeRank and a subset of the
     * fieldMatch features.
     * <p>
     * By default consecutive query terms are 'somewhat' connected, meaning ranking features will score higher in documents
     * where the terms are found close to each other. This effect can be increased or decreased by manipulating the
     * connectivity value. Typical use is to increase the connectivity between terms in the query that we believe are
     * semantically connected. E.g., in the query 'new york hotel', it is a good idea to increase the connectivity between
     * "new" and "york" to ensure that a document containing "List of hotels in New York" is ranked above one containing
     * "List of new hotels in York".
     *
     * @param item the item this should be connected to - in practice the previous item in the query.
     * @param connectivity a value between 0 (none) and 1 (maximal), defining the connectivity between this and the
     *        argument item. The default connectivity is 0.1.
     */
    void setConnectivity(Item item, double connectivity);
    Item getConnectedItem();
    double getConnectivity();

    /**
     * Used for setting explicit term significance (in the tf/idf sense) to a single term or phrase,
     * relative to the rest of the query.
     * This influences ranking features which take term significance into account, and overrides the default
     * partial corpus based term significance computation in the backend.
     */
    void setSignificance(double significance);
    boolean hasExplicitSignificance();
    void setExplicitSignificance(boolean significance);
    double getSignificance();

}
