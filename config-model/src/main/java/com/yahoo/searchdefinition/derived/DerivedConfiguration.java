// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.protect.Validator;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.derived.validation.Validation;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * A set of all derived configuration of a search definition. Use this as a facade to individual configurations when
 * necessary.
 *
 * @author bratseth
 */
public class DerivedConfiguration {

    private Search search;
    private Summaries summaries;
    private SummaryMap summaryMap;
    private Juniperrc juniperrc;
    private AttributeFields attributeFields;
    private RankProfileList rankProfileList;
    private IndexingScript indexingScript;
    private IndexInfo indexInfo;
    private VsmFields streamingFields;
    private VsmSummary streamingSummary;
    private IndexSchema indexSchema;
    private ImportedFields importedFields;

    /**
     * Creates a complete derived configuration from a search definition.
     *
     * @param search The search to derive a configuration from. Derived objects will be snapshots, but this argument is
     *               live. Which means that this object will be inconsistent when the given search definition is later
     *               modified.
     * @param rankProfileRegistry a {@link com.yahoo.searchdefinition.RankProfileRegistry}
     */
    public DerivedConfiguration(Search search, RankProfileRegistry rankProfileRegistry) {
        this(search, null, new BaseDeployLogger(), rankProfileRegistry);
    }

    /**
     * Creates a complete derived configuration snapshot from a search definition.
     *
     * @param search             The search to derive a configuration from. Derived objects will be snapshots, but this
     *                           argument is live. Which means that this object will be inconsistent when the given
     *                           search definition is later modified.
     * @param abstractSearchList Search definition this one inherits from, only superclass configuration should be
     *                           generated. Null or empty list if there is none.
     * @param deployLogger       a {@link DeployLogger} for logging when
     *                           doing operations on this
     * @param rankProfileRegistry a {@link com.yahoo.searchdefinition.RankProfileRegistry}
     */
    public DerivedConfiguration(Search search, List<Search> abstractSearchList, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry) {
        Validator.ensureNotNull("Search definition", search);
        if ( ! search.isProcessed()) {
            throw new IllegalArgumentException("Search '" + search.getName() + "' not processed.");
        }
        this.search = search;
        if ( ! search.isDocumentsOnly()) {
            streamingFields = new VsmFields(search);
            streamingSummary = new VsmSummary(search);
        }
        if (abstractSearchList != null) {
            for (Search abstractSearch : abstractSearchList) {
                if (!abstractSearch.isProcessed()) {
                    throw new IllegalArgumentException("Search '" + search.getName() + "' not processed.");
                }
            }
        }
        if ( ! search.isDocumentsOnly()) {
            attributeFields = new AttributeFields(search);
            summaries = new Summaries(search, deployLogger);
            summaryMap = new SummaryMap(search, summaries);
            juniperrc = new Juniperrc(search);
            rankProfileList = new RankProfileList(search, attributeFields, rankProfileRegistry);
            indexingScript = new IndexingScript(search);
            indexInfo = new IndexInfo(search);
            indexSchema = new IndexSchema(search);
            importedFields = new ImportedFields(search);
        }
        Validation.validate(this, search);
    }

    /**
     * Exports a complete set of configuration-server format config files.
     *
     * @param toDirectory  the directory to export to, current dir if null
     * @throws IOException if exporting fails, some files may still be created
     */
    public void export(String toDirectory) throws IOException {
        if (!search.isDocumentsOnly()) {
            summaries.export(toDirectory);
            summaryMap.export(toDirectory);
            juniperrc.export(toDirectory);
            attributeFields.export(toDirectory);
            streamingFields.export(toDirectory);
            streamingSummary.export(toDirectory);
            indexSchema.export(toDirectory);
            rankProfileList.export(toDirectory);
            indexingScript.export(toDirectory);
            indexInfo.export(toDirectory);
            importedFields.export(toDirectory);
        }
    }

    public static void exportDocuments(DocumentmanagerConfig.Builder documentManagerCfg, String toDirectory) throws IOException {
        exportCfg(new DocumentmanagerConfig(documentManagerCfg), toDirectory + "/" + "documentmanager.cfg");
    }

    public static void exportDocuments(DocumenttypesConfig.Builder documentTypesCfg, String toDirectory) throws IOException {
        exportCfg(new DocumenttypesConfig(documentTypesCfg), toDirectory + "/" + "documenttypes.cfg");
    }

    private static void exportCfg(ConfigInstance instance, String fileName) throws IOException {
        Writer writer = null;
        try {
            writer = IOUtils.createWriter(fileName, false);
            if (writer != null) {
                writer.write(instance.toString());
                writer.write("\n");
            }
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

    public IndexingScript getIndexingScript() {
        return indexingScript;
    }

    public IndexInfo getIndexInfo() {
        return indexInfo;
    }

    public void setIndexingScript(IndexingScript script) {
        this.indexingScript = script;
    }

    public Search getSearch() {
        return search;
    }

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

    public SummaryMap getSummaryMap() {
        return summaryMap;
    }

    public ImportedFields getImportedFields() {
        return importedFields;
    }
}
