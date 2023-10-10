// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.PageTemplateVisitor;

/**
 * Superclass of page template choice resolvers.
 * <p>
 * Subclasses overrides one of the two resolve methods to either resolve each choices individually
 * or look at all choices at once.
 * <p>
 * All subclasses of this must be multithread safe. I.e multiple calls may be made
 * to resolve at the same time from different threads.
 *
 * @author bratseth
 */
public abstract class Resolver extends AbstractComponent {

    public Resolver(String id) {
        super(new ComponentId(id));
    }

    public Resolver(ComponentId id) {
        super(id);
    }

    protected Resolver() {}

    /**
     * Override this to resolve choices. Before retuning this method <i>must</i> resolve the given choice
     * between a set of page templates <i>and</i> all choices found recursively within the <i>chosen</i>
     * page template. It is permissible but not required to add solutions also to choices present within those
     * templates which are not chosen.
     * <p>
     * This default implementation creates a Resolution and calls
     * <code>resolve(choice/mapChoice,query,result,resolution)</code> first on the given page template choice, then
     * on each choice found in that temnplate. This provides a simple API to resolvers which make each choice
     * independently.
     *
     * @param pageTemplate the choice of page templates to resolve - a choice containing singleton lists of PageTemplate elements
     * @param query the query, from which information useful for correct resolution can be found
     * @param result the result, from which further information useful for correct resolution can be found
     * @return the resolution of the choices contained in the given page template
     */
    public Resolution resolve(Choice pageTemplate, Query query, Result result) {
        Resolution resolution=new Resolution();
        resolve(pageTemplate,query,result,resolution);
        PageTemplate chosenPageTemplate=(PageTemplate)pageTemplate.get(resolution.getResolution(pageTemplate)).get(0);
        ChoiceResolverVisitor choiceResolverVisitor=new ChoiceResolverVisitor(query,result,resolution);
        chosenPageTemplate.accept(choiceResolverVisitor);
        return choiceResolverVisitor.getResolution();
    }

    /**
     * Override this to resolve <i>each</i> choice independently.
     * This default implementation does nothing.
     *
     * @param choice the choice to resolve
     * @param query the query for which this should be resolved, typically used to extract features
     * @param result the result for which this should be resolved, typically used to extract features
     * @param resolution the set of resolutions made so far, to which this should be added:
     *        <code>resolution.addChoiceResolution(choice,chosenAlternativeIndex)</code>
     */
    public void resolve(Choice choice,Query query,Result result,Resolution resolution) {
    }

    /**
     * Override this to resolve <i>each</i> map choice independently.
     * This default implementation does nothing.
     *
     * @param choice the choice to resolve
     * @param query the query for which this should be resolved, typically used to extract features
     * @param result the result for which this should be resolved, typically used to extract features
     * @param resolution the set of resolutions made so far, to which this should be added:
     *        <code>resolution.addMapChoiceResolution(choice,chosenMapping)</code>
     */
    public void resolve(MapChoice choice,Query query,Result result,Resolution resolution) {
    }

    private class ChoiceResolverVisitor extends PageTemplateVisitor {

        private Resolution resolution;

        private Query query;

        private Result result;

        public ChoiceResolverVisitor(Query query,Result result,Resolution resolution) {
            this.query=query;
            this.result=result;
            this.resolution=resolution;
        }

        @Override
        public void visit(Choice choice) {
            if (choice.alternatives().size()<2) return; // No choice...
            resolve(choice,query,result,resolution);
        }

        @Override
        public void visit(MapChoice choice) {
            resolve(choice,query,result,resolution);
        }

        public Resolution getResolution() { return resolution; }

    }

}
