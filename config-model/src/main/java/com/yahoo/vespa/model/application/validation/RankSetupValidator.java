// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.collections.Pair;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.io.IOUtils;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.schema.DistributableResource;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.ImportedFieldsConfig;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.yolean.Exceptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates rank setup for all content clusters (rank-profiles, index-schema, attributes configs), validation is done
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
            for (SearchCluster cluster : model.getSearchClusters()) {
                // Skipping ranking expression checking for streaming clusters, not implemented yet
                if (cluster.isStreaming()) continue;

                IndexedSearchCluster sc = (IndexedSearchCluster) cluster;
                String clusterDir = cfgDir.getAbsolutePath() + "/" + sc.getClusterName() + "/";
                for (DocumentDatabase docDb : sc.getDocumentDbs()) {
                    String schemaName = docDb.getDerivedConfiguration().getSchema().getName();
                    String schemaDir = clusterDir + schemaName + "/";
                    writeConfigs(schemaDir, docDb);
                    writeExtraVerifyRankSetupConfig(schemaDir, docDb);
                    if (!validate("dir:" + schemaDir, sc, schemaName, deployState.getDeployLogger(), cfgDir)) {
                        return;
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

    private boolean validate(String configId, SearchCluster searchCluster, String schema, DeployLogger deployLogger, File tempDir) {
        Instant start = Instant.now();
        try {
            log.log(Level.FINE, () -> String.format("Validating schema '%s' for cluster %s with config id %s", schema, searchCluster, configId));
            boolean ret = execValidate(configId, searchCluster, schema, deployLogger);
            if (!ret) {
                // Give up, don't log same error msg repeatedly
                deleteTempDir(tempDir);
            }
            log.log(Level.FINE, () -> String.format("Validation took %s ms", Duration.between(start, Instant.now()).toMillis()));
            return ret;
        } catch (IllegalArgumentException e) {
            deleteTempDir(tempDir);
            throw e;
        }
    }

    private void deleteTempDir(File dir) {
        IOUtils.recursiveDeleteDir(dir);
    }

    private void writeConfigs(String dir, AnyConfigProducer producer) throws IOException {
        RankProfilesConfig.Builder rpcb = new RankProfilesConfig.Builder();
        ((RankProfilesConfig.Producer) producer).getConfig(rpcb);
        writeConfig(dir, RankProfilesConfig.getDefName() + ".cfg", rpcb.build());

        IndexschemaConfig.Builder iscb = new IndexschemaConfig.Builder();
        ((IndexschemaConfig.Producer) producer).getConfig(iscb);
        writeConfig(dir, IndexschemaConfig.getDefName() + ".cfg", iscb.build());

        AttributesConfig.Builder acb = new AttributesConfig.Builder();
        ((AttributesConfig.Producer) producer).getConfig(acb);
        writeConfig(dir, AttributesConfig.getDefName() + ".cfg", acb.build());

        RankingConstantsConfig.Builder rccb = new RankingConstantsConfig.Builder();
        ((RankingConstantsConfig.Producer) producer).getConfig(rccb);
        writeConfig(dir, RankingConstantsConfig.getDefName() + ".cfg", rccb.build());

        RankingExpressionsConfig.Builder recb = new RankingExpressionsConfig.Builder();
        ((RankingExpressionsConfig.Producer) producer).getConfig(recb);
        writeConfig(dir, RankingExpressionsConfig.getDefName() + ".cfg", recb.build());

        OnnxModelsConfig.Builder omcb = new OnnxModelsConfig.Builder();
        ((OnnxModelsConfig.Producer) producer).getConfig(omcb);
        writeConfig(dir, OnnxModelsConfig.getDefName() + ".cfg", omcb.build());

        ImportedFieldsConfig.Builder ifcb = new ImportedFieldsConfig.Builder();
        ((ImportedFieldsConfig.Producer) producer).getConfig(ifcb);
        writeConfig(dir, ImportedFieldsConfig.getDefName() + ".cfg", ifcb.build());
    }

    private void writeExtraVerifyRankSetupConfig(List<String> config, Collection<? extends DistributableResource> resources) {
        for (DistributableResource model : resources) {
            String modelPath = getFileRepositoryPath(model.getFilePath().getName(), model.getFileReference());
            int index = config.size() / 2;
            config.add(String.format("file[%d].ref \"%s\"", index, model.getFileReference()));
            config.add(String.format("file[%d].path \"%s\"", index, modelPath));
            log.log(Level.FINE, index + ": " + model.getPathType() + " -> " + model.getName() + " -> " + modelPath + " -> " + model.getFileReference());
        }
    }

    private void writeExtraVerifyRankSetupConfig(String dir, DocumentDatabase db) throws IOException {
        List<String> config = new ArrayList<>();

        // Assist verify-ranksetup in finding the actual ONNX model files
        writeExtraVerifyRankSetupConfig(config, db.getDerivedConfiguration().getRankProfileList().getOnnxModels().asMap().values());
        writeExtraVerifyRankSetupConfig(config, db.getDerivedConfiguration().getSchema().rankExpressionFiles().expressions());

        config.sort(String::compareTo);
        String configContent = config.isEmpty() ? "" : StringUtilities.implodeMultiline(config);
        IOUtils.writeFile(dir + "verify-ranksetup.cfg", configContent, false);
    }

    public static String getFileRepositoryPath(String name, String fileReference) {
        ConfigserverConfig cfg = new ConfigserverConfig(new ConfigserverConfig.Builder());  // assume defaults
        String fileRefDir = Defaults.getDefaults().underVespaHome(cfg.fileReferencesDir());
        return Paths.get(fileRefDir, fileReference, name).toString();
    }

    private static void writeConfig(String dir, String configName, ConfigInstance config) throws IOException {
        IOUtils.writeFile(dir + configName, StringUtilities.implodeMultiline(ConfigInstance.serialize(config)), false);
    }

    private boolean execValidate(String configId, SearchCluster sc, String sdName, DeployLogger deployLogger) {
        String command = String.format("%s %s", binaryName, configId);
        try {
            Pair<Integer, String> ret = new ProcessExecuter(true).exec(command);
            Integer exitCode = ret.getFirst();
            String output = ret.getSecond();
            if (exitCode != 0) {
                validateFail(output, exitCode, sc, sdName, deployLogger);
            }
        } catch (IOException e) {
            validateWarn(e, deployLogger);
            return false;
        }
        return true;
    }

    private void validateWarn(Exception e, DeployLogger deployLogger) {
        String msg = "Unable to execute '" + binaryName +
                     "', validation of ranking expressions will only take place when you start Vespa: " +
                     Exceptions.toMessageString(e);
        deployLogger.logApplicationPackage(Level.WARNING, msg);
    }

    private void validateFail(String output, int exitCode, SearchCluster sc, String sdName, DeployLogger deployLogger) {
        StringBuilder message = new StringBuilder("Error in rank setup in schema '").append(sdName)
                .append("' for content cluster '").append(sc.getClusterName()).append("'.").append(" Details:\n");
        if (output.isEmpty()) {
            message.append("Verifying rank setup failed and got no output from stderr and stdout from '")
                   .append(binaryName)
                   .append("' (exit code: ")
                   .append(exitCode)
                   .append(").");
            if (exitCode == 137)
                message.append(" Exit code 137 usually means that the program has been killed by the OOM killer")
                       .append(", too little memory is allocated for the instance/container/machine");
            else
                message.append(" This could be due to full disk, out of memory etc. ");
        } else {
            for (String line : output.split("\n")) {
                // Remove debug lines from start script
                if (line.startsWith("debug\t")) continue;
                try {
                    LogMessage logMessage = LogMessage.parseNativeFormat(line);
                    message.append(logMessage.getLevel())
			    .append(": ")
			    .append(logMessage.getPayload().replace("\\n", "\n\t"))
			    .append("\n");
                } catch (InvalidLogFormatException e) {
                    message.append(line).append("\n");
                }
            }
        }

        if (ignoreValidationErrors) {
            deployLogger.log(Level.WARNING, message.append("(Continuing since ignoreValidationErrors flag is set.)").toString());
        } else {
            throw new IllegalArgumentException(message.toString());
        }
    }

}
