// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class wrapping parsing of schema files and holding a collection of
 * schemas in the intermediate format.
 *
 * @author arnej27959
 **/
public class IntermediateCollection {

    private final DeployLogger deployLogger;
    private final ModelContext.Properties modelProperties;

    private Map<String, ParsedSchema> parsedSchemas = new HashMap<>();

    IntermediateCollection() {
        this.deployLogger = new BaseDeployLogger();
        this.modelProperties = new TestProperties();
    }

    public IntermediateCollection(DeployLogger logger, ModelContext.Properties properties) {
        this.deployLogger = logger;
        this.modelProperties = properties;
    }

    public Map<String, ParsedSchema> getParsedSchemas() { return Map.copyOf(parsedSchemas); }

    public ParsedSchema getParsedSchema(String name) { return parsedSchemas.get(name); }

    ParsedSchema addSchemaFromString(String input) throws ParseException {
            var stream = new SimpleCharStream(input);
            var parser = new IntermediateParser(stream, deployLogger, modelProperties);
            var schema = parser.schema();
            if (parsedSchemas.containsKey(schema.name())) {
                throw new IllegalArgumentException("Duplicate schemas named: " + schema.name());
            }
            parsedSchemas.put(schema.name(), schema);
            return schema;
    }

    private void addSchemaFromStringWithFileName(String input, String fileName) throws ParseException {
        var parsed = addSchemaFromString(input);
        String nameFromFile = baseName(fileName);
        if (! parsed.name().equals(nameFromFile)) {
            throw new IllegalArgumentException("The file containing schema '"
                                               + parsed.name() + "' must be named '"
                                               + parsed.name() + ApplicationPackage.SD_NAME_SUFFIX
                                               + "', was '" + stripDirs(fileName) + "'");
        }
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

    /**
     * parse a schema from the given reader and add result to collection
     **/
    public void addSchemaFromReader(NamedReader reader) {
        try {
            addSchemaFromStringWithFileName(IOUtils.readAll(reader.getReader()), reader.getName());
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Failed reading from " + reader.getName() + ": " + ex.getMessage());
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Failed parsing schema from " + reader.getName() + ": " + ex.getMessage());
        }

    }

    /** for unit tests */
    public void addSchemaFromFile(String fileName) {
        try {
            addSchemaFromStringWithFileName(IOUtils.readFile(new File(fileName)), fileName);
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Could not read file " + fileName + ": " + ex.getMessage());
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Failed parsing schema file " + fileName + ": " + ex.getMessage());
        }
    }

    /**
     * parse a rank profile from the given reader and add to the schema identified by name.
     * note: the named schema must have been parsed already.
     **/
    public void addRankProfileFile(String schemaName, NamedReader reader) {
        try {
            ParsedSchema schema = parsedSchemas.get(schemaName);
            if (schema == null) {
                throw new IllegalArgumentException("No schema named: " + schemaName);
            }
            var stream = new SimpleCharStream(IOUtils.readAll(reader.getReader()));
            var parser = new IntermediateParser(stream, deployLogger, modelProperties);
            parser.rankProfile(schema);
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Failed reading from " + reader.getName() + ": " + ex.getMessage());
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Failed parsing rank-profile from " + reader.getName() + ": " + ex.getMessage());
        }
    }

    // for unit test
    void addRankProfileFile(String schemaName, String fileName) {
        try {
            var reader = IOUtils.createReader(fileName, "UTF-8");
            addRankProfileFile(schemaName, new NamedReader(fileName, reader));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Could not read file " + fileName + ": " + ex.getMessage());
        }
    }

    void resolveInternalConnections() {
        var resolver = new InheritanceResolver(parsedSchemas);
        resolver.resolveInheritance();
    }
}
