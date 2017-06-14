// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * An interface used for anything which may be addressed using an external,
 * unique ID in the query tree in the backend.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public interface TaggableItem {

    public int getUniqueID();
    public void setUniqueID(int id);
    public boolean hasUniqueID();

    /**
     * Set the connectivity to another term in the same query tree.
     * This is used to influence ranking features taking proximity into account: nativeRank and a subset of the
     * fieldMatch features.
     * <p>
     * By default consecutive query terms are 'somewhat' connected, meaning ranking features will be better in documents
     * where the terms are found close to each other. This effect can be increased or decreased by manipulating the
     * connectivity value. Typical use is to increase the connectivity between terms in the query that we believe are
     * semantically connected. E.g in the query 'new york hotel', it is a good idea to increase the connectivity between
     * "new" and "york" to ensure that a document containing "List of hotels in New York" is ranked above one containing
     * "List of new hotels in York".
     *
     * @param item the item this should be connected to - in practice the next consecutive item in the query
     * @param connectivity a value between 0 (none) and 1 (maximal), defining the connectivity between this and the
     *        argument item. The default connectivity is 0.1.
     */
    public void setConnectivity(Item item, double connectivity);
    public Item getConnectedItem();
    public double getConnectivity();


    /**
     * Used for setting explicit term significance (in the tf/idf sense) to a single term or phrase,
     * relative to the rest of the query.
     * This influences ranking features which take term significance into account and overrides the default
     * partial corpus based term significance computation happening in the backend.
     */
    public void setSignificance(double significance);
    public boolean hasExplicitSignificance();
    public void setExplicitSignificance(boolean significance);
    public double getSignificance();
}
