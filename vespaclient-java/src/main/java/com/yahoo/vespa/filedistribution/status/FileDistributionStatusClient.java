// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution.status;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.airline.Command;
import io.airlift.airline.HelpOption;
import io.airlift.airline.Option;
import io.airlift.airline.SingleCommand;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import static org.apache.hc.client5.http.config.RequestConfig.custom;

/**
 * Tool for getting file distribution status
 *
 * @author hmusum
 */
public class FileDistributionStatusClient {

    private static final String statusUnknown = "UNKNOWN";
    private static final String statusInProgress = "IN_PROGRESS";
    private static final String statusFinished = "FINISHED";

    private final String tenantName;
    private final String applicationName;
    private final String instanceName;
    private final String environment;
    private final String region;
    private final double timeout;
    private final boolean debug;

    FileDistributionStatusClient(CommandLineArguments arguments) {
        tenantName = arguments.getTenantName();
        applicationName = arguments.getApplicationName();
        instanceName = arguments.getInstanceName();
        environment = arguments.getEnvironment();
        region = arguments.getRegion();
        timeout = arguments.getTimeout();
        debug = arguments.getDebugFlag();
    }

    public static void main(String[] args) {
        try {
            new FileDistributionStatusClient(CommandLineArguments.build(args)).run();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public void run() {
        String json = doHttpRequest();
        System.out.println(parseAndGenerateOutput(json));
    }

    @SuppressWarnings("deprecation")
    private String doHttpRequest() {
        Timeout timeoutInMillis = Timeout.ofMilliseconds((long) (timeout * 1000));
        RequestConfig config = custom()
                .setConnectionRequestTimeout(timeoutInMillis)
                .setResponseTimeout(timeoutInMillis)
                .build();
        try (CloseableHttpClient httpClient =  VespaHttpClientBuilder.custom().connectTimeout(timeoutInMillis).buildClient()) {
            URI statusUri = createStatusApiUri();
            if (debug)
                System.out.println("URI:" + statusUri);
            HttpGet request = new HttpGet(statusUri);
            request.addHeader("Connection", "Close");
            request.setConfig(config);
            CloseableHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            if (debug)
                System.out.println("response:" + content);
            if (response.getCode() == 200) {
                return content;
            } else {
                throw new RuntimeException("Failed to get status for request " + statusUri + ": " +
                        response.getCode() + ": " + content);
            }
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    String parseAndGenerateOutput(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String status = jsonNode.get("status").asText();
        return switch (status) {
            case statusUnknown -> "File distribution status unknown: " + jsonNode.get("message").asText();
            case statusInProgress -> "File distribution in progress:\n" + inProgressOutput(jsonNode.get("hosts"));
            case statusFinished -> "File distribution finished";
            default -> throw new RuntimeException("Unknown status " + status);
        };
    }

    private URI createStatusApiUri() {
        String path = String.format("/application/v2/tenant/%s/application/%s/environment/%s/region/%s/instance/%s/filedistributionstatus",
                                    tenantName, applicationName, environment, region, instanceName);
        try {
            return new URIBuilder()
                    .setScheme("http")
                    .setHost("localhost")
                    .setPort(19071)
                    .setPath(path)
                    .addParameter("timeout", String.valueOf(timeout))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String inProgressOutput(JsonNode hosts) {
        ArrayList<String> statusPerHost = new ArrayList<>();
        for (JsonNode host : hosts) {
            String status = host.get("status").asText();
            StringBuilder sb = new StringBuilder(host.get("hostname").asText()).append(": ").append(status);
            switch (status) {
                case statusUnknown:
                    sb.append(" (").append(host.get("message").asText()).append(")");
                    break;
                case statusInProgress:
                    JsonNode fileReferencesArray = host.get("fileReferences");
                    int finished = 0;
                    for (JsonNode element : fileReferencesArray) {
                        for (Iterator<Map.Entry<String, JsonNode>> it = element.fields(); it.hasNext(); ) {
                            Map.Entry<String, JsonNode> fileReferenceStatus = it.next();
                            if (fileReferenceStatus.getValue().asDouble() == 1.0)
                                finished++;
                        }
                    }
                    sb.append(" (").append(finished).append(" of ").append(fileReferencesArray.size()).append(" finished)");
                    break;
                case statusFinished:
                    break; // Nothing to add
                default:
                    throw new RuntimeException("Unknown status " + status);
            }
            statusPerHost.add(sb.toString());
        }
        return String.join("\n", statusPerHost);
    }

    @Command(name = "vespa-status-filedistribution", description = "Tool for getting file distribution status.")
    public static class CommandLineArguments {

        static CommandLineArguments build(String[] args) {
            CommandLineArguments arguments = null;
            try {
                arguments = SingleCommand.singleCommand(CommandLineArguments.class).parse(args);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.err.println("Use --help to show usage.\n");
                System.exit(1);
            }

            if (arguments.helpOption.showHelpIfRequested()) {
                System.exit(0);
            }

            if (arguments.getTenantName() == null) {
                System.err.println("'--tenant' not set.");
                System.exit(1);
            }

            if (arguments.getApplicationName() == null) {
                System.err.println("'--application' not set.");
                System.exit(1);
            }

            return arguments;
        }

        @Inject
        HelpOption helpOption;

        @Option(name = {"--tenant"},
                description = "tenant name")
        private String tenantNameArg;

        @Option(name = {"--application"},
                description = "application name")
        private String applicationNameArg;

        @Option(name = {"--instance"},
                description = "instance name")
        private String instanceNameArg = "default";

        @Option(name = {"--environment"},
                description = "environment name")
        private String environmentArg = "prod";

        @Option(name = {"--region"},
                description = "region name")
        private String regionArg = "default";

        @Option(name = {"--timeout"},
                description = "The timeout (in seconds).")
        private double timeoutArg = 5;

        @Option(name = {"--debug"},
                description = "Print debug log.")
        private boolean debugArg;

        public String getTenantName() { return tenantNameArg; }

        public String getApplicationName() { return applicationNameArg; }

        public String getInstanceName() { return instanceNameArg; }

        public String getEnvironment() { return environmentArg; }

        public String getRegion() { return regionArg; }

        public double getTimeout() { return timeoutArg; }

        public boolean getDebugFlag() { return debugArg; }
    }

}
