// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.collections.ListenableArrayList;
import com.yahoo.net.URI;
import com.yahoo.processing.response.ArrayDataList;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.response.DefaultIncomingData;
import com.yahoo.processing.response.IncomingData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * <p>A group of ordered hits. Since hitGroup is itself a kind of Hit,
 * this can compose hierarchies of grouped hits.
 *
 * <p>Group hits has a relevancy just as other hits - they can be ordered
 * between each other and in comparison to other hits.
 *
 * <p>Note that a group is by default a meta hit, but it can also contain its own content
 * in addition to subgroup content, in which case it should be set to non-meta.
 *
 * @author bratseth
 */
public class HitGroup extends Hit implements DataList<Hit>, Cloneable, Iterable<Hit> {

    // This does its own book-keeping of its various state variables
    // (see methods towards the end). For state variables which are recursive
    // (depending on the state of hits in subgroups), the strategy is to do
    // book-keeping on only this immediate level, but not do recursive calls to
    // find the true recursive state when queried. This is sort of a middle ground
    // between handling the complexity of recursive state book-keeping and the
    // query cost of not doing any book-keeping.
    // There is also a method, analyse which recursively updates the recursive
    // state of the group and all subgroups. This should be called if the hits
    // may have changed their own state in a way that may impact the recursive
    // state of this.

    private ListenableArrayList<Hit> hits = new ListenableArrayList<>(16);

    transient private List<Hit> unmodifiableHits = Collections.unmodifiableList(hits);

    /** Whether or not the hits are sorted */
    private boolean hitsSorted = true;

    /** Whether or not deletion of hits breaks the sorted ordering */
    private boolean deletionBreaksOrdering = false;

    /** Whether the hits should be sorted (again) */
    private boolean orderedHits = false;

    /** The current number of concrete (non-meta) hits in the result */
    private int concreteHitCount = 0;

    /** The class used to determine the ordering of the hits of this */
    transient private HitOrderer hitOrderer = null;

    /** Accounting the number of subgroups to allow some early returns when the number is 0 */
    private int subgroupCount = 0;

    /**
     * The number of hits not cached at this level, not counting hits in subgroups or
     * any nested hitgroups themselves
     */
    private int notCachedCount = 0;

    /**
     * A direct reference to the errors of this result, or null if there are no errors.
     * The error hit will also be listed in the set of this of this result
     */
    private DefaultErrorHit errorHit = null;

    private final ListenableFuture<DataList<Hit>> completedFuture;

    private final IncomingData<Hit> incomingHits;

    /** Creates an invalid group of hits. Id must be set before handoff. */
    public HitGroup() {
        incomingHits = new IncomingData.NullIncomingData<>(this);
        setRelevance(new Relevance(1));
        setMeta(true);
        completedFuture = new IncomingData.NullIncomingData.ImmediateFuture<>(this);
    }

    /**
     * Creates a hit group with max relevancy (1)
     *
     * @param id the id of this hit - any string, it is convenient to make this unique in the result containing this
     */
    public HitGroup(String id) {
        this(id,new Relevance(1));
    }

    /**
     * Creates a hit group
     *
     * @param id the id of this hit - any string, it is convenient to make this unique in the result containing this
     * @param relevance the relevance of this group of hits, preferably a number between 0 and 1
     */
    public HitGroup(String id, double relevance) {
        this(id,new Relevance(relevance));
    }

    /**
     * Creates a group hit
     *
     * @param id the id of this hit - any string, it is convenient to make this unique in the result containing this
     * @param relevance the relevancy of this group of hits
     */
    public HitGroup(String id, Relevance relevance) {
        super(id, relevance);
        this.incomingHits = new IncomingData.NullIncomingData<>(this);
        setMeta(true);
        completedFuture = new IncomingData.NullIncomingData.ImmediateFuture<>(this);
    }

    /**
     * Creates a group hit
     *
     * @param id the id of this hit - any string, it is convenient to make this unique in the result containing this
     * @param relevance the relevancy of this group of hits
     * @param incomingHits the incoming buffer to which new hits can be added asynchronously
     */
    protected HitGroup(String id, Relevance relevance, IncomingData<Hit> incomingHits) {
        super(id, relevance);
        this.incomingHits = incomingHits;
        setMeta(true);
        completedFuture = new ArrayDataList.DrainOnGetFuture<>(this);
    }

    /**
     * Creates a HitGroup which contains data which arrives in the future.
     *
     * @param id the id of this
     * @return a HitGroup which is incomplete and which has an {@link #incoming} where new hits can be added later
     */
    public static HitGroup createAsync(String id) {
        DefaultIncomingData<Hit> incomingData = new DefaultIncomingData<>();
        HitGroup hitGroup = new HitGroup(id, new Relevance(1), incomingData);
        incomingData.assignOwner(hitGroup);
        return hitGroup;
    }

    /** Calls setId(new URI(id)) */
    @Override
    public void setId(String id) {
        setId(new URI(id));
    }

    /**
     * Assign an id to this hit.
     * For HitGroups, this is a legal call also when an id is already set,
     * i.e hit groups allows their ids to be reassigned.
     * This is to allow hit groups to be inserted in new structures with an id reflecting their
     * role/placement in the structure.
     *
     * @param id the new or initial iof of this hit
     */
    @Override
    public void setId(URI id) {
        super.assignId(id);
    }

    /**
     * Turn off internal resorting of hits.
     *
     * @param ordered set to true to tell this group that the hits set in it is already correctly ordered and should
     *                never be resorted. Set to false to use the default lazy resorting by hit ordering.
     */
    public void setOrdered(boolean ordered) { this.orderedHits = ordered; }

    /**
     * Returns the number of hits available immediately in this group
     * (counting a subgroup as one hit).
     */
    public int size() {
        return hits.size();
    }

    /**
     * <p>Returns the number of concrete hits contained in this group
     * and all subgroups. This should equal the
     * requested hits count if the query has that many matches.</p>
     */
    public int getConcreteSize() {
        if (subgroupCount<1) return concreteHitCount;
        int recursiveConcreteCount=concreteHitCount;
        for (Hit hit : hits) {
            if (hit instanceof HitGroup)
                recursiveConcreteCount+=((HitGroup)hit).getConcreteSize();
        }
        return recursiveConcreteCount;
    }

    /**
     * <p>Returns the number of concrete hits contained in <i>this</i> group,
     * without counting hits in subgroups.
     */
    public int getConcreteSizeShallow() { return concreteHitCount; }

    /**
     * Returns the number of HitGroups present immediately in this list of hits.
     */
    public int getSubgroupCount() { return subgroupCount; }

    /**
     * Adds a hit to this group.
     * If the given hit is an ErrorHit and this group already have an error hit,
     * the errors in the given hit are merged into the errors of this.
     *
     * @return the resulting hit - this is usually the input hit, but if an error hit was added,
     *         and there was already an error hit present, that hit, containing the merged information
     *         is returned
     */
    @Override
    public Hit add(Hit hit) {
        if (hit.isMeta() && hit instanceof DefaultErrorHit) {
            if (errorHit != null) {
                errorHit.addErrors((DefaultErrorHit)hit);
                return errorHit; // don't add another error hit
            }
            else {
                errorHit = merge(consumeAnyQueryErrors(), (DefaultErrorHit) hit);
                hit = errorHit; // Add this hit below
            }
        }
        handleNewHit(hit);
        hits.add(hit);
        return hit;
    }

    /**
     * Adds a list of hits to this group, the same
     */
    public void addAll(List<Hit> hits) {
        for (Hit hit : hits)
            add(hit);
    }

    /**
     * Returns the hit at the given (0-base) index in this group of hit
     * (without searching any subgroups).
     *
     * @param  index the index into this list
     * @throws IndexOutOfBoundsException if there is no hit at the given index
     */
    public Hit get(int index) {
        updateHits();
        ensureSorted();
        return hits.get(index);
    }

    /** Same as {@link #get(String,int)} */
    public Hit get(String id) {
        return get(id,-1);
    }

    public Hit get(String id, int depth) {
        return get(new URI(id), depth);
    }

    /**
     * Returns the hit with the given id, or null if there is no hit with this id
     * in this group or any subgroup.
     * This method is o(min(number of nested hits in this result,depth)).
     *
     * @param id the id of the hit to return from this or any nested group
     * @param depth the max depth to recurse into nested groups: -1: Recurse infinitely deep, 0: Only look at hits in
     *        the list of this group, 1: Look at hits in this group, and the hits of any immediate nested HitGroups,
     *        etc.
     * @return The hit, or null if not found.
     */
    public Hit get(URI id, int depth) {
        updateHits();
        for (Iterator<Hit> i = unorderedIterator(); i.hasNext();) {
            Hit hit = i.next();
            URI hitUri = hit.getId();

            if (hitUri != null && hitUri.equals(id)) {
                return hit;
            }

            if (hit instanceof HitGroup && depth!=0) {
                Hit found=((HitGroup)hit).get(id,depth-1);
                if (found!=null) return found;
            }
        }
        return null;
    }

    /**
     * Inserts the given hit at the specified index in this group.
     */
    public void set(int index, Hit hit) {
        updateHits();
        if (hit instanceof ErrorHit) { // Merge instead
            add(hit);
            return;
        }

        handleNewHit(hit);
        Hit oldHit = hits.set(index, hit);

        if (oldHit!=null)
            handleRemovedHit(oldHit);
    }

    /**
     * Adds a hit to this group in the specified index,
     * all existing hits on this index and higher will have their index
     * increased by one.
     * <b>Note:</b> If the group was sorted, it will still be considered sorted
     * after this call.
     */
    public void add(int index, Hit hit) {
        if (hit instanceof ErrorHit) { // Merge instead
            add(hit);
            return;
        }

        boolean wasSorted = hitsSorted;
        handleNewHit(hit);
        hits.add(index, hit);
        hitsSorted = wasSorted;
    }

    /**
     * Removes a hit from this group or any subgroup
     *
     * @param uriString the uri of the hit to remove
     * @return the hit to remove, or null if the hit was not present
     */
    public Hit remove(String uriString) {
        return remove(new URI(uriString));
    }

    /**
     * Removes a hit from this group or any subgroup.
     *
     * @param uri the uri of the hit to remove.
     * @return the hit removed, or null if not found.
     */
    public Hit remove(URI uri) {
        for (Iterator<Hit> it = hits.iterator(); it.hasNext(); ) {
            Hit hit = it.next();
            if (uri.equals(hit.getId())) {
                it.remove();
                handleRemovedHit(hit);
                return hit;
            }
            else if (hit instanceof HitGroup) {
                Hit removed = ((HitGroup)hit).remove(uri);
                if (removed != null) {
                    return removed;
                }
            }
        }
        return null;
    }

    /**
     * Removes a hit from this group (not considering the hits of any subgroup)
     *
     * @param index the position of the hit to remove
     * @return the hit removed
     * @throws IndexOutOfBoundsException if there is no hit at the given position
     */
    public Hit remove(int index) {
        updateHits();
        Hit hit = hits.remove(index);
        handleRemovedHit(hit);

        return hit;
    }

    /** 
     * Sets the main error of this result
     * 
     * @deprecated prefer addError to add some error information.
     */
    // TODO: Remove on Vespa 7
    @Deprecated
    public void setError(ErrorMessage error) {
        addError(error);
    }

    /** Adds an error to this result */
    public void addError(ErrorMessage error) {
        getError(); // update the list of errors
        if (errorHit == null)
            add(new DefaultErrorHit(getSource(), error));
        else
            errorHit.addError(error);
    }

    /** Returns the error hit containing all error information, or null if no error has occurred */
    public ErrorHit getErrorHit() {
        getError(); // Make sure the error hit is updated
        return errorHit;
    }

    /** 
     * Removes the error hit of this.
     * This removes all error messages of this and the query producing it.
     *
     * @return the error hit which was removed, or null if there were no errors
     */
    public DefaultErrorHit removeErrorHit() {
        updateHits(); // Consume and remove from the query producing this as well
        DefaultErrorHit removed = errorHit;
        if (removed != null)
            remove(removed.getId());
        errorHit = null;
        return removed;
    }
    
    /**
     * Returns the first error in this result,
     * or null if no searcher has produced an error AND the query doesn't contain an error
     */
    public ErrorMessage getError() {
        updateHits();
        if (errorHit == null)
            return null;
        else
            return errorHit.errors().iterator().next();
    }

    /**
     * Combines two error hits to one. Any one argument may be null, in which case the other is returned.
     *
     * @return true if this should also be added to the list of hits of this result
     */
    private DefaultErrorHit merge(DefaultErrorHit first, DefaultErrorHit second) {
        if (first == null) return second;
        if (second == null) return first;

        String mergedSource = first.getSource()!=null ? first.getSource() : second.getSource();
        List<ErrorMessage> mergedErrors = new ArrayList<>();
        mergedErrors.addAll(first.errors());
        mergedErrors.addAll(second.errors());
        return new DefaultErrorHit(mergedSource, mergedErrors);
    }

    /**
     * Must be called before the list of hits, or anything dependent on the list of hits, is removed.
     * Consumes errors from the query if there is one set for this group
     */
    private void updateHits() {
        DefaultErrorHit queryErrors = consumeAnyQueryErrors();
        if (queryErrors != null)
            add(queryErrors);
    }

    /** 
     * Consumes errors from the query and returns them in a new error hit
     * 
     * @return the error hit containing all query errors, or null if no query errors should be consumed
     */
    private DefaultErrorHit consumeAnyQueryErrors() {
        if (errorHit != null) return null;
        if (getQuery() == null) return null;
        if (getQuery().errors().isEmpty()) return null;

        // Move errors from the query into this
        List<ErrorMessage> queryErrors = getQuery().errors().stream().map(this::toSearchError).collect(Collectors.toList());
        getQuery().errors().clear(); // TODO: Remove this line (not promised, can be done at any time)
        return new DefaultErrorHit(getSource(), queryErrors);
    }

    /** Compatibility */
    private ErrorMessage toSearchError(com.yahoo.processing.request.ErrorMessage error) {
        if (error instanceof ErrorMessage) 
            return (ErrorMessage)error;
        else 
            return new ErrorMessage(error.getCode(), error.getMessage(), error.getDetailedMessage(), error.getCause());
    }

    /**
     * Remove the first <code>offset</code> <i>concrete</i> hits in this group,
     * and hits beyond <code>offset+numHits</code>
     */
    public void trim(int offset, int numHits) {
        updateHits();
        ensureSorted();

        int highBound = numHits + offset; // Largest offset +1

        int currentIndex = -1;

        for (Iterator<Hit> i = hits.iterator(); i.hasNext();) {
            Hit hit = i.next();

            if (hit.isAuxiliary()) continue;

            currentIndex++;
            if (currentIndex < offset || currentIndex >= highBound) {
                i.remove();
                handleRemovedHit(hit);
            }
        }
    }

    /**
     * Returns an iterator of the hits in this group.
     * <p>
     * This iterator is modifiable - removals will take effect in this group of hits.
     */
    public Iterator<Hit> iterator() {
        updateHits();
        ensureSorted();
        return new HitIterator(this, hits);
    }

    /**
     * Returns an iterator that does depth-first traversal of leaf hits of this group. Calling this method has the
     * side-effect of sorting the internal list of hits.
     *
     * @return A modifiable iterator.
     */
    public Iterator<Hit> deepIterator() {
        return new DeepHitIterator(iterator(), true);
    }

    /**
     * Returns an iterator that does depth-first traversal of leaf hits of this group, in a potentially unsorted order.
     * As opposed to {@link #deepIterator()}, this method has no side-effect.
     *
     * @return A modifiable iterator.
     */
    public Iterator<Hit> unorderedDeepIterator() {
        return new DeepHitIterator(unorderedIterator(), false);
    }

    /** Returns a read only list view of the hits in this */
    public List<Hit> asList() {
        updateHits();
        ensureSorted();
        return unmodifiableHits;
    }

    /**
     * Returns a read only list view of the hits in this which is potentially unsorted.
     * Using this over getHits is potentially faster when a sorted view is not needed.
     */
    public List<Hit> asUnorderedHits() {
        updateHits();
        return unmodifiableHits;
    }

    /**
     * Returns an iterator of the hits in this group in a potentially unsorted order.
     * <p>
     * Using this over getPreludeHitIterator is potentially faster when a sorted view is not needed.
     * <p>
     * This iterator is modifiable - removals will take effect in this group of hits.
     */
    public Iterator<Hit> unorderedIterator() {
        updateHits();
        return new HitIterator(this, hits);
    }

    /**
     * Force hit sorting now.
     * This is not normally useful because a group will stay sorted automatically,
     * but it is in the case where
     * the hits have changed their internal state in a way that should change ordering
     */
    public void sort() {
        if (hitOrderer == null) {
            Collections.sort(hits);
            hitsSorted = true;
        } else {
            // This may or may not lead to a sorted result set, but it's a best effort
            hitOrderer.order(hits);
            if (likelyHitsHaveCorrectValueForSortFields()) {
                hitsSorted = true;
            }
        }
    }

    private boolean likelyHitsHaveCorrectValueForSortFields() {
        if (hitOrderer == null) {
            return true;
        } else {
            Set<String> filledFields = getFilled();
            return filledFields == null || !filledFields.isEmpty();
        }
    }

    /**
     * <p>Sets the hit orderer for this group.</p>
     *
     * @param hitOrderer the new hit orderer, or null to use default relevancy ordering
     */
    public void setOrderer(HitOrderer hitOrderer) {
        this.hitOrderer = hitOrderer;
        if (hits.size() > 1) {
            hitsSorted = false;
        }
    }

    /**
     * Explicitly set whether the hits in this group are correctly sorted at this moment.
     * If the contained hits are modified directly in a way that
     * may break ordering, you should call setSorted(false).
     */
    public void setSorted(boolean sorted) {
        this.hitsSorted = sorted;
    }


    /** Returns the orderer used by this group, or null if the default relevancy order is used */
    public HitOrderer getOrderer() {
        return hitOrderer;
    }

    public void setDeletionBreaksOrdering(boolean flag) { deletionBreaksOrdering = flag; }

    public boolean getDeletionBreaksOrdering() { return deletionBreaksOrdering; }

    /** Called before hit lists or positions are used */
    private void ensureSorted() {
        if ( ! orderedHits && ! hitsSorted && likelyHitsHaveCorrectValueForSortFields()) {
            sort();
        }
    }

    /**
     * Returns true if all the hits recursively contained in this
     * is cached
     */
    @Override
    public boolean isCached() {
        if (notCachedCount<1) return true;
        if (subgroupCount<1) return false; // No need to check below

        // Else check recursively
        for (Hit hit : hits) {
            if (hit instanceof HitGroup) {
                if (hit.isCached()) return true;
            }
        }
        return false;
    }

    /**
     * Returns whether all hits in this result have been filled with
     * the properties contained in the given summary class. Note that
     * this method will also return true if no hits in this result are
     * fillable.
     */
    public boolean isFilled(String summaryClass) {
        Set<String> filled = getFilled();
        return (filled == null || filled.contains(summaryClass));
    }


    /**
     * Sets sorting information to be the same as for the provided hitGroup.
     * The contained hits should already be sorted in the order specified by
     * the hitGroup given as argument.
     */
    public void copyOrdering(HitGroup hitGroup) {
        setOrderer(hitGroup.getOrderer());
        setDeletionBreaksOrdering(hitGroup.getDeletionBreaksOrdering());
        setOrdered(hitGroup.orderedHits);
    }

    // -------------- State bookkeeping

    /** Ensures result invariants. Must be called when a hit is added to this result. */
    @SuppressWarnings("deprecation")
    private void handleNewHit(Hit hit) {
        if (!hit.isAuxiliary())
            concreteHitCount++;

        if (hit.getAddNumber() < 0) {
            hit.setAddNumber(size());
        }

        hitsSorted = false;
        Set<String> hitFilled = hit.getFilled();

        if (hitFilled != null) {
            Set<String> filled = getFilledInternal();
            if (filled == null) {
                if (hitFilled.isEmpty()) {
                    filled = null;
                } else if (hitFilled.size() == 1) {
                    filled = Collections.singleton(hitFilled.iterator().next());
                } else {
                    filled = new HashSet<>(hitFilled);
                }
                setFilledInternal(filled);
            } else {
                if (filled.size() == 1) {
                    if ( ! hitFilled.contains(filled.iterator().next())) {
                        filled = null; // No intersection
                        setFilledInternal(filled);
                    }
                } else {
                    filled.retainAll(hitFilled);
                }
            }
        }

        if (hit instanceof HitGroup) {
            subgroupCount++;
        }
        if (!hit.isCached()) {
            notCachedCount++;
        }
    }

    // Filled is not kept in sync at removal
    private void handleRemovedHit(Hit hit) {
        if ( ! hit.isAuxiliary()) {
            concreteHitCount--;
            if ( ! hit.isCached())
                notCachedCount--;
        }
        else if (hit instanceof HitGroup) {
            subgroupCount--;
        }
        else if (hit instanceof DefaultErrorHit) {
            errorHit = null;
        }

        if (deletionBreaksOrdering) {
            hitsSorted = false;
        }
    }

    private void analyzeHit(Hit hit) {
        if (hit instanceof HitGroup) {
            ((HitGroup)hit).analyze();
        }
        if (!hit.isAuxiliary())
            concreteHitCount++;

        if (!hit.isCached())
            notCachedCount++;
    }

    /**
     * Update concreteHitCount, cached and filled by iterating trough the hits of this result.
     * Recursively also update all subgroups.
     */
    public void analyze() {
        concreteHitCount=0;
        setFilledInternal(null);
        notCachedCount=0;
        Set<String> filled = getFilledInternal();

        Iterator<Hit> i = unorderedIterator();
        while (filled == null && i.hasNext()) {
            Hit hit = i.next();
            analyzeHit(hit);
            Set<String> hitFilled = hit.getFilled();
            if (hitFilled != null) {
                filled = (hitFilled.size() == 1)
                        ? Collections.singleton(hitFilled.iterator().next())
                        : hitFilled.isEmpty() ? null : new HashSet<>(hitFilled);
                setFilledInternal(filled);
            }
        }
        String singleKey = null;
        if (filled != null && filled.size() == 1) {
            singleKey = filled.iterator().next();
        }


        for (; i.hasNext();) {
            Hit hit = i.next();
            analyzeHit(hit);

            if (filled != null) {
                Set<String> hitFilled = hit.getFilled();
                if (hitFilled == null) {
                    // Intentionally empty. Strange semantic, null -> matches everything
                } else if (hitFilled.isEmpty()) {
                    filled = null; // No intersection
                    setFilledInternal(filled);
                } else {
                    if (filled.size() == 1) {
                        if ( ! hitFilled.contains(singleKey)) {
                            filled = null; // No intersection
                            setFilledInternal(filled);
                            singleKey = null;
                        }
                    } else {
                        filled.retainAll(hitFilled);
                        if (filled.size() == 1) {
                            singleKey = filled.iterator().next();
                        }
                    }
                }
            }
        }
    }

    public HitGroup clone() {
        HitGroup hitGroupClone = (HitGroup) super.clone();
        hitGroupClone.hits = new ListenableArrayList<>(this.hits.size());
        hitGroupClone.unmodifiableHits = Collections.unmodifiableList(hitGroupClone.hits);
        for (Iterator<Hit> i = this.hits.iterator(); i.hasNext();) {
            Hit hitClone = i.next().clone();
            hitGroupClone.hits.add(hitClone);
        }
        if (this.errorHit!=null) { // Find the cloned error and assign it
            for (Hit hit : hitGroupClone.asList()) {
                if (hit instanceof DefaultErrorHit)
                    hitGroupClone.errorHit=(DefaultErrorHit)hit;
            }
        }

        if (this.getFilledInternal()!=null) {
            hitGroupClone.setFilledInternal(new HashSet<>(this.getFilledInternal()));
        }

        return hitGroupClone;
    }

    @Override
    public void setFillable() {}

    /** Ignored as this should always be derived from the content hits */
    @Override
    public void setFilled(String summaryClass) {}

    @Override
    public boolean isFillable() {
        return fillableHits().iterator().hasNext();
    }

    @Override
    public Set<String> getFilled() {
        Iterator<Hit> hitIterator = hits.iterator();
        Set<String> firstSummaryNames = getSummaryNamesNextFilledHit(hitIterator);
        if (firstSummaryNames == null || firstSummaryNames.isEmpty())
            return firstSummaryNames;

        Set<String> intersection = firstSummaryNames;
        while (true) {
            Set<String> summaryNames = getSummaryNamesNextFilledHit(hitIterator);
            if (summaryNames == null)
                break;

            if (intersection.size() == 1)
                return getFilledSingle(first(intersection), hitIterator);


            boolean notInSet = false;
            if (intersection == firstSummaryNames) {
                if (intersection.size() == summaryNames.size()) {
                    for(String s : summaryNames) {
                        if ( ! intersection.contains(s)) {
                            intersection = new HashSet<>(firstSummaryNames);
                            notInSet = true;
                            break;
                        }
                    }
                }
            }
            if (notInSet) {
                intersection.retainAll(summaryNames);
            }

        }

        return intersection;
    }

    private Set<String> getSummaryNamesNextFilledHit(Iterator<Hit> hitIterator) {
        while (hitIterator.hasNext()) {
            Set<String> filled = hitIterator.next().getFilled();
            if (filled != null)
                return filled;
        }
        return null;
    }

    private Set<String> getFilledSingle(String summaryName, Iterator<Hit> hitIterator) {
        while (true) {
            Set<String> summaryNames = getSummaryNamesNextFilledHit(hitIterator);
            if (summaryNames == null) {
                return Collections.singleton(summaryName);
            } else if (!summaryNames.contains(summaryName)) {
                return Collections.emptySet();
            }
        }
    }

    private Iterable<Hit> fillableHits() {
        Predicate<Hit> isFillable = hit -> hit.isFillable();

        return Iterables.filter(hits, isFillable);
    }

    /** Returns the incoming hit buffer to which new hits can be added to this asynchronous, if supported by the instance */
    @Override
    public IncomingData<Hit> incoming() { return incomingHits; }

    @Override
    public ListenableFuture<DataList<Hit>> complete() { return completedFuture; }

    @Override
    public void addDataListener(Runnable runnable) {
        hits.addListener(runnable);
    }

    @Override
    public void close() {
        super.close();
        hits = null;
        unmodifiableHits = null;
        hitOrderer = null;
        incomingHits.drain(); // Just to gc as much as possible
    }

}
