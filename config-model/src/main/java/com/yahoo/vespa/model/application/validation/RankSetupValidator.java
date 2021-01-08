// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.io.IOUtils;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.collections.Pair;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.search.ImportedFieldsConfig;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.SearchCluster;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validate rank setup for all search clusters (rank-profiles, index-schema, attributes configs), validating done
 * by running the binary 'vespa-verify-ranksetup-bin'
 *
 * @author vegardh
 */
public class RankSetupValidator extends Validator {

    private static final Logger log = Logger.getLogger(RankSetupValidator.class.getName());
    private static final String binaryName = "vespa-verify-ranksetup-bin ";

    private final boolean ignoreValidationErrors;

    public RankSetupValidator(boolean ignoreValidationErrors) {
        this.ignoreValidationErrors = ignoreValidationErrors;
    }

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        File cfgDir = null;
        try {
            cfgDir = Files.createTempDirectory("verify-ranksetup." +
                                               deployState.getProperties().applicationId().toFullString() +
                                               ".")
                    .toFile();

            for (AbstractSearchCluster cluster : model.getSearchClusters()) {
                // Skipping rank expression checking for streaming clusters, not implemented yet
                if (cluster.isRealtime()) {
                    IndexedSearchCluster sc = (IndexedSearchCluster) cluster;
                    String clusterDir = cfgDir.getAbsolutePath() + "/" + sc.getClusterName() + "/";
                    for (DocumentDatabase docDb : sc.getDocumentDbs()) {
                        final String name = docDb.getDerivedConfiguration().getSearch().getName();
                        String searchDir = clusterDir + name + "/";
                        writeConfigs(searchDir, docDb);
                        writeExtraVerifyRanksetupConfig(searchDir, docDb);
                        if ( ! validate("dir:" + searchDir, sc, name, deployState.getDeployLogger(), cfgDir)) {
                            return;
                        }
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (cfgDir != null)
                deleteTempDir(cfgDir);
        }
    }

    private boolean validate(String configId, SearchCluster searchCluster, String sdName, DeployLogger deployLogger, File tempDir) {
        Instant start = Instant.now();
        try {
            boolean ret = execValidate(configId, searchCluster, sdName, deployLogger);
            if (!ret) {
                // Give up, don't say same error msg repeatedly
                deleteTempDir(tempDir);
            }
            log.log(Level.FINE, String.format("Validating %s for %s, %s took %s ms",
                                                  sdName,
                                                  searchCluster,
                                                  configId,
                                                  Duration.between(start, Instant.now()).toMillis()));
            return ret;
        } catch (IllegalArgumentException e) {
            deleteTempDir(tempDir);
            throw e;
        }
    }

    private void deleteTempDir(File dir) {
        IOUtils.recursiveDeleteDir(dir);
    }

    private void writeConfigs(String dir, AbstractConfigProducer<?> producer) throws IOException {
            RankProfilesConfig.Builder rpcb = new RankProfilesConfig.Builder();
            ((RankProfilesConfig.Producer) producer).getConfig(rpcb);
            RankProfilesConfig rpc = new RankProfilesConfig(rpcb);
            writeConfig(dir, RankProfilesConfig.getDefName() + ".cfg", rpc);

            IndexschemaConfig.Builder iscb = new IndexschemaConfig.Builder();
            ((IndexschemaConfig.Producer) producer).getConfig(iscb);
            IndexschemaConfig isc = new IndexschemaConfig(iscb);
            writeConfig(dir, IndexschemaConfig.getDefName() + ".cfg", isc);

            AttributesConfig.Builder acb = new AttributesConfig.Builder();
            ((AttributesConfig.Producer) producer).getConfig(acb);
            AttributesConfig ac = new AttributesConfig(acb);
            writeConfig(dir, AttributesConfig.getDefName() + ".cfg", ac);

            RankingConstantsConfig.Builder rccb = new RankingConstantsConfig.Builder();
            ((RankingConstantsConfig.Producer) producer).getConfig(rccb);
            RankingConstantsConfig rcc = new RankingConstantsConfig(rccb);
            writeConfig(dir, RankingConstantsConfig.getDefName() + ".cfg", rcc);

            OnnxModelsConfig.Builder omcb = new OnnxModelsConfig.Builder();
            ((OnnxModelsConfig.Producer) producer).getConfig(omcb);
            OnnxModelsConfig omc = new OnnxModelsConfig(omcb);
            writeConfig(dir, OnnxModelsConfig.getDefName() + ".cfg", omc);

            ImportedFieldsConfig.Builder ifcb = new ImportedFieldsConfig.Builder();
            ((ImportedFieldsConfig.Producer) producer).getConfig(ifcb);
            ImportedFieldsConfig ifc = new ImportedFieldsConfig(ifcb);
            writeConfig(dir, ImportedFieldsConfig.getDefName() + ".cfg", ifc);
    }

    private void writeExtraVerifyRanksetupConfig(String dir, DocumentDatabase db) throws IOException {
        String configName = "verify-ranksetup.cfg";
        String configContent = "";

        // Assist verify-ranksetup in finding the actual ONNX model files
        Map<String, OnnxModel> models = db.getDerivedConfiguration().getSearch().onnxModels().asMap();
        if (models.values().size() > 0) {
            List<String> config = new ArrayList<>(models.values().size() * 2);
            for (OnnxModel model : models.values()) {
                String modelPath = getFileRepositoryPath(model.getFilePath(), model.getFileReference());
                config.add(String.format("file[%d].ref \"%s\"", config.size() / 2, model.getFileReference()));
                config.add(String.format("file[%d].path \"%s\"", config.size() / 2, modelPath));
            }
            configContent = StringUtilities.implodeMultiline(config);
        }
        IOUtils.writeFile(dir + configName, configContent, false);
    }

    public static String getFileRepositoryPath(Path path, String fileReference) {
        ConfigserverConfig cfg = new ConfigserverConfig(new ConfigserverConfig.Builder());  // assume defaults
        String fileRefDir = Defaults.getDefaults().underVespaHome(cfg.fileReferencesDir());
        return Paths.get(fileRefDir, fileReference, path.getName()).toString();
    }

    private static void writeConfig(String dir, String configName, ConfigInstance config) throws IOException {
        IOUtils.writeFile(dir + configName, StringUtilities.implodeMultiline(ConfigInstance.serialize(config)), false);
    }

    private boolean execValidate(String configId, SearchCluster sc, String sdName, DeployLogger deployLogger) {
        String job = String.format("%s %s", binaryName, configId);
        ProcessExecuter executer = new ProcessExecuter(true);
        try {
            Pair<Integer, String> ret = executer.exec(job);
            if (ret.getFirst() != 0) {
                validateFail(ret.getSecond(), sc, sdName, deployLogger);
            }
        } catch (IOException e) {
            validateWarn(e, deployLogger);
            return false;
        }
        return true;
    }

    private void validateWarn(Exception e, DeployLogger deployLogger) {
        String msg = "Unable to execute '"+ binaryName + "', validation of rank expressions will only take place when you start Vespa: " +
                Exceptions.toMessageString(e);
        deployLogger.log(Level.WARNING, msg);
    }

    private void validateFail(String output, SearchCluster sc, String sdName, DeployLogger deployLogger) {
        StringBuilder errMsg = new StringBuilder("For search cluster '").append(sc.getClusterName()).append("', ")
                .append("search definition '").append(sdName).append("': error in rank setup. Details:\n");
        for (String line : output.split("\n")) {
            // Remove debug lines from start script
            if (line.startsWith("debug\t")) continue;
            try {
                LogMessage logMessage = LogMessage.parseNativeFormat(line);
                errMsg.append(logMessage.getLevel()).append(": ").append(logMessage.getPayload()).append("\n");
            } catch (InvalidLogFormatException e) {
                errMsg.append(line).append("\n");
            }
        }
        if (ignoreValidationErrors) {
            deployLogger.log(Level.WARNING, errMsg.append("(Continuing since ignoreValidationErrors flag is set.)").toString());
        } else {
            throw new IllegalArgumentException(errMsg.toString());
        }
    }

}
