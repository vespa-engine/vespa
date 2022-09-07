// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.ImportedFieldsConfig;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
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
        RankingExpressionsConfig.Producer,
        OnnxModelsConfig.Producer,
        IndexschemaConfig.Producer,
        JuniperrcConfig.Producer,
        SummaryConfig.Producer,
        ImportedFieldsConfig.Producer,
        SchemaInfoConfig.Producer {

    private final String schemaName;
    private final DerivedConfiguration derivedCfg;

    public DocumentDatabase(AbstractConfigProducer<?> parent, String schemaName, DerivedConfiguration derivedCfg) {
        super(parent, schemaName);
        this.schemaName = schemaName;
        this.derivedCfg = derivedCfg;
    }

    public String getName() {
        return schemaName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public DerivedConfiguration getDerivedConfiguration() {
        return derivedCfg;
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) { derivedCfg.getIndexInfo().getConfig(builder); }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) { derivedCfg.getIndexingScript().getConfig(builder); }

    @Override
    public void getConfig(AttributesConfig.Builder builder) { derivedCfg.getConfig(builder); }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) { derivedCfg.getRankProfileList().getConfig(builder); }

    @Override
    public void getConfig(RankingExpressionsConfig.Builder builder) { derivedCfg.getRankProfileList().getConfig(builder); }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) { derivedCfg.getRankProfileList().getConfig(builder); }

    @Override
    public void getConfig(OnnxModelsConfig.Builder builder) { derivedCfg.getRankProfileList().getConfig(builder); }

    @Override
    public void getConfig(IndexschemaConfig.Builder builder) { derivedCfg.getIndexSchema().getConfig(builder); }

    @Override
    public void getConfig(JuniperrcConfig.Builder builder) { derivedCfg.getJuniperrc().getConfig(builder); }

    @Override
    public void getConfig(SummaryConfig.Builder builder) { derivedCfg.getSummaries().getConfig(builder); }

    @Override
    public void getConfig(ImportedFieldsConfig.Builder builder) { derivedCfg.getImportedFields().getConfig(builder); }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) { derivedCfg.getSchemaInfo().getConfig(builder); }

}
