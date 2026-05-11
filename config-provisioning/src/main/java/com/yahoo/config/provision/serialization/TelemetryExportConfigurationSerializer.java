// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.serialization;

import com.yahoo.config.provision.TelemetryExportConfiguration;
import com.yahoo.config.provision.TelemetryExportConfiguration.Auth;
import com.yahoo.config.provision.TelemetryExportConfiguration.Exporter.ExporterType;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serializes {@link TelemetryExportConfiguration} to/from Slime and JSON.
 *
 * @author onur
 */
public class TelemetryExportConfigurationSerializer {

    private static final String exportersKey = "exporters";
    private static final String idKey = "id";
    private static final String typeKey = "type";
    private static final String endpointKey = "endpoint";
    private static final String projectKey = "project";
    private static final String authKey = "auth";
    private static final String vaultKey = "vault";
    private static final String secretNameKey = "secretName";
    private static final String headerKey = "header";
    private static final String usernameSecretNameKey = "usernameSecretName";
    private static final String passwordSecretNameKey = "passwordSecretName";
    private static final String metricSetsKey = "metricSets";
    private static final String logFileTypesKey = "logFileTypes";

    public static byte[] toJson(TelemetryExportConfiguration config) {
        Slime slime = new Slime();
        toSlime(config, slime.setObject());
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize TelemetryExportConfiguration", e);
        }
    }

    public static TelemetryExportConfiguration fromJson(byte[] json) {
        return fromSlime(SlimeUtils.jsonToSlime(json).get());
    }

    public static void toSlime(TelemetryExportConfiguration config, Cursor root) {
        Cursor exportersArray = root.setArray(exportersKey);
        for (var exporter : config.exporters()) {
            Cursor exporterObject = exportersArray.addObject();
            exporterObject.setString(idKey, exporter.id());
            exporterObject.setString(typeKey, exporter.type().name());
            exporter.endpoint().ifPresent(e -> exporterObject.setString(endpointKey, e));
            exporter.project().ifPresent(p -> exporterObject.setString(projectKey, p));
            exporter.auth().ifPresent(auth -> {
                Cursor authObject = exporterObject.setObject(authKey);
                authObject.setString(typeKey, auth.type());
                authObject.setString(vaultKey, auth.vault());
                auth.secretName().ifPresent(s -> authObject.setString(secretNameKey, s));
                auth.header().ifPresent(h -> authObject.setString(headerKey, h));
                auth.usernameSecretName().ifPresent(u -> authObject.setString(usernameSecretNameKey, u));
                auth.passwordSecretName().ifPresent(p -> authObject.setString(passwordSecretNameKey, p));
            });
            Cursor metricSetsArray = exporterObject.setArray(metricSetsKey);
            for (String metricSet : exporter.metricSets()) {
                metricSetsArray.addString(metricSet);
            }
            Cursor logFileTypesArray = exporterObject.setArray(logFileTypesKey);
            for (String logFileType : exporter.logFileTypes()) {
                logFileTypesArray.addString(logFileType);
            }
        }
    }

    public static TelemetryExportConfiguration fromSlime(Inspector root) {
        List<TelemetryExportConfiguration.Exporter> exporters = new ArrayList<>();
        root.field(exportersKey).traverse((ArrayTraverser) (i, exporterInspector) -> {
            String id = exporterInspector.field(idKey).asString();
            ExporterType type = ExporterType.valueOf(exporterInspector.field(typeKey).asString());
            String endpoint = optionalString(exporterInspector.field(endpointKey));
            String project = optionalString(exporterInspector.field(projectKey));

            Auth auth = null;
            Inspector authInspector = exporterInspector.field(authKey);
            if (authInspector.valid()) {
                auth = new Auth(
                        authInspector.field(typeKey).asString(),
                        authInspector.field(vaultKey).asString(),
                        Optional.ofNullable(optionalString(authInspector.field(secretNameKey))),
                        Optional.ofNullable(optionalString(authInspector.field(headerKey))),
                        Optional.ofNullable(optionalString(authInspector.field(usernameSecretNameKey))),
                        Optional.ofNullable(optionalString(authInspector.field(passwordSecretNameKey))));
            }

            List<String> metricSets = new ArrayList<>();
            exporterInspector.field(metricSetsKey).traverse((ArrayTraverser) (j, entry) -> metricSets.add(entry.asString()));

            List<String> logFileTypes = new ArrayList<>();
            exporterInspector.field(logFileTypesKey).traverse((ArrayTraverser) (j, entry) -> logFileTypes.add(entry.asString()));

            exporters.add(new TelemetryExportConfiguration.Exporter(id, type, Optional.ofNullable(endpoint), Optional.ofNullable(project),
                                                          Optional.ofNullable(auth), metricSets, logFileTypes));
        });
        if (exporters.isEmpty()) return TelemetryExportConfiguration.empty();
        return new TelemetryExportConfiguration(exporters);
    }

    private static String optionalString(Inspector inspector) {
        return inspector.valid() ? inspector.asString() : null;
    }

}
