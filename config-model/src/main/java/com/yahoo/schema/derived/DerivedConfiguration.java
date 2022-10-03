// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.validation.Validation;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.io.IOException;
import java.io.Writer;

/**
 * A set of all derived configuration of a schema. Use this as a facade to individual configurations when
 * necessary.
 *
 * @author bratseth
 */
public class DerivedConfiguration implements AttributesConfig.Producer {

    private final Schema schema;
    private Summaries summaries;
    private Juniperrc juniperrc;
    private AttributeFields attributeFields;
    private RankProfileList rankProfileList;
    private IndexingScript indexingScript;
    private IndexInfo indexInfo;
    private SchemaInfo schemaInfo;
    private VsmFields streamingFields;
    private VsmSummary streamingSummary;
    private IndexSchema indexSchema;
    private ImportedFields importedFields;
    private final QueryProfileRegistry queryProfiles;
    private final long maxUncommittedMemory;

    /**
     * Creates a complete derived configuration from a search definition.
     * Only used in tests.
     *
     * @param schema the search to derive a configuration from. Derived objects will be snapshots, but this argument is
     *               live. Which means that this object will be inconsistent when the given search definition is later
     *               modified.
     * @param rankProfileRegistry a {@link com.yahoo.schema.RankProfileRegistry}
     */
    public DerivedConfiguration(Schema schema, RankProfileRegistry rankProfileRegistry) {
        this(schema, rankProfileRegistry, new QueryProfileRegistry());
    }

    DerivedConfiguration(Schema schema, RankProfileRegistry rankProfileRegistry, QueryProfileRegistry queryProfiles) {
        this(schema, new DeployState.Builder().rankProfileRegistry(rankProfileRegistry).queryProfiles(queryProfiles).build());
    }

    /**
     * Creates a complete derived configuration snapshot from a schema.
     *
     * @param schema the schema to derive a configuration from. Derived objects will be snapshots, but this
     *               argument is live. Which means that this object will be inconsistent if the given
     *               schema is later modified.
     */
    public DerivedConfiguration(Schema schema, DeployState deployState) {
        try {
            Validator.ensureNotNull("Schema", schema);
            this.schema = schema;
            this.queryProfiles = deployState.getQueryProfiles().getRegistry();
            this.maxUncommittedMemory = deployState.getProperties().featureFlags().maxUnCommittedMemory();
            if (!schema.isDocumentsOnly()) {
                streamingFields = new VsmFields(schema);
                streamingSummary = new VsmSummary(schema);
            }
            if (!schema.isDocumentsOnly()) {
                attributeFields = new AttributeFields(schema);
                summaries = new Summaries(schema, deployState.getDeployLogger(), deployState.getProperties().featureFlags());
                juniperrc = new Juniperrc(schema);
                rankProfileList = new RankProfileList(schema, schema.rankExpressionFiles(), attributeFields, deployState);
                indexingScript = new IndexingScript(schema);
                indexInfo = new IndexInfo(schema);
                schemaInfo = new SchemaInfo(schema, deployState.rankProfileRegistry(), summaries);
                indexSchema = new IndexSchema(schema);
                importedFields = new ImportedFields(schema);
            }
            Validation.validate(this, schema);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + schema, e);
        }
    }

    /**
     * Exports a complete set of configuration-server format config files.
     *
     * @param toDirectory  the directory to export to, current dir if null
     * @throws IOException if exporting fails, some files may still be created
     */
    public void export(String toDirectory) throws IOException {
        if (!schema.isDocumentsOnly()) {
            summaries.export(toDirectory);
            juniperrc.export(toDirectory);
            attributeFields.export(toDirectory);
            streamingFields.export(toDirectory);
            streamingSummary.export(toDirectory);
            indexSchema.export(toDirectory);
            rankProfileList.export(toDirectory);
            indexingScript.export(toDirectory);
            indexInfo.export(toDirectory);
            importedFields.export(toDirectory);
            schemaInfo.export(toDirectory);
        }
    }

    public static void exportDocuments(DocumentmanagerConfig.Builder documentManagerCfg, String toDirectory) throws IOException {
        exportCfg(new DocumentmanagerConfig(documentManagerCfg), toDirectory + "/" + "documentmanager.cfg");
    }

    public static void exportDocuments(DocumenttypesConfig.Builder documentTypesCfg, String toDirectory) throws IOException {
        exportCfg(new DocumenttypesConfig(documentTypesCfg), toDirectory + "/" + "documenttypes.cfg");
    }

    public static void exportQueryProfiles(QueryProfileRegistry queryProfileRegistry, String toDirectory) throws IOException {
        exportCfg(new QueryProfiles(queryProfileRegistry, (level, message) -> {}).getConfig(), toDirectory + "/" + "query-profiles.cfg");
    }

    public void exportConstants(String toDirectory) throws IOException {
        RankingConstantsConfig.Builder b = new RankingConstantsConfig.Builder();
        rankProfileList.getConfig(b);
        exportCfg(b.build(), toDirectory + "/" + "ranking-constants.cfg");
    }

    private static void exportCfg(ConfigInstance instance, String fileName) throws IOException {
        Writer writer = null;
        try {
            writer = IOUtils.createWriter(fileName, false);
            writer.write(instance.toString());
            writer.write("\n");
        } finally {
            if (writer != null) {
                IOUtils.closeWriter(writer);
            }
        }
    }

    public Summaries getSummaries() {
        return summaries;
    }

    public AttributeFields getAttributeFields() {
        return attributeFields;
    }

    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        getConfig(builder, AttributeFields.FieldSet.ALL);
    }

    public void getConfig(AttributesConfig.Builder builder, AttributeFields.FieldSet fs) {
        attributeFields.getConfig(builder, fs, maxUncommittedMemory);
    }

    public IndexingScript getIndexingScript() {
        return indexingScript;
    }

    public IndexInfo getIndexInfo() {
        return indexInfo;
    }

    public SchemaInfo getSchemaInfo() { return schemaInfo; }

    public void setIndexingScript(IndexingScript script) {
        this.indexingScript = script;
    }

    public Schema getSchema() { return schema; }

    public RankProfileList getRankProfileList() {
        return rankProfileList;
    }

    public VsmSummary getVsmSummary() {
        return streamingSummary;
    }

    public VsmFields getVsmFields() {
        return streamingFields;
    }

    public IndexSchema getIndexSchema() {
        return indexSchema;
    }

    public Juniperrc getJuniperrc() {
        return juniperrc;
    }

    public ImportedFields getImportedFields() {
        return importedFields;
    }

    public QueryProfileRegistry getQueryProfiles() { return queryProfiles; }

}
