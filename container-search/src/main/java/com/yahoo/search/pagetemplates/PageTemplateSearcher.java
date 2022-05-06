// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.intent.model.IntentModel;
import com.yahoo.search.pagetemplates.config.PageTemplateConfigurer;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.engine.resolvers.ResolverRegistry;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.PageElement;
import com.yahoo.search.pagetemplates.model.Source;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Enables page optimization templates.
 * This searcher should be placed before federation points in the search chain.
 * <p>
 * <b>Input query properties:</b>
 * <ul>
 * <li><code>page.idList</code> - a List&lt;String&gt; of id strings of the page templates this should choose between</li>
 * <li><code>page.id</code> - a space-separated string of ids of the page templates this should choose between.
 *     This property is ignored if <code>page.idList</code> is set</li>
 * <li><code>page.resolver</code> the id of the resolver to use to resolve choices. This is either the component id
 *     of a deployed resolver component, or one of the strings
 *     <code>native.deterministic</code> (which always pics the last choice) or <code>native.random</code></li>
 * </ul>
 *
 * <b>Output query properties:</b>
 * <ul>
 * <li><code>page.ListOfPageTemplate</code>A List&lt;PageTemplate&gt;
 * containing a list of the page templates used for this query
 * </ul>
 *
 * <p>
 * The set of page templates chosen for the query specifies a list of sources to be queried (the page template sources).
 * In addition, the query may contain
 * <ul>
 *   <li>a set of sources set explicitly in the Request, a query property or a searcher (the query model sources)
 *   <li>a set of sources specified in the {@link com.yahoo.search.intent.model.IntentModel} (the intent model sources)
 * </ul>
 * This searcher combines these sources into a single set in query.model by the following rules:
 * <ul>
 *   <li>If the query model sources is set (not empty), it is not changed
 *   <li>If the page template sources contains the ANY source AND there is an intent model
 *       the query model sources is set to the union of the page template sources and the intent model sources
 *   <li>If the page template sources contains the ANY source AND there is no intent model,
 *       the query model sources is left empty (causing all sources to be queried)
 *   <li>Otherwise, the query model sources is set to the page template sources
 * </ul>
 *
 * @author bratseth
 */
@Provides("PageTemplates")
public class PageTemplateSearcher extends Searcher {

    /** The name of the query property containing the resolved candidate page template list */
    public static final CompoundName pagePageTemplateListName=new CompoundName("page.PageTemplateList");
    /** The name of the query property containing a list of candidate pages to consider */
    public static final CompoundName pageIdListName=new CompoundName("page.idList");
    /** The name of the query property containing the page id to use */
    public static final CompoundName pageIdName=new CompoundName("page.id");
    /** The name of the query property containing the resolver id to use */
    public static final CompoundName pageResolverName=new CompoundName("page.resolver");

    private final ResolverRegistry resolverRegistry;

    private final Organizer organizer = new Organizer();

    private final PageTemplateRegistry templateRegistry;

    /** Creates this from a configuration. This will be called by the container. */
    @Inject
    public PageTemplateSearcher(PageTemplatesConfig pageTemplatesConfig, ComponentRegistry<Resolver> resolverRegistry) {
        this(PageTemplateConfigurer.toRegistry(pageTemplatesConfig), resolverRegistry.allComponents());
    }

    /**
     * Creates this from an existing page template registry, using only built-in resolvers
     *
     * @param templateRegistry the page template registry. This will be frozen by this call.
     * @param resolvers the resolvers to use, in addition to the default resolvers
     */
    public PageTemplateSearcher(PageTemplateRegistry templateRegistry, Resolver... resolvers) {
        this(templateRegistry, Arrays.asList(resolvers));
    }

    private PageTemplateSearcher(PageTemplateRegistry templateRegistry, List<Resolver> resolvers) {
        this.templateRegistry = templateRegistry;
        templateRegistry.freeze();
        this.resolverRegistry = new ResolverRegistry(resolvers);
    }

    @Override
    public Result search(Query query, Execution execution) {
        // Pre execution: Choose template and sources
        List<PageElement> pages = selectPageTemplates(query);
        if (pages.isEmpty()) return execution.search(query); // Bypass if no page template chosen
        addSources(pages,query);

        // Set the page template list for inspection by other searchers
        query.properties().set(pagePageTemplateListName, pages);

        // Execute
        Result result = execution.search(query);

        // Post execution: Resolve choices and organize the result as dictated by the resolved template
        Choice pageTemplateChoice = Choice.createSingletons(pages);
        Resolution resolution = selectResolver(query).resolve(pageTemplateChoice, query, result);
        organizer.organize(pageTemplateChoice, resolution, result);
        return result;
    }

    /**
     * Returns the list of page templates specified in the query, or the default if none, or the
     * empty list if no default, never null.
     */
    private List<PageElement> selectPageTemplates(Query query) {
        // Determine the list of page template ids
        @SuppressWarnings("unchecked")
        List<String> pageIds = (List<String>) query.properties().get(pageIdListName);
        if (pageIds == null) {
            String pageIdString = query.properties().getString(pageIdName,"").trim();
            if (pageIdString.length() > 0)
                pageIds = Arrays.asList(pageIdString.split(" "));
        }

        // If none set, just return the default or null if none
        if (pageIds == null) {
            PageElement defaultPage=templateRegistry.getComponent("default");
            return (defaultPage == null ? Collections.<PageElement>emptyList() : Collections.singletonList(defaultPage));
        }

        // Resolve the id list to page templates
        List<PageElement> pages = new ArrayList<>(pageIds.size());
        for (String pageId : pageIds) {
            PageTemplate page = templateRegistry.getComponent(pageId);
            if (page == null)
                query.errors().add(ErrorMessage.createInvalidQueryParameter("Could not resolve requested page template '" +
                                                                            pageId + "'"));
            else
                pages.add(page);
        }

        return pages;
    }

    private Resolver selectResolver(Query query) {
        String resolverId = query.properties().getString(pageResolverName);
        if (resolverId == null) return resolverRegistry.defaultResolver();
        Resolver resolver = resolverRegistry.getComponent(resolverId);
        if (resolver == null) throw new IllegalInputException("No page template resolver '" + resolverId + "'");
        return resolver;
    }

    /** Sets query.getModel().getSources() to the right value and add source parameters specified in templates */
    private void addSources(List<PageElement> pages, Query query) {
        // Determine all wanted sources
        Set<Source> pageSources = new HashSet<>();
        for (PageElement page : pages)
            pageSources.addAll(((PageTemplate)page).getSources());

        addErrorIfSameSourceMultipleTimes(pages,pageSources,query);

        if (query.getModel().getSources().size() > 0) {
            // Add properties if the source list is set explicitly, but do not modify otherwise
            addParametersForIncludedSources(pageSources, query);
            return;
        }

        if (pageSources.contains(Source.any)) {
            IntentModel intentModel = IntentModel.getFrom(query);
            if (intentModel != null) {
                query.getModel().getSources().addAll(intentModel.getSourceNames());
                addPageTemplateSources(pageSources, query);
            }
            // otherwise leave empty to search all
        }
        else { // Let the page templates decide
            addPageTemplateSources(pageSources, query);
        }
    }

    private void addPageTemplateSources(Set<Source> pageSources,Query query) {
        for (Source pageSource : pageSources) {
            if (pageSource == Source.any) continue;
            query.getModel().getSources().add(pageSource.getName());
            addParameters(pageSource,query);
        }
    }

    private void addParametersForIncludedSources(Set<Source> sources, Query query) {
        for (Source source : sources) {
            if (source.parameters().size() > 0 && query.getModel().getSources().contains(source.getName()))
                addParameters(source,query);
        }
    }

    /** Adds parameters specified in the source to the correct namespace in the query */
    private void addParameters(Source source,Query query) {
        for (Map.Entry<String,String> parameter : source.parameters().entrySet())
            query.properties().set("source." + source.getName() + "." + parameter.getKey(),parameter.getValue());
    }

    /**
     * Currently executing multiple queries to the same source with different parameter sets,
     * is not supported. (Same parameter sets in multiple templates is supported,
     * and will be just one entry in this set).
     */
    private void addErrorIfSameSourceMultipleTimes(List<PageElement> pages, Set<Source> sources, Query query) {
        Set<String> sourceNames = new HashSet<>();
        for (Source source : sources) {
            if (sourceNames.contains(source.getName()))
                query.errors().add(ErrorMessage.createInvalidQueryParameter(
                        "Querying the same source multiple times with different parameter sets as part of one query " +
                        "is not supported. " + pages + " requests this for source '" + source + "'"));
            sourceNames.add(source.getName());
        }
    }

}
