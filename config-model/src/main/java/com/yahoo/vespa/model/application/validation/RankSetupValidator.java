// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.io.IOUtils;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.yolean.Exceptions;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.collections.Pair;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
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
import java.time.Duration;
import java.time.Instant;

/**
 * Validate rank setup for all search clusters (rank-profiles, index-schema, attributes configs), validating done
 * by running through the binary 'verify_ranksetup'
 *
 * @author vegardh
 *
 */
public class RankSetupValidator extends Validator {
    private final boolean force;

    public RankSetupValidator(boolean force) {
        this.force = force;
    }

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        try {
            File cfgDir = Files.createTempDirectory("deploy_ranksetup").toFile();

            for (AbstractSearchCluster cluster : model.getSearchClusters()) {
                // Skipping rank expression checking for streaming clusters, not implemented yet
                if (cluster.isRealtime()) {
                    IndexedSearchCluster sc = (IndexedSearchCluster) cluster;
                    String clusterDir = cfgDir.getAbsolutePath() + "/" + sc.getClusterName() + "/";
                    for (DocumentDatabase docDb : sc.getDocumentDbs()) {
                        final String name = docDb.getDerivedConfiguration().getSearch().getName();
                        String searchDir = clusterDir + name + "/";
                        writeConfigs(searchDir, docDb);
                        if (!validate("dir:" + searchDir, sc, name, deployState.getDeployLogger(), cfgDir)) {
                            return;
                        }
                    }
                }
            }
            deleteTempDir(cfgDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean validate(String configId, SearchCluster searchCluster, String sdName, DeployLogger logger, File tempDir) {
        Instant start = Instant.now();
        try {
            boolean ret = execValidate(configId, searchCluster, sdName, logger);
            if (!ret) {
                // Give up, don't say same error msg repeatedly
                deleteTempDir(tempDir);
            }
            logger.log(LogLevel.DEBUG, String.format("Validating %s for %s, %s took %s ms", sdName, searchCluster, configId, Duration.between(start, Instant.now()).toMillis()));
            return ret;
        } catch (IllegalArgumentException e) {
            deleteTempDir(tempDir);
            throw e;
        }
    }

    private void deleteTempDir(File dir) {
        if (!IOUtils.recursiveDeleteDir(dir)) {
            throw new RuntimeException("Failed deleting " + dir);
        }
    }

    private void writeConfigs(String dir, AbstractConfigProducer producer) throws IOException {
            RankProfilesConfig.Builder rpb = new RankProfilesConfig.Builder();
            RankProfilesConfig.Producer rpProd = (RankProfilesConfig.Producer) producer;
            rpProd.getConfig(rpb);
            writeConfig(dir, "rank-profiles.cfg", new RankProfilesConfig(rpb));

            IndexschemaConfig.Builder isB = new IndexschemaConfig.Builder();
            IndexschemaConfig.Producer isProd = (IndexschemaConfig.Producer) producer;
            isProd.getConfig(isB);
            writeConfig(dir, "indexschema.cfg", new IndexschemaConfig(isB));

            AttributesConfig.Builder acb = new AttributesConfig.Builder();
            AttributesConfig.Producer acProd = (AttributesConfig.Producer) producer;
            acProd.getConfig(acb);
            writeConfig(dir, "attributes.cfg", new AttributesConfig(acb));
    }

    private static void writeConfig(String dir, String configName, ConfigInstance config) throws IOException {
        IOUtils.writeFile(dir + configName, StringUtilities.implodeMultiline(ConfigInstance.serialize(config)), false);
    }

    private boolean execValidate(String configId, SearchCluster sc, String sdName, DeployLogger logger) {
        String job = "verify_ranksetup-bin " + configId;
        ProcessExecuter executer = new ProcessExecuter();
        try {
            Pair<Integer, String> ret = executer.exec(job);
            if (ret.getFirst() != 0) {
                validateFail(ret.getSecond(), sc, sdName, logger);
            }
        } catch (IOException e) {
            validateWarn(e, logger);
            return false;
        }
        return true;
    }

    private void validateWarn(Exception e, DeployLogger logger) {
        String msg = "Unable to execute 'verify_ranksetup', validation of rank expressions will only take place when you start Vespa: " +
                Exceptions.toMessageString(e);
        logger.log(LogLevel.WARNING, msg);
    }

    private void validateFail(String output, SearchCluster sc, String sdName, DeployLogger logger) {
        String errMsg = "For search cluster '" + sc.getClusterName() + "', search definition '" + sdName + "': error in rank setup. Details:\n";
        for (String line : output.split("\n")) {
            // Remove debug lines from start script
            if (line.startsWith("debug\t")) continue;
            try {
                LogMessage logmsg = LogMessage.parseNativeFormat(line);
                errMsg = errMsg + logmsg.getLevel() + ": " + logmsg.getPayload() + "\n";
            } catch (InvalidLogFormatException e) {
                errMsg = errMsg + line + "\n";
            }
        }
        if (force) {
            logger.log(LogLevel.WARNING, errMsg + "(Continuing because of force.)");
        } else {
            throw new IllegalArgumentException(errMsg);
        }
    }

}
