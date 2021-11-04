// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This abstract class represents the id of a single group in the grouping result model. A subclass corresponding to the
 * evaluation result of generating {@link com.yahoo.search.grouping.request.GroupingExpression} is contained in all
 * {@link Group} objects. It is used by {@link com.yahoo.search.grouping.GroupingRequest} to identify its root result
 * group, and by all client code for identifying groups.
 * <p>
 * The {@link #toString()} method of this class generates a URI-compatible string on the form
 * "group:&lt;typeName&gt;:&lt;subclassSpecific&gt;".
 *
 * @author Simon Thoresen Hult
 */
public abstract class GroupId {

    private final String type;
    private final String image;

    protected GroupId(String type, Object... args) {
        this.type = type;

        StringBuilder image = new StringBuilder("group:");
        image.append(type);
        for (Object arg : args) {
            image.append(":").append(arg);
        }
        this.image = image.toString();
    }

    /** Returns the type name of this group id. This is the second part of the {@link #toString()} value of this. */
    public String getTypeName() {
        return type;
    }

    @Override
    public String toString() {
        return image;
    }

}
