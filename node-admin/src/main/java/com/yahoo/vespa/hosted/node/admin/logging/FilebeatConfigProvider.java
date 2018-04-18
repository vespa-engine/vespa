// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.logging;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class FilebeatConfigProvider {

    private static final String TEMPLATE = "filebeat.yml.template";

    private static final String TENANT_FIELD = "%%TENANT%%";
    private static final String APPLICATION_FIELD = "%%APPLICATION%%";
    private static final String INSTANCE_FIELD = "%%INSTANCE%%";
    private static final String ENVIRONMENT_FIELD = "%%ENVIRONMENT%%";
    private static final String REGION_FIELD = "%%REGION%%";
    private static final String FILEBEAT_SPOOL_SIZE_FIELD = "%%FILEBEAT_SPOOL_SIZE%%";
    private static final String LOGSTASH_HOSTS_FIELD = "%%LOGSTASH_HOSTS%%";
    private static final String LOGSTASH_WORKERS_FIELD = "%%LOGSTASH_WORKERS%%";
    private static final String LOGSTASH_BULK_MAX_SIZE_FIELD = "%%LOGSTASH_BULK_MAX_SIZE%%";

    private static final int logstashWorkers = 3;
    private static final int logstashBulkMaxSize = 2048;
    private final Environment environment;

    public FilebeatConfigProvider(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> getConfig(NodeSpec node) {

        if (environment.getLogstashNodes().size() == 0 || !node.owner.isPresent()) {
            return Optional.empty();
        }
        NodeSpec.Owner owner = node.owner.get();
        int spoolSize = environment.getLogstashNodes().size() * logstashWorkers * logstashBulkMaxSize;
        String logstashNodeString = environment.getLogstashNodes().stream()
                .map(this::addQuotes)
                .collect(Collectors.joining(","));
        return Optional.of(getTemplate()
                .replaceAll(ENVIRONMENT_FIELD, environment.getEnvironment())
                .replaceAll(REGION_FIELD, environment.getRegion())
                .replaceAll(FILEBEAT_SPOOL_SIZE_FIELD, Integer.toString(spoolSize))
                .replaceAll(LOGSTASH_HOSTS_FIELD, logstashNodeString)
                .replaceAll(LOGSTASH_WORKERS_FIELD, Integer.toString(logstashWorkers))
                .replaceAll(LOGSTASH_BULK_MAX_SIZE_FIELD, Integer.toString(logstashBulkMaxSize))
                .replaceAll(TENANT_FIELD, owner.tenant)
                .replaceAll(APPLICATION_FIELD, owner.application)
                .replaceAll(INSTANCE_FIELD, owner.instance));
    }

    private String addQuotes(String logstashNode) {
        return logstashNode.startsWith("\"")
                ? logstashNode
                : String.format("\"%s\"", logstashNode);
    }

    private String getTemplate() {
        return "################### Filebeat Configuration Example #########################\n" +
                "\n" +
                "############################# Filebeat ######################################\n" +
                "filebeat:\n" +
                "  # List of prospectors to fetch data.\n" +
                "  prospectors:\n" +
                "\n" +
                "    # vespa\n" +
                "    - paths:\n" +
                "        - " + environment.pathInNodeUnderVespaHome("logs/vespa/vespa.log") + "\n" +
                "      exclude_files: [\".gz$\"]\n" +
                "      document_type: vespa\n" +
                "      fields:\n" +
                "        HV-tenant: %%TENANT%%\n" +
                "        HV-application: %%APPLICATION%%\n" +
                "        HV-instance: %%INSTANCE%%\n" +
                "        HV-region: %%REGION%%\n" +
                "        HV-environment: %%ENVIRONMENT%%\n" +
                "        index_source: \"hosted-instance_%%TENANT%%_%%APPLICATION%%_%%REGION%%_%%ENVIRONMENT%%_%%INSTANCE%%\"\n" +
                "      fields_under_root: true\n" +
                "      close_older: 20m\n" +
                "      force_close_files: true\n" +
                "\n" +
                "    # vespa qrs\n" +
                "    - paths:\n" +
                "        - " + environment.pathInNodeUnderVespaHome("logs/vespa/qrs/QueryAccessLog.*.*") + "\n" +
                "      exclude_files: [\".gz$\"]\n" +
                "      exclude_lines: [\"reserved-for-internal-use/feedapi\"]\n" +
                "      document_type: vespa-qrs\n" +
                "      fields:\n" +
                "        HV-tenant: %%TENANT%%\n" +
                "        HV-application: %%APPLICATION%%\n" +
                "        HV-instance: %%INSTANCE%%\n" +
                "        HV-region: %%REGION%%\n" +
                "        HV-environment: %%ENVIRONMENT%%\n" +
                "        index_source: \"hosted-instance_%%TENANT%%_%%APPLICATION%%_%%REGION%%_%%ENVIRONMENT%%_%%INSTANCE%%\"\n" +
                "      fields_under_root: true\n" +
                "      close_older: 20m\n" +
                "      force_close_files: true\n" +
                "\n" +
                "  # General filebeat configuration options\n" +
                "  #\n" +
                "  # Event count spool threshold - forces network flush if exceeded\n" +
                "  spool_size: %%FILEBEAT_SPOOL_SIZE%%\n" +
                "\n" +
                "  # Defines how often the spooler is flushed. After idle_timeout the spooler is\n" +
                "  # Flush even though spool_size is not reached.\n" +
                "  #idle_timeout: 5s\n" +
                "  publish_async: false\n" +
                "\n" +
                "  # Name of the registry file. Per default it is put in the current working\n" +
                "  # directory. In case the working directory is changed after when running\n" +
                "  # filebeat again, indexing starts from the beginning again.\n" +
                "  registry_file: /var/lib/filebeat/registry\n" +
                "\n" +
                "  # Full Path to directory with additional prospector configuration files. Each file must end with .yml\n" +
                "  # These config files must have the full filebeat config part inside, but only\n" +
                "  # the prospector part is processed. All global options like spool_size are ignored.\n" +
                "  # The config_dir MUST point to a different directory then where the main filebeat config file is in.\n" +
                "  #config_dir:\n" +
                "\n" +
                "###############################################################################\n" +
                "############################# Libbeat Config ##################################\n" +
                "# Base config file used by all other beats for using libbeat features\n" +
                "\n" +
                "############################# Output ##########################################\n" +
                "\n" +
                "# Configure what outputs to use when sending the data collected by the beat.\n" +
                "# Multiple outputs may be used.\n" +
                "output:\n" +
                "\n" +
                "  ### Logstash as output\n" +
                "  logstash:\n" +
                "    # The Logstash hosts\n" +
                "    hosts: [%%LOGSTASH_HOSTS%%]\n" +
                "\n" +
                "    timeout: 15\n" +
                "\n" +
                "    # Number of workers per Logstash host.\n" +
                "    worker: %%LOGSTASH_WORKERS%%\n" +
                "\n" +
                "    # Set gzip compression level.\n" +
                "    compression_level: 3\n" +
                "\n" +
                "    # Optional load balance the events between the Logstash hosts\n" +
                "    loadbalance: true\n" +
                "\n" +
                "    # Optional index name. The default index name depends on the each beat.\n" +
                "    # For Packetbeat, the default is set to packetbeat, for Topbeat\n" +
                "    # top topbeat and for Filebeat to filebeat.\n" +
                "    #index: filebeat\n" +
                "\n" +
                "    bulk_max_size: %%LOGSTASH_BULK_MAX_SIZE%%\n" +
                "\n" +
                "    # Optional TLS. By default is off.\n" +
                "    #tls:\n" +
                "      # List of root certificates for HTTPS server verifications\n" +
                "      #certificate_authorities: [\"/etc/pki/root/ca.pem\"]\n" +
                "\n" +
                "      # Certificate for TLS client authentication\n" +
                "      #certificate: \"/etc/pki/client/cert.pem\"\n" +
                "\n" +
                "      # Client Certificate Key\n" +
                "      #certificate_key: \"/etc/pki/client/cert.key\"\n" +
                "\n" +
                "      # Controls whether the client verifies server certificates and host name.\n" +
                "      # If insecure is set to true, all server host names and certificates will be\n" +
                "      # accepted. In this mode TLS based connections are susceptible to\n" +
                "      # man-in-the-middle attacks. Use only for testing.\n" +
                "      #insecure: true\n" +
                "\n" +
                "      # Configure cipher suites to be used for TLS connections\n" +
                "      #cipher_suites: []\n" +
                "\n" +
                "      # Configure curve types for ECDHE based cipher suites\n" +
                "      #curve_types: []\n" +
                "\n" +
                "############################# Shipper #########################################\n" +
                "\n" +
                "shipper:\n" +
                "\n" +
                "############################# Logging #########################################\n" +
                "\n" +
                "# There are three options for the log ouput: syslog, file, stderr.\n" +
                "# Under Windos systems, the log files are per default sent to the file output,\n" +
                "# under all other system per default to syslog.\n" +
                "logging:\n" +
                "\n" +
                "  # Send all logging output to syslog. On Windows default is false, otherwise\n" +
                "  # default is true.\n" +
                "  to_syslog: false\n" +
                "\n" +
                "  # Write all logging output to files. Beats automatically rotate files if rotateeverybytes\n" +
                "  # limit is reached.\n" +
                "  to_files: true\n" +
                "\n" +
                "  # To enable logging to files, to_files option has to be set to true\n" +
                "  files:\n" +
                "    # The directory where the log files will written to.\n" +
                "    path: " + environment.pathInNodeUnderVespaHome("logs/filebeat") + "\n" +
                "\n" +
                "    # The name of the files where the logs are written to.\n" +
                "    name: filebeat\n" +
                "\n" +
                "    # Configure log file size limit. If limit is reached, log file will be\n" +
                "    # automatically rotated\n" +
                "    rotateeverybytes: 10485760 # = 10MB\n" +
                "\n" +
                "    # Number of rotated log files to keep. Oldest files will be deleted first.\n" +
                "    keepfiles: 7\n" +
                "\n" +
                "  # Enable debug output for selected components. To enable all selectors use [\"*\"]\n" +
                "  # Other available selectors are beat, publish, service\n" +
                "  # Multiple selectors can be chained.\n" +
                "  #selectors: [ ]\n" +
                "\n" +
                "  # Sets log level. The default log level is error.\n" +
                "  # Available log levels are: critical, error, warning, info, debug\n" +
                "  level: warning\n";
    }
}
