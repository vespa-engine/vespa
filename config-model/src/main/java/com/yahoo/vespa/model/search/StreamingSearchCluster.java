// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A search cluster of type streaming.
 * 
 * @author baldersheim
 * @author vegardh
 */
public class StreamingSearchCluster extends SearchCluster implements 
        DocumentdbInfoConfig.Producer, 
        RankProfilesConfig.Producer,
        VsmsummaryConfig.Producer,
        VsmfieldsConfig.Producer,
        SummarymapConfig.Producer,
        SummaryConfig.Producer
{

    private class AttributesProducer extends AbstractConfigProducer implements AttributesConfig.Producer {
        AttributesProducer(AbstractConfigProducer parent, String docType) {
            super(parent, docType);
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            if (getSdConfig() != null) {
                getSdConfig().getAttributeFields().getConfig(builder, AttributeFields.FieldSet.FAST_ACCESS);
            }
        }
    }

    private final String storageRouteSpec;
    private final AttributesProducer attributesConfig;
    private final String docTypeName;
    private DerivedConfiguration sdConfig = null;

    public StreamingSearchCluster(AbstractConfigProducer parent, String clusterName, int index, String docTypeName, String storageRouteSpec) {
        super(parent, clusterName, index);
        attributesConfig = new AttributesProducer(parent, docTypeName);
        this.docTypeName = docTypeName;
        this.storageRouteSpec = storageRouteSpec;
    }

    public final String getDocumentDBConfigId() {
        return attributesConfig.getConfigId();
    }
    @Override
    protected IndexingMode getIndexingMode() { return IndexingMode.STREAMING; }
    public final String getStorageRouteSpec()       { return storageRouteSpec; }

    public String getDocTypeName() {
        return docTypeName;
    }

    @Override
    public int getRowBits() { return 0; }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        DocumentdbInfoConfig.Documentdb.Builder docDb = new DocumentdbInfoConfig.Documentdb.Builder();
        String searchName = sdConfig.getSearch().getName();
        docDb.name(searchName);
        SummaryConfig.Producer prod = sdConfig.getSummaries();
        convertSummaryConfig(prod, null, docDb);
        RankProfilesConfig.Builder rpb = new RankProfilesConfig.Builder();
        sdConfig.getRankProfileList().getConfig(rpb);
        addRankProfilesConfig(docDb, new RankProfilesConfig(rpb));
        builder.documentdb(docDb);
    }

    @Override
    protected void assureSdConsistent() {
        if (sdConfig == null) {
            throw new IllegalStateException("Search cluster '" + getClusterName() + "' does not have any search definitions");
        }
    }

    protected void deriveAllSearchDefinitions(List<SearchDefinitionSpec> local,
                                              List<com.yahoo.searchdefinition.Search> global) {
        if (local.size() == 1) {
            deriveSingleSearchDefinition(local.get(0).getSearchDefinition().getSearch(), global);
        } else if (local.size() > 1){
            throw new IllegalStateException("Logical indexes are not supported: Got " + local.size() + " search definitions, expected 1");
        }
    }
    private void deriveSingleSearchDefinition(com.yahoo.searchdefinition.Search localSearch,
                                              List<com.yahoo.searchdefinition.Search> globalSearches) {
        if (!localSearch.getName().equals(docTypeName)) {
            throw new IllegalStateException("Mismatch between document type name (" + docTypeName + ") and name of search definition (" + localSearch.getName() + ")");
        }
        this.sdConfig = new DerivedConfiguration(localSearch, globalSearches, deployLogger(),
                                                 getRoot().getDeployState().rankProfileRegistry(),
                                                 getRoot().getDeployState().getQueryProfiles().getRegistry());
    }
    @Override
    public DerivedConfiguration getSdConfig() {
        return sdConfig;
    }
    @Override
    protected void exportSdFiles(File toDir) throws IOException {
        if (sdConfig!=null) {
            sdConfig.export(toDir.getCanonicalPath());
        }
    }
    @Override
    public void defaultDocumentsConfig() { }

    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        if (getSdConfig()!=null) getSdConfig().getAttributeFields().getConfig(builder);
    }
    
    @Override
    public void getConfig(VsmsummaryConfig.Builder builder) {
        if (getSdConfig()!=null) 
            if (getSdConfig().getVsmSummary()!=null)
                getSdConfig().getVsmSummary().getConfig(builder);
    }
    
    @Override
    public void getConfig(VsmfieldsConfig.Builder builder) {
        if (getSdConfig()!=null)
            if (getSdConfig().getVsmFields()!=null)
                getSdConfig().getVsmFields().getConfig(builder);
    }
    
    @Override
    public void getConfig(SummarymapConfig.Builder builder) {
        if (getSdConfig()!=null)
            if (getSdConfig().getSummaryMap()!=null)
                getSdConfig().getSummaryMap().getConfig(builder);
    }

    @Override
    public void getConfig(SummaryConfig.Builder builder) {
        if (getSdConfig()!=null)
            if (getSdConfig().getSummaries()!=null)
                getSdConfig().getSummaries().getConfig(builder);        
    }

}
