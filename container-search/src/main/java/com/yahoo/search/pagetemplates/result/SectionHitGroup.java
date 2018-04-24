// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.result;

import com.yahoo.search.pagetemplates.model.Renderer;
import com.yahoo.search.pagetemplates.model.Source;
import com.yahoo.search.result.HitGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * A hit group corresponding to a section - contains some additional information
 * in proper getters and setters which is used during rendering.
 *
 * @author bratseth
 */
public class SectionHitGroup extends HitGroup {

    private static final long serialVersionUID = -9048845836777953538L;
    private List<Source> sources = new ArrayList<>(0);
    private List<Renderer> renderers = new ArrayList<>(0);
    private final String displayId;

    private boolean leaf=false;

    public SectionHitGroup(String id) {
        super(id);
        if (id.startsWith("section:section_"))
            displayId=null; // Don't display section ids when not named explicitly
        else
            displayId=id;
        types().add("section");
    }

    @Override
    public String getDisplayId() { return displayId; }

    /**
     * Returns the live, modifiable list of sources which are not fetched by the framework but should
     * instead be included in the result
     */
    public List<Source> sources() { return sources; }

    /** Returns the live, modifiable list of renderers in this section */
    public List<Renderer> renderers() { return renderers; }

    /** Returns whether this is a leaf section containing no subsections */
    public boolean isLeaf() { return leaf; }

    public void setLeaf(boolean leaf) { this.leaf=leaf; }

    @Override
    public void close() {
        sources = null;
        renderers = null;
    }

}
