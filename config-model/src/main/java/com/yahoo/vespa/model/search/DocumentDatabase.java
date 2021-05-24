// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.ImportedFieldsConfig;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.summary.JuniperrcConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

/**
 * Represents a document database and the backend configuration needed for this database.
 *
 * @author geirst
 */
public class DocumentDatabase extends AbstractConfigProducer<DocumentDatabase> implements
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        AttributesConfig.Producer,
        RankProfilesConfig.Producer,
        RankingConstantsConfig.Producer,
        OnnxModelsConfig.Producer,
        IndexschemaConfig.Producer,
        JuniperrcConfig.Producer,
        SummarymapConfig.Producer,
        SummaryConfig.Producer,
        ImportedFieldsConfig.Producer {

    private final String inputDocType;
    private final DerivedConfiguration derivedCfg;

    public DocumentDatabase(AbstractConfigProducer<?> parent, String inputDocType, DerivedConfiguration derivedCfg) {
        super(parent, inputDocType);
        this.inputDocType = inputDocType;
        this.derivedCfg = derivedCfg;
    }

    public String getName() {
        return inputDocType;
    }

    public String getInputDocType() {
        return inputDocType;
    }

    public DerivedConfiguration getDerivedConfiguration() {
        return derivedCfg;
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        derivedCfg.getIndexInfo().getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        derivedCfg.getIndexingScript().getConfig(builder);
    }

    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        derivedCfg.getAttributeFields().getConfig(builder);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        derivedCfg.getRankProfileList().getConfig(builder);
    }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) {
        derivedCfg.getRankProfileList().getConfig(builder);
    }

    @Override
    public void getConfig(OnnxModelsConfig.Builder builder) {
        derivedCfg.getRankProfileList().getConfig(builder);
    }

    @Override
    public void getConfig(IndexschemaConfig.Builder builder) {
        derivedCfg.getIndexSchema().getConfig(builder);
    }

    @Override
    public void getConfig(JuniperrcConfig.Builder builder) {
        derivedCfg.getJuniperrc().getConfig(builder);
    }

    @Override
    public void getConfig(SummarymapConfig.Builder builder) {
        derivedCfg.getSummaryMap().getConfig(builder);
    }

    @Override
    public void getConfig(SummaryConfig.Builder builder) {
        derivedCfg.getSummaries().getConfig(builder);
    }

    @Override
    public void getConfig(ImportedFieldsConfig.Builder builder) {
        derivedCfg.getImportedFields().getConfig(builder);
    }

}
