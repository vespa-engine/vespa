package com.yahoo.vespa.hosted.node.admin.logging;

import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class FilebeatConfigProvider {

    private static final String TEMPLATE = "filebeat.yml.template";
    private final Environment environment;

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

    public FilebeatConfigProvider(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> getConfig(ContainerNodeSpec containerNodeSpec) throws IOException {
        if(environment.getLogstashNodes().size() == 0) {
            return Optional.empty();
        }

        // Get a template with the environment stuff filled in
        String template = replaceEnvironmentInfo(readTemplate());

        // Replace application specific parts
        Optional<ContainerNodeSpec.Owner> owner = containerNodeSpec.owner;
        return owner.map(o -> replaceOwnerInfo(o, template));
    }

    private String replaceEnvironmentInfo(String template) {
        template = template.replaceAll(ENVIRONMENT_FIELD, environment.getEnvironment());
        template = template.replaceAll(REGION_FIELD, environment.getRegion());

        int spoolSize = environment.getLogstashNodes().size() * logstashWorkers * logstashBulkMaxSize;
        template = template.replaceAll(FILEBEAT_SPOOL_SIZE_FIELD, Integer.toString(spoolSize));
        template = template.replaceAll(LOGSTASH_HOSTS_FIELD, environment.getLogstashNodes().stream().collect(Collectors.joining(",")));
        template = template.replaceAll(LOGSTASH_WORKERS_FIELD, Integer.toString(logstashWorkers));
        template = template.replaceAll(LOGSTASH_BULK_MAX_SIZE_FIELD, Integer.toString(logstashBulkMaxSize));
        return template;
    }

    private String replaceOwnerInfo(ContainerNodeSpec.Owner owner, String template) {
        template = template.replaceAll(TENANT_FIELD, owner.tenant);
        template = template.replaceAll(APPLICATION_FIELD, owner.application);
        template = template.replaceAll(INSTANCE_FIELD, owner.instance);
        return template;
    }

    private String readTemplate() throws IOException {
        String file = getClass().getClassLoader().getResource(TEMPLATE).getFile();
        List<String> lines = Files.readAllLines(Paths.get(file));
        return lines.stream().collect(Collectors.joining("\n"));
    }
}
