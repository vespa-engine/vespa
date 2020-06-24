// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.systemstate.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class Location {

    private List<String> items = new ArrayList<String>();

    /**
     * Constructs a new location with no items.
     */
    public Location() {
        // empty
    }

    /**
     * Constructs a new location based on a location string.
     *
     * @param loc The location string to parse.
     */
    public Location(String loc) {
        items.addAll(Arrays.asList(loc.split("/")));
        normalize();
    }

    /**
     * Constructs a new location based on a list of items.
     *
     * @param items The components that make up this location.
     */
    public Location(List<String> items) {
        this.items.addAll(items);
        normalize();
    }

    /**
     * Constructs a new location as a copy of another.
     *
     * @param loc The location to copy.
     */
    public Location(Location loc) {
        items.addAll(loc.items);
    }

    /**
     * Constructs a new location based on a working directory and a list of items.
     *
     * @param pwd The path of the working directory.
     * @param items The components that make up this location.
     */
    public Location(Location pwd, List<String> items) {
        this.items.addAll(pwd.getItems());
        this.items.addAll(items);
        normalize();
    }

    /**
     * Returns a location object that represents the "next" step along this location path. This means removing the first
     * elements of this location's items and returning a new location for this sublist.
     *
     * @return The next location along this path.
     */
    public Location getNext() {
        List<String> next = new ArrayList<String>(items);
        next.remove(0);
        return new Location(next);
    }

    /**
     * Returns the components of this location.
     *
     * @return The component array.
     */
    public List<String> getItems() {
        return items;
    }

    /**
     * Normalizes the items list of this location so that all PREV or THIS locations are replaced by their actual
     * meaning. This carries some overhead since it is not done in place.
     *
     * @return This, to allow chaining.
     */
    private Location normalize() {
        List<String> norm = new ArrayList<String>();
        for (String item : items) {
            if (item.equals(NodeState.NODE_PARENT)) {
                if (norm.size() == 0) {
                    // ignore
                }
                else {
                    norm.remove(norm.size() - 1);
                }
            }
            else if (!item.equals(NodeState.NODE_CURRENT)) {
                norm.add(item);
            }
        }
        items = norm;
        return this;
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer();
        for (int i = 0; i < items.size(); ++i) {
            ret.append(items.get(i));
            if (i < items.size() - 1) {
                ret.append("/");
            }
        }
        return ret.toString();
    }
}
