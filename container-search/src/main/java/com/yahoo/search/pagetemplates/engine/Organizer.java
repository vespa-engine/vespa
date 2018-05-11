// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine;

import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.model.*;
import com.yahoo.search.pagetemplates.result.SectionHitGroup;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reorganizes and prunes a result as prescribed by a resolved template.
 * This class is multithread safe.
 *
 * @author bratseth
 */
public class Organizer {

    /**
     * Organizes the given result
     *
     * @param templateChoice a choice between singleton lists of PageTemplates
     * @param resolution the resolution of (at least) the template choice and all choices contained in that template
     * @param result the result to organize
     */
    public void organize(Choice templateChoice, Resolution resolution, Result result) {
        PageTemplate template=(PageTemplate)templateChoice.get(resolution.getResolution(templateChoice)).get(0);
        SectionHitGroup sectionGroup =toGroup(template.getSection(),resolution,result);
        ErrorHit errors=result.hits().getErrorHit();

        // transfer state from existing hit
        sectionGroup.setQuery(result.hits().getQuery());
        if (errors!=null && errors instanceof DefaultErrorHit)
            sectionGroup.add((DefaultErrorHit)errors);
        result.hits().forEachField((name, value) -> sectionGroup.setField(name, value));
        result.setHits(sectionGroup);
    }

    /** Creates the hit group corresponding to a section, drawing data from the given result */
    private SectionHitGroup toGroup(Section section,Resolution resolution,Result result) {
        SectionHitGroup sectionGroup=new SectionHitGroup("section:" + section.getId());
        setField("id",section.getId(),sectionGroup);
        sectionGroup.setLeaf(section.elements(Section.class).size()==0);
        setField("layout",section.getLayout().getName(),sectionGroup);
        setField("region",section.getRegion(),sectionGroup);

        List<String> sourceList=new ArrayList<>();
        renderElements(resolution, result, sectionGroup, sourceList, section.elements());

        // Trim to max
        if (section.getMax()>=0)
            sectionGroup.trim(0,section.getMax());
        if (sectionGroup.size()>1)
            assignOrderer(section,resolution,sourceList,sectionGroup);

        return sectionGroup;
    }

    private void renderElements(Resolution resolution, Result result, SectionHitGroup sectionGroup, List<String> sourceList, List<PageElement> elements) {
        for (PageElement element : elements) {
            if (element instanceof Section) {
                sectionGroup.add(toGroup((Section)element,resolution,result));
            }
            else if (element instanceof Source) {
                addSource(resolution,(Source)element,sectionGroup,result,sourceList);
            }
            else if (element instanceof Renderer) {
                sectionGroup.renderers().add((Renderer)element);
            }
            else if (element instanceof Choice) {
                Choice choice=(Choice)element;
                if (choice.isEmpty()) continue; // Ignore
                int chosen=resolution.getResolution(choice);
                renderElements(resolution, result, sectionGroup, sourceList, choice.alternatives().get(chosen));
            }
            else if (element instanceof Placeholder) {
                Placeholder placeholder =(Placeholder)element;
                List<PageElement> mappedElements=
                        resolution.getResolution(placeholder.getValueContainer()).get(placeholder.getId());
                renderElements(resolution,result,sectionGroup,sourceList,mappedElements);
            }
        }
    }

    private void setField(String fieldName,Object value,Hit to) {
        if (value==null) return;
        to.setField(fieldName,value);
    }

    private void addSource(Resolution resolution,Source source,SectionHitGroup sectionGroup,Result result,List<String> sourceList) {
        renderElements(resolution,result,sectionGroup, sourceList, source.renderers());
        /*
        for (PageElement element : source.renderers()) {
            if (element instanceof Renderer)
            if (renderer.isEmpty()) continue;
            sectionGroup.renderers().add(renderer.get(resolution.getResolution(renderer)));
        }
        */

        if (source.getUrl()==null)
            addHitsFromSource(source,sectionGroup,result,sourceList);
        else
            sectionGroup.sources().add(source); // source to be rendered by the frontend
    }

    private void addHitsFromSource(Source source,SectionHitGroup sectionGroup,Result result,List<String> sourceList) {
        if (source==Source.any) { // Add any source not added yet
            for (Hit hit : result.hits()) {
                if ( ! (hit instanceof HitGroup)) continue;
                String groupId=hit.getId().stringValue();
                if ( ! groupId.startsWith("source:")) continue;
                String sourceName=groupId.substring(7);
                if (sourceList.contains(sourceName)) continue;
                sectionGroup.addAll(((HitGroup)hit).asList());
                sourceList.add(sourceName); // Add *'ed sources explicitly
            }
        }
        else {
            HitGroup sourceGroup=(HitGroup)result.hits().get("source:" + source.getName());
            if (sourceGroup!=null)
                sectionGroup.addAll(sourceGroup.asList());
            sourceList.add(source.getName()); // Add even if not found - may be added later
        }
    }

    private void assignOrderer(Section section,Resolution resolution,List<String> sourceList,HitGroup group) {
        if (section.getOrder()==null) { // then sort by relevance, source
            group.setOrderer(new HitSortOrderer(new RelevanceComparator(new SourceOrderComparator(sourceList))));
            return;
        }

        // replace a source field comparison by one which knows the source list order
        // and add default sorting at the end if necessary
        Sorting sorting=section.getOrder();
        int rankIndex=-1;
        int sourceIndex=-1;
        for (int i=0; i<sorting.fieldOrders().size(); i++) {
            Sorting.FieldOrder order=sorting.fieldOrders().get(i);
            if ("[relevance]".equals(order.getFieldName()) || "[rank]".equals(order.getFieldName()))
                rankIndex=i;
            else if (order.getFieldName().equals("[source]"))
                sourceIndex=i;
        }

        ChainableComparator comparator;
        Sorting beforeSource=null;
        Sorting afterSource=null;
        if (sourceIndex>=0) { // replace alphabetical sorting on source by sourceList order sorting
            if (sourceIndex>0) // sort fields before the source
                beforeSource=new Sorting(new ArrayList<>(sorting.fieldOrders().subList(0,sourceIndex)));
            if (sorting.fieldOrders().size()>sourceIndex+1) // sort fields after the source
                afterSource=new Sorting(new ArrayList<>(sorting.fieldOrders().subList(sourceIndex+1,sorting.fieldOrders().size()+1)));

            comparator=new SourceOrderComparator(sourceList, FieldComparator.create(afterSource));
            if (beforeSource!=null)
                comparator=new FieldComparator(beforeSource,comparator);

        }
        else if (rankIndex>=0) { // add sort by source at the end
            comparator=new FieldComparator(sorting,new SourceOrderComparator(sourceList));
        }
        else { // add sort by rank,source at the end
            comparator=new FieldComparator(sorting,new RelevanceComparator(new SourceOrderComparator(sourceList)));
        }
        group.setOrderer(new HitSortOrderer(comparator));
    }

}
