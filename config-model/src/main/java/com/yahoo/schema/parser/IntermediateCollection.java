// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class wrapping parsing of schema files and holding a collection of
 * schemas in the intermediate format.
 *
 * @author arnej27959
 */
public class IntermediateCollection {

    private final DeployLogger deployLogger;
    private final ModelContext.Properties modelProperties;

    private final Map<String, ParsedSchema> parsedSchemas = new LinkedHashMap<>();

    IntermediateCollection() {
        this.deployLogger = new BaseDeployLogger();
        this.modelProperties = new TestProperties();
    }

    public IntermediateCollection(DeployLogger logger, ModelContext.Properties properties) {
        this.deployLogger = logger;
        this.modelProperties = properties;
    }

    public Map<String, ParsedSchema> getParsedSchemas() { return Collections.unmodifiableMap(parsedSchemas); }

    public ParsedSchema getParsedSchema(String name) { return parsedSchemas.get(name); }

    public ParsedSchema addSchemaFromString(String input) throws ParseException {
        var stream = new SimpleCharStream(input);
        var parser = new SchemaParser(stream, deployLogger, modelProperties);
        try {
            var schema = parser.schema();
            if (schema == null) {
                throw new IllegalArgumentException("No schema content");
            }
            if (parsedSchemas.containsKey(schema.name())) {
                throw new IllegalArgumentException("Duplicate schemas named " + schema.name());
            }
            parsedSchemas.put(schema.name(), schema);
            return schema;
        } catch (TokenMgrException e) {
            throw new ParseException("Unknown symbol: " + e.getMessage());
        } catch (ParseException pe) {
            throw new ParseException(stream.formatException(Exceptions.toMessageString(pe)));
        }
    }

    private String addSchemaFromStringWithFileName(String input, String fileName) throws ParseException {
        var parsed = addSchemaFromString(input);
        String nameFromFile = baseName(fileName);
        if (! parsed.name().equals(nameFromFile)) {
            throw new IllegalArgumentException("The file containing schema '"
                                               + parsed.name() + "' must be named '"
                                               + parsed.name() + ApplicationPackage.SD_NAME_SUFFIX
                                               + "', but is '" + stripDirs(fileName) + "'");
        }
        return parsed.name();
    }

    private String baseName(String filename) {
        int pos = filename.lastIndexOf('/');
        if (pos != -1) {
            filename = filename.substring(pos + 1);
        }
        pos = filename.lastIndexOf('.');
        if (pos != -1) {
            filename = filename.substring(0, pos);
        }
        return filename;
    }

    private String stripDirs(String filename) {
        int pos = filename.lastIndexOf('/');
        if (pos != -1) {
            return filename.substring(pos + 1);
        }
        return filename;
    }

    /** Parses a schema from the given reader and add result to collection. */
    public String addSchemaFromReader(NamedReader reader) {
        try {
            var nameParsed = addSchemaFromStringWithFileName(IOUtils.readAll(reader.getReader()), reader.getName());
            reader.close();
            return nameParsed;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed parsing schema from '" + reader.getName() + "'", e);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed reading from '" + reader.getName() + "'", e);
        }
    }

    /** For unit tests */
    public String addSchemaFromFile(String fileName) {
        try {
            var parsed = addSchemaFromString(IOUtils.readFile(new File(fileName)));
            return parsed.name();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed parsing schema from '" + fileName + "'", e);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed reading from '" + fileName + "'", e);
        }
    }

    /**
     * Parses a rank profile from the given reader and add to the schema identified by name.
     * note: the named schema must have been parsed already.
     */
    public void addRankProfileFile(String schemaName, NamedReader reader) throws ParseException {
        try {
            ParsedSchema schema = parsedSchemas.get(schemaName);
            if (schema == null) {
                throw new IllegalArgumentException("No schema named '" + schemaName + "'");
            }
            var stream = new SimpleCharStream(IOUtils.readAll(reader.getReader()));
            var parser = new SchemaParser(stream, deployLogger, modelProperties);
            try {
                parser.rankProfile(schema);
            } catch (ParseException pe) {
                throw new ParseException("Failed parsing rank-profile from '" + reader.getName() + "': " +
                                         stream.formatException(Exceptions.toMessageString(pe)));
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Failed reading from '" + reader.getName() + "': " + ex.getMessage());
        }
    }

    // for unit test
    void addRankProfileFile(String schemaName, String fileName) throws ParseException {
        try {
            var reader = IOUtils.createReader(fileName, "UTF-8");
            addRankProfileFile(schemaName, new NamedReader(fileName, reader));
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read file '" + fileName + "'", e);
        }
    }

    void resolveInternalConnections() {
        var resolver = new InheritanceResolver(parsedSchemas);
        resolver.resolveInheritance();
    }
}
