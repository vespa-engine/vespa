// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import static com.yahoo.prelude.searcher.PosSearcher.POSITION_PARSING;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.Location;
import com.yahoo.prelude.query.GeoLocationItem;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import com.yahoo.component.annotation.Inject;
import java.util.List;

/**
 * If position attribute has not been set, it will be set here.
 *
 * @author baldersheim
 */
@After({PhaseNames.RAW_QUERY, POSITION_PARSING})
@Before(PhaseNames.TRANSFORMED_QUERY)
public class DefaultPositionSearcher extends Searcher {

    private final boolean useV8GeoPositions;

    @Inject
    public DefaultPositionSearcher(DocumentmanagerConfig cfg) {
        this.useV8GeoPositions = cfg.usev8geopositions();
    }

    DefaultPositionSearcher() {
        this.useV8GeoPositions = false;
    }

    @Override
    public com.yahoo.search.Result search(Query query, Execution execution) {
        Location location = query.getRanking().getLocation();
        if (location != null && (location.getAttribute() == null)) {
            IndexFacts facts = execution.context().getIndexFacts();
            List<String> search = facts.newSession(query.getModel().getSources(), query.getModel().getRestrict()).documentTypes();

            for (String sd : search) {
                String defaultPosition = facts.getDefaultPosition(sd);
                if (defaultPosition != null) {
                    location.setAttribute(defaultPosition);
                }
            }
            if (location.getAttribute() == null) {
                location.setAttribute(facts.getDefaultPosition(null));
            }
        }
        if (useV8GeoPositions && (location != null) && (location.getAttribute() != null)) {
            var geoLoc = new GeoLocationItem(location);
            if (location.isGeoCircle() && location.degRadius() < 0) {
                query.getModel().getQueryTree().withRank(geoLoc);
            } else {
                query.getModel().getQueryTree().and(geoLoc);
            }
            location = null;
            query.getRanking().setLocation(location);
        }
        return execution.search(query);
    }

}
