// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.derived.SummaryMap;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.config.model.producer.AbstractConfigProducer;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Represents a search cluster.
 *
 * @author arnej27959
 */
public abstract class SearchCluster extends AbstractSearchCluster
        implements
        DocumentdbInfoConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer {

    protected SearchCluster(AbstractConfigProducer<SearchCluster> parent, String clusterName, int index) {
        super(parent, clusterName, index);
    }

   /**
     * Must be called after cluster is built, to derive SD configs
     * Derives the search definitions from the application package..
     * Also stores the document names contained in the search
     * definitions.
     */
    public void deriveSchemas(DeployState deployState) {
        deriveAllSchemas(getLocalSDS(), deployState);
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        if (getSdConfig()!=null) getSdConfig().getIndexInfo().getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        if (getSdConfig()!=null) getSdConfig().getIndexingScript().getConfig(builder);
    }

    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        if (getSdConfig()!=null) getSdConfig().getAttributeFields().getConfig(builder);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        if (getSdConfig()!=null) getSdConfig().getRankProfileList().getConfig(builder);
    }

    /**
     * Converts summary and summary map config to the appropriate information in documentdb
     *
     * @param summaryConfigProducer the summary config
     * @param summarymapConfigProducer the summary map config, or null if none is available
     * @param docDb the target document dm config
     */
    protected void convertSummaryConfig(SummaryConfig.Producer summaryConfigProducer,
                                        SummarymapConfig.Producer summarymapConfigProducer,
                                        DocumentdbInfoConfig.Documentdb.Builder docDb) {

        SummaryConfig.Builder summaryConfigBuilder = new SummaryConfig.Builder();
        summaryConfigProducer.getConfig(summaryConfigBuilder);
        SummaryConfig summaryConfig = summaryConfigBuilder.build();

        SummarymapConfig summarymapConfig = null;
        if (summarymapConfigProducer != null) {
            SummarymapConfig.Builder summarymapConfigBuilder = new SummarymapConfig.Builder();
            summarymapConfigProducer.getConfig(summarymapConfigBuilder);
            summarymapConfig = summarymapConfigBuilder.build();
        }

        for (SummaryConfig.Classes sclass : summaryConfig.classes()) {
            DocumentdbInfoConfig.Documentdb.Summaryclass.Builder sumClassBuilder = new DocumentdbInfoConfig.Documentdb.Summaryclass.Builder();
            sumClassBuilder.
                id(sclass.id()).
                name(sclass.name());
            for (SummaryConfig.Classes.Fields field : sclass.fields()) {
                DocumentdbInfoConfig.Documentdb.Summaryclass.Fields.Builder fieldsBuilder = new DocumentdbInfoConfig.Documentdb.Summaryclass.Fields.Builder();
                fieldsBuilder.name(field.name())
                             .type(field.type())
                             .dynamic(isDynamic(field.name(), summarymapConfig));
                sumClassBuilder.fields(fieldsBuilder);
            }
            docDb.summaryclass(sumClassBuilder);
        }
    }

    /** Returns true if this is a dynamic summary field */
    private boolean isDynamic(String fieldName, SummarymapConfig summarymapConfig) {
        if (summarymapConfig == null) return false; // not know for streaming, but also not used

        for (SummarymapConfig.Override override : summarymapConfig.override()) {
            if ( ! fieldName.equals(override.field())) continue;
            if (SummaryMap.isDynamicCommand(override.command())) return true;
        }
        return false;
    }

    protected void addRankProfilesConfig(DocumentdbInfoConfig.Documentdb.Builder docDbBuilder, RankProfilesConfig rankProfilesCfg) {
        for (RankProfilesConfig.Rankprofile rankProfile : rankProfilesCfg.rankprofile()) {
            DocumentdbInfoConfig.Documentdb.Rankprofile.Builder rpB = new DocumentdbInfoConfig.Documentdb.Rankprofile.Builder();
            rpB.name(rankProfile.name());
            rpB.hasSummaryFeatures(containsPropertiesWithPrefix(RawRankProfile.summaryFeatureFefPropertyPrefix, rankProfile.fef()));
            rpB.hasRankFeatures(containsPropertiesWithPrefix(RawRankProfile.rankFeatureFefPropertyPrefix, rankProfile.fef()));
            docDbBuilder.rankprofile(rpB);
        }
    }

    private boolean containsPropertiesWithPrefix(String prefix, RankProfilesConfig.Rankprofile.Fef fef) {
        for (RankProfilesConfig.Rankprofile.Fef.Property p : fef.property()) {
            if (p.name().startsWith(prefix))
                return true;
        }
        return false;
    }

    protected abstract void deriveAllSchemas(List<SchemaSpec> localSearches, DeployState deployState);

    public abstract void defaultDocumentsConfig();
    public abstract DerivedConfiguration getSdConfig();

}
