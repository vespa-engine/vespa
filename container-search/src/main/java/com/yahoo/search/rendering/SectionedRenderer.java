// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.search.Result;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * Renders each part of a result to a writer.
 * The renderers are cloned just before rendering,
 * and must therefore obey the following contract:
 * <ol>
 *   <li>At construction time, only final members shall be initialized,
 *       and these must refer to immutable data only.
 *   <li>State mutated during rendering shall be initialized in the init method.
 * </ol>
 *
 * @author Tony Vaagenes
 */
abstract public class SectionedRenderer<WRITER> extends Renderer {
    /**
     * Wraps the Writer instance.
     * The result is given as a parameter to all the callback methods.
     * Must be overridden if the generic parameter WRITER != java.io.Writer.
     */
    @SuppressWarnings("unchecked")
    public WRITER wrapWriter(Writer writer) {
        return (WRITER)writer;
    }

    /**
     * Called at the start of rendering.
     */
    abstract public void beginResult(WRITER writer, Result result) throws IOException;

    /**
     * Called at the end of rendering.
     */
    abstract public void endResult(WRITER writer, Result result) throws IOException;

    /**
     * Called if there are errors in the result.
     */
    abstract public void error(WRITER writer, Collection<ErrorMessage> errorMessages) throws IOException;

    /**
     * Called if there are no hits in the result.
     */
    abstract public void emptyResult(WRITER writer, Result result) throws IOException;

    /**
     * Called if there is a non-null query context for the query of the result.
     */
    abstract public void queryContext(WRITER writer, QueryContext queryContext) throws IOException;

    /**
     * Called when a HitGroup is encountered. After all its children have been provided
     * to methods of this class, endHitGroup is called.
     */
    abstract public void beginHitGroup(WRITER writer, HitGroup hitGroup) throws IOException;

    /**
     * Called after all the children of the HitGroup have been provided to methods of this class.
     * See beginHitGroup.
     */
    abstract public void endHitGroup(WRITER writer, HitGroup hitGroup) throws IOException;

    /**
     * Called when a Hit is encountered.
     */
    abstract public void hit(WRITER writer, Hit hit) throws IOException;

    /**
     * Called when an errorHit is encountered.
     * Forwards to hit() per default.
     */
    public void errorHit(WRITER writer, ErrorHit errorHit) throws IOException {
        hit(writer, (Hit)errorHit);
    }

    /* Begin Grouping */

    /**
     * Same as beginHitGroup, but for Group(grouping api).
     * Forwards to beginHitGroup() per default.
     */
    public void beginGroup(WRITER writer, Group group) throws IOException {
        beginHitGroup(writer, group);
    }

    /**
     * Same as endHitGroup, but for Group(grouping api).
     * Forwards to endHitGroup() per default.
     */
    public void endGroup(WRITER writer, Group group) throws IOException {
        endHitGroup(writer, group);
    }

    /**
     * Same as beginHitGroup, but for GroupList(grouping api).
     * Forwards to beginHitGroup() per default.
     */
    public void beginGroupList(WRITER writer, GroupList groupList) throws IOException {
        beginHitGroup(writer, groupList);
    }

    /**
     * Same as endHitGroup, but for GroupList(grouping api).
     * Forwards to endHitGroup() per default.
     */
    public void endGroupList(WRITER writer, GroupList groupList) throws IOException {
        endHitGroup(writer, groupList);
    }

    /**
     * Same as beginHitGroup, but for HitList(grouping api).
     * Forwards to beginHitGroup() per default.
     */
    public void beginHitList(WRITER writer, HitList hitList) throws IOException {
        beginHitGroup(writer, hitList);
    }

    /**
     * Same as endHitGroup, but for HitList(grouping api).
     * Forwards to endHitGroup() per default.
     */
    public void endHitList(WRITER writer, HitList hitList) throws IOException {
        endHitGroup(writer, hitList);
    }
    /* End Grouping */

    /**
     * Picks apart the result and feeds it to the other methods.
     */
    @Override
    public final void render(Writer writer, Result result) throws IOException {
        WRITER wrappedWriter = wrapWriter(writer);

        beginResult(wrappedWriter, result);
        renderResultContent(wrappedWriter, result);
        endResult(wrappedWriter, result);
    }

    private  void renderResultContent(WRITER writer, Result result) throws IOException {
        if (result.hits().getError() != null || result.hits().getQuery().errors().size() > 0) {
            error(writer, asUnmodifiableSearchErrorList(result.hits().getQuery().errors(), result.hits().getError()));
        }

        if (result.getConcreteHitCount() == 0) {
            emptyResult(writer, result);
        }

        if (result.getContext(false) != null) {
            queryContext(writer, result.getContext(false));
        }

        renderHitGroup(writer, result.hits());
    }

    private Collection<ErrorMessage> asUnmodifiableSearchErrorList(List<com.yahoo.processing.request.ErrorMessage> queryErrors,ErrorMessage resultError) {
        if (queryErrors.size() == 0)
            return Collections.singletonList(resultError);
        List<ErrorMessage> searchErrors = new ArrayList<>(queryErrors.size() + (resultError != null ? 1 :0) );
        for (int i=0; i<queryErrors.size(); i++)
            searchErrors.add(ErrorMessage.from(queryErrors.get(i)));
        if (resultError != null)
            searchErrors.add(resultError);
        return Collections.unmodifiableCollection(searchErrors);
    }

    private void renderHitGroup(WRITER writer, HitGroup hitGroup) throws IOException {
        if (hitGroup instanceof GroupList) {
            beginGroupList(writer, (GroupList) hitGroup);
            renderHitGroupContent(writer, hitGroup);
            endGroupList(writer, (GroupList) hitGroup);
        } else if (hitGroup instanceof HitList) {
            beginHitList(writer, (HitList) hitGroup);
            renderHitGroupContent(writer, hitGroup);
            endHitList(writer, (HitList) hitGroup);
        } else if (hitGroup instanceof Group) {
            beginGroup(writer, (Group) hitGroup);
            renderHitGroupContent(writer, hitGroup);
            endGroup(writer, (Group) hitGroup);
        } else {
            beginHitGroup(writer, hitGroup);
            renderHitGroupContent(writer, hitGroup);
            endHitGroup(writer, hitGroup);
        }
    }

    private void renderHitGroupContent(WRITER writer, HitGroup hitGroup) throws IOException {
        for (Hit hit : hitGroup.asList()) {
            renderHit(writer, hit);
        }
    }

    private void renderHit(WRITER writer, Hit hit) throws IOException {
        if (hit instanceof  HitGroup) {
            renderHitGroup(writer, (HitGroup) hit);
        } else if (hit instanceof ErrorHit) {
            errorHit(writer, (ErrorHit) hit);
        } else {
            hit(writer, hit);
        }
    }
}
