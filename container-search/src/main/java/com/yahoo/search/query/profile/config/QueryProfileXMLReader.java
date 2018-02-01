// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.query.profile.DimensionValues;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A class which imports query profiles and types from XML files
 *
 * @author bratseth
 */
public class QueryProfileXMLReader {

    /**
     * Reads all query profile xml files in a given directory,
     * and all type xml files from the immediate subdirectory "types/" (if any)
     *
     * @throws RuntimeException if <code>directory</code> is not a readable directory, or if there is some error in the XML
     */
    public QueryProfileRegistry read(String directory) {
        List<NamedReader> queryProfileReaders = new ArrayList<>();
        List<NamedReader> queryProfileTypeReaders = new ArrayList<>();
        try {
            File dir = new File(directory);
            if ( ! dir.isDirectory() ) throw new IllegalArgumentException("Could not read query profiles: '" +
                                                                         directory + "' is not a valid directory.");

            for (File file : sortFiles(dir)) {
                if ( ! file.getName().endsWith(".xml")) continue;
                queryProfileReaders.add(new NamedReader(file.getName(),new FileReader(file)));
            }
            File typeDir=new File(dir,"types");
            if (typeDir.isDirectory()) {
                for (File file : sortFiles(typeDir)) {
                    if ( ! file.getName().endsWith(".xml")) continue;
                    queryProfileTypeReaders.add(new NamedReader(file.getName(),new FileReader(file)));
                }
            }

            return read(queryProfileTypeReaders,queryProfileReaders);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read query profiles from '" + directory + "'",e);
        }
        finally {
            closeAll(queryProfileReaders);
            closeAll(queryProfileTypeReaders);
        }
    }

    private List<File> sortFiles(File dir) {
        ArrayList<File> files = new ArrayList<>();
        files.addAll(Arrays.asList(dir.listFiles()));
        Collections.sort(files);
        return files;
    }

    private void closeAll(List<NamedReader> readers) {
        for (NamedReader reader : readers) {
            try { reader.close(); } catch (IOException e) { }
        }
    }

    /**
     * Read the XML file readers into a registry. This does not close the readers.
     * This method is used directly from the admin system.
     */
    public QueryProfileRegistry read(List<NamedReader> queryProfileTypeReaders, List<NamedReader> queryProfileReaders) {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        // Phase 1
        List<Element> queryProfileTypeElements = createQueryProfileTypes(queryProfileTypeReaders, registry.getTypeRegistry());
        List<Element> queryProfileElements = createQueryProfiles(queryProfileReaders, registry);

        // Phase 2
        fillQueryProfileTypes(queryProfileTypeElements, registry.getTypeRegistry());
        fillQueryProfiles(queryProfileElements, registry);
        return registry;
    }

    public List<Element> createQueryProfileTypes(List<NamedReader> queryProfileTypeReaders, QueryProfileTypeRegistry registry) {
        List<Element> queryProfileTypeElements = new ArrayList<>(queryProfileTypeReaders.size());
        for (NamedReader reader : queryProfileTypeReaders) {
            Element root = XML.getDocument(reader).getDocumentElement();
            if ( ! root.getNodeName().equals("query-profile-type")) {
                throw new IllegalArgumentException("Root tag in '" + reader.getName() +
                                                   "' must be 'query-profile-type', not '" + root.getNodeName() + "'");
            }

            String idString=root.getAttribute("id");
            if (idString == null || idString.equals(""))
                throw new IllegalArgumentException("'" + reader.getName() + "' has no 'id' attribute in the root element");
            ComponentId id = new ComponentId(idString);
            validateFileNameToId(reader.getName(),id,"query profile type");
            QueryProfileType type = new QueryProfileType(id);
            type.setMatchAsPath(XML.getChild(root,"match") != null);
            type.setStrict(XML.getChild(root,"strict") != null);
            registry.register(type);
            queryProfileTypeElements.add(root);
        }
        return queryProfileTypeElements;
    }

    public List<Element> createQueryProfiles(List<NamedReader> queryProfileReaders, QueryProfileRegistry registry) {
        List<Element> queryProfileElements = new ArrayList<>(queryProfileReaders.size());
        for (NamedReader reader : queryProfileReaders) {
            Element root = XML.getDocument(reader).getDocumentElement();
            if ( ! root.getNodeName().equals("query-profile")) {
                throw new IllegalArgumentException("Root tag in '" + reader.getName() +
                                                   "' must be 'query-profile', not '" + root.getNodeName() + "'");
            }

            String idString = root.getAttribute("id");
            if (idString == null || idString.equals(""))
                throw new IllegalArgumentException("Query profile '" + reader.getName() +
                                                   "' has no 'id' attribute in the root element");
            ComponentId id = new ComponentId(idString);
            validateFileNameToId(reader.getName(), id, "query profile");

            QueryProfile queryProfile = new QueryProfile(id);
            String typeId = root.getAttribute("type");
            if (typeId != null && ! typeId.equals("")) {
                QueryProfileType type = registry.getType(typeId);
                if (type == null)
                    throw new IllegalArgumentException("Query profile '" + reader.getName() +
                                                       "': Type id '" + typeId + "' can not be resolved");
                queryProfile.setType(type);
            }

            Element dimensions = XML.getChild(root,"dimensions");
            if (dimensions != null)
                queryProfile.setDimensions(toArray(XML.getValue(dimensions)));

            registry.register(queryProfile);
            queryProfileElements.add(root);
        }
        return queryProfileElements;
    }

    /** Throws an exception if the name is not corresponding to the id */
    private void validateFileNameToId(String actualName, ComponentId id, String artifactName) {
        String expectedCanonicalFileName = id.toFileName();
        String expectedAlternativeFileName = id.stringValue().replace(":", "-").replace("/", "_"); // legacy
        String fileName = new File(actualName).getName();
        fileName = stripXmlEnding(fileName);
        String canonicalFileName = ComponentId.fromFileName(fileName).toFileName();
        if ( ! canonicalFileName.equals(expectedCanonicalFileName) && ! canonicalFileName.equals(expectedAlternativeFileName))
            throw new IllegalArgumentException("The file name of " + artifactName + " '" + id +
                                               "' must be '" + expectedCanonicalFileName + ".xml' but was '" +
                                               actualName + "'");
    }

    private String stripXmlEnding(String fileName) {
        if ( ! fileName.endsWith(".xml"))
            throw new IllegalArgumentException("'" + fileName + "' should have a .xml ending");
        else
            return fileName.substring(0,fileName.length()-4);
    }

    private String[] toArray(String csv) {
        String[] array = csv.split(",");
        for (int i = 0; i < array.length; i++)
            array[i] = array[i].trim();
        return array;
    }

    public void fillQueryProfileTypes(List<Element> queryProfileTypeElements, QueryProfileTypeRegistry registry) {
        for (Element element : queryProfileTypeElements) {
            QueryProfileType type = registry.getComponent(new ComponentSpecification(element.getAttribute("id")).toId());
            try {
                readInheritedTypes(element, type, registry);
                readFieldDefinitions(element, type, registry);
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("Error reading " + type, e);
            }
        }
    }

    private void readInheritedTypes(Element element,QueryProfileType type, QueryProfileTypeRegistry registry) {
        String inheritedString = element.getAttribute("inherits");
        if (inheritedString == null || inheritedString.equals("")) return;
        for (String inheritedId : inheritedString.split(" ")) {
            inheritedId = inheritedId.trim();
            if (inheritedId.equals("")) continue;
            QueryProfileType inheritedType = registry.getComponent(inheritedId);
            if (inheritedType == null)
                throw new IllegalArgumentException("Could not resolve inherited query profile type '" + inheritedId);
            type.inherited().add(inheritedType);
        }
    }

    private void readFieldDefinitions(Element element, QueryProfileType type, QueryProfileTypeRegistry registry) {
        for (Element field : XML.getChildren(element,"field")) {
            String name = field.getAttribute("name");
            if (name == null || name.equals("")) throw new IllegalArgumentException("A field has no 'name' attribute");
            try {
                String fieldTypeName = field.getAttribute("type");
                if (fieldTypeName == null) throw new IllegalArgumentException("Field '" + field + "' has no 'type' attribute");
                FieldType fieldType=FieldType.fromString(fieldTypeName,registry);
                type.addField(new FieldDescription(name,
                                                   fieldType,
                                                   field.getAttribute("alias"),
                                                   getBooleanAttribute("mandatory", false, field),
                                                   getBooleanAttribute("overridable", true, field)),
                              registry);
            }
            catch(RuntimeException e) {
                throw new IllegalArgumentException("Invalid field '" + name + "'",e);
            }
        }
    }

    public void fillQueryProfiles(List<Element> queryProfileElements, QueryProfileRegistry registry) {
        for (Element element : queryProfileElements) {
            // Lookup by exact id
            QueryProfile profile = registry.getComponent(new ComponentSpecification(element.getAttribute("id")).toId());
            try {
                readInherited(element, profile, registry,null, profile.toString());
                readFields(element, profile, registry,null, profile.toString());
                readVariants(element, profile, registry);
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("Error reading " + profile, e);
            }
        }
    }

    private void readInherited(Element element, QueryProfile profile, QueryProfileRegistry registry,
                               DimensionValues dimensionValues, String sourceDescription) {
        String inheritedString = element.getAttribute("inherits");
        if (inheritedString == null || inheritedString.equals("")) return;
        for (String inheritedId : inheritedString.split(" ")) {
            inheritedId = inheritedId.trim();
            if (inheritedId.equals("")) continue;
            QueryProfile inheritedProfile = registry.getComponent(inheritedId);
            if (inheritedProfile == null)
                throw new IllegalArgumentException("Could not resolve inherited query profile '" +
                                                   inheritedId + "' in " + sourceDescription);
            profile.addInherited(inheritedProfile,dimensionValues);
        }
    }

    private void readFields(Element element,QueryProfile profile, QueryProfileRegistry registry,
                            DimensionValues dimensionValues, String sourceDescription) {
        List<KeyValue> references = new ArrayList<>();
        List<KeyValue> properties = new ArrayList<>();
        for (Element field : XML.getChildren(element,"field")) {
            String name = field.getAttribute("name");
            if (name == null || name.equals(""))
                throw new IllegalArgumentException("A field in " + sourceDescription + " has no 'name' attribute");
            try {
                Boolean overridable = getBooleanAttribute("overridable",null,field);
                if (overridable != null)
                    profile.setOverridable(name, overridable, null);

                Object fieldValue = readFieldValue(field, name, sourceDescription, registry);
                if (fieldValue instanceof QueryProfile)
                    references.add(new KeyValue(name, fieldValue));
                else
                    properties.add(new KeyValue(name, fieldValue));
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid field '" + name + "' in " + sourceDescription,e);
            }
        }
        // Must set references before properties
        for (KeyValue keyValue : references)
            profile.set(keyValue.getKey(), keyValue.getValue(), dimensionValues, registry);
        for (KeyValue keyValue : properties)
            profile.set(keyValue.getKey(), keyValue.getValue(), dimensionValues, registry);

    }

    private Object readFieldValue(Element field, String name, String targetDescription, QueryProfileRegistry registry) {
        Element ref = XML.getChild(field,"ref");
        if (ref != null) {
            String referencedName = XML.getValue(ref);
            QueryProfile referenced = registry.getComponent(referencedName);
            if (referenced == null)
                throw new IllegalArgumentException("Could not find query profile '" + referencedName + "' referenced as '" +
                                                   name + "' in " + targetDescription);
            return referenced;
        }
        else {
            return XML.getValue(field);
        }
    }

    private void readVariants(Element element, QueryProfile profile, QueryProfileRegistry registry) {
        for (Element queryProfileVariantElement : XML.getChildren(element,"query-profile")) { // A "virtual" query profile contained inside another
            List<String> dimensions = profile.getDimensions();
            if (dimensions == null)
                throw new IllegalArgumentException("Cannot create a query profile variant in " + profile +
                                                   ", as it has not declared any variable dimensions");
            String dimensionString = queryProfileVariantElement.getAttribute("for");
            String[] dimensionValueArray = makeStarsNull(toArray(dimensionString));
            if (dimensions.size()<dimensionValueArray.length)
                throw new IllegalArgumentException("Cannot create a query profile variant for '" + dimensionString +
                                                   "' as only " + dimensions.size() + " dimensions has been defined");
            DimensionValues dimensionValues = DimensionValues.createFrom(dimensionValueArray);

            String description = "variant '" + dimensionString + "' in " + profile.toString();
            readInherited(queryProfileVariantElement, profile, registry, dimensionValues, description);
            readFields(queryProfileVariantElement, profile, registry, dimensionValues, description);
        }
    }

    private String[] makeStarsNull(String[] strings) {
        for (int i = 0; i < strings.length; i++)
            if (strings[i].equals("*"))
                strings[i] = null;
        return strings;
    }

    /**
     * Returns true if the string is "true".
     * Returns false if the string is "false".
     * Returns <code>default</code> if the string is null or empty (this parameter may be null).
     *
     * @throws IllegalArgumentException if the string has any other value
     */
    private Boolean asBoolean(String s,Boolean defaultValue) {
        if (s == null) return defaultValue;
        if (s.isEmpty()) return defaultValue;
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        throw new IllegalArgumentException("Expected 'true' or 'false' but was'" + s + "'");
    }

    /** Returns the given attribute as a boolean, using the semantics of {@link #asBoolean} */
    private Boolean getBooleanAttribute(String attributeName, Boolean defaultValue, Element from) {
        try {
            return asBoolean(from.getAttribute(attributeName), defaultValue);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Attribute '" + attributeName, e);
        }
    }

    private static class KeyValue {

        private String key;
        private Object value;

        public KeyValue(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }

        public Object getValue() { return value; }

    }

}
