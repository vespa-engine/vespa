// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.component.provider.FreezableClass;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A HashMap wrapper which can be cloned without copying the wrapped map.
 * Copying of the map is deferred until there is a write access to the wrapped map.
 * This may be frozen, at which point no further modifications are allowed.
 * Note that <b>until</b> this is cloned, the internal map may be both read and written.
 *
 * @author bratseth
 */
public class CopyOnWriteContent extends FreezableClass implements Cloneable {

    // TODO: Now that we used CompiledQueryProfiles at runtime we can remove this

    // Possible states:
    // WRITABLE:    The map can be freely modified - it is only used by this
    //              -> !isFrozen() && (map!=null || unmodifiableMap==null)
    // COPYONWRITE: The map is referred by at least one clone - further modification must cause a copy
    //              -> !isFrozen() && (map==null && unmodifiableMap!=null)
    // FROZEN:      No further changes are allowed to the state of this, ever
    //              -> isFrozen()

    // Possible start states:
    // WRITABLE:    When created using the public constructor
    // COPYONWRITE: When created by cloning

    // Possible state transitions:
    // WRITABLE->COPYONWRITE:          When this is cloned
    // COPYONWRITE->WRITABLE:          When a clone is written to
    // (COPYONWRITE,WRITABLE)->FROZEN: When a profile is frozen

    /** The modifiable content of this. Null if this is empty or if this is not in the WRITABLE state */
    private Map<String ,Object> map=null;
    /**
     * If map is non-null this is either null (not instantiated yet) or an unmodifiable wrapper of map,
     * if map is null this is either null (this is empty) or a reference to the map of the content this was cloned from
     */
    private Map<String, Object> unmodifiableMap =null;

    /** Create a WRITABLE, empty instance */
    public CopyOnWriteContent() {
    }

    /** Create a COPYONWRITE instance with some initial state */
    private static CopyOnWriteContent createInCopyOnWriteState(Map<String,Object> unmodifiableMap) {
        CopyOnWriteContent content=new CopyOnWriteContent();
        content.unmodifiableMap = unmodifiableMap;
        return content;
    }

    /** Create a WRITABLE instance with some initial state */
    private static CopyOnWriteContent createInWritableState(Map<String,Object> map) {
        CopyOnWriteContent content=new CopyOnWriteContent();
        content.map = map;
        return content;
    }

    @Override
    public void freeze() {
        // Freeze this
        if (unmodifiableMap==null)
            unmodifiableMap= map!=null ? Collections.unmodifiableMap(map) : Collections.<String, Object>emptyMap();
        map=null; // just to keep the states simpler

        // Freeze content
        for (Map.Entry<String,Object> entry : unmodifiableMap.entrySet()) {
            if (entry.getValue() instanceof QueryProfile)
                ((QueryProfile)entry.getValue()).freeze();
        }
        super.freeze();
    }

    private boolean isEmpty() {
        return (map==null || map.isEmpty()) && (unmodifiableMap ==null || unmodifiableMap.isEmpty());
    }

    private boolean isWritable() {
        return !isFrozen() && (map!=null || unmodifiableMap==null);
    }

    @Override
    public CopyOnWriteContent clone() {
        if (isEmpty()) return new CopyOnWriteContent(); // No referencing is necessary in this case
        if (isDeepUnmodifiable(unmodifiableMap())) {
            // Create an instance pointing to this and put both in the COPYONWRITE state
            unmodifiableMap(); // Make sure we have an unmodifiable reference to the map below
            map=null; // Put this into the COPYONWRITE state (unless it is already frozen, in which case this is a noop)
            return createInCopyOnWriteState(unmodifiableMap());
        }
        else {
            // This contains query profiles, don't try to defer copying
            return createInWritableState(deepClone(map));
        }
    }

    private boolean isDeepUnmodifiable(Map<String,Object> map) {
        for (Object value : map.values())
            if (value instanceof QueryProfile && !((QueryProfile)value).isFrozen()) return false;
        return true; // all other values are primitives
    }

    /** Deep clones a map - this handles all value types which can be found in a query profile */
    static Map<String,Object> deepClone(Map<String,Object> map) {
        if (map==null) return null;
        Map<String,Object> mapClone=new HashMap<>(map.size());
        for (Map.Entry<String,Object> entry : map.entrySet())
            mapClone.put(entry.getKey(),QueryProfile.cloneIfNecessary(entry.getValue()));
        return mapClone;
    }


    //------- Content access -------------------------------------------------------

    public Map<String,Object> unmodifiableMap() {
        if (isEmpty()) return Collections.emptyMap();
        if (map==null) // in COPYONWRITE or FROZEN state
            return unmodifiableMap;
        // In WRITABLE state: Create unmodifiable wrapper if necessary and return it
        if (unmodifiableMap==null)
            unmodifiableMap=Collections.unmodifiableMap(map);
        return unmodifiableMap;
    }

    public Object get(String key) {
        if (map!=null) return map.get(key);
        if (unmodifiableMap!=null) return unmodifiableMap.get(key);
        return null;
    }

    public void put(String key,Object value) {
        ensureNotFrozen();
        copyIfNotWritable();
        if (map==null)
            map=new HashMap<>();
        map.put(key,value);
    }

    public void remove(String key) {
        ensureNotFrozen();
        copyIfNotWritable();
        if (map!=null)
            map.remove(key);
    }

    private void copyIfNotWritable() {
        if (isWritable()) return;
        // move from COPYONWRITE to WRITABLE state
        map=new HashMap<>(unmodifiableMap); // deep clone is not necessary as this map is shallowly modifiable
        unmodifiableMap=null; // will be created as needed
    }

}
