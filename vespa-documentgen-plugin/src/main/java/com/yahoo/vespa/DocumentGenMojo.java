// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa;

import com.yahoo.collections.Pair;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.OwnedStructDataType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.parser.ParseException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates Vespa document classes from schema files.
 *
 * @author Vegard Balgaard Havdal
 */
@Mojo(name = "document-gen", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class DocumentGenMojo extends AbstractMojo {

    private long newestModifiedTime = 0;

    private static final int STD_INDENT = 4;

    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    /**
     * Directory containing the schema files
     */
    @Parameter(defaultValue = ".", required = true)
    private File schemasDirectory;

    /**
     * Java package for generated classes
     */
    @Parameter(defaultValue = "com.yahoo.vespa.document", required = true)
    private String packageName;

    /**
     * User provided annotation types that the plugin should not generate types for. They should however be included in
     * ConcreteDocumentFactory.
     */
    @Parameter
    private List<Annotation> provided = new ArrayList<Annotation>();

    /**
     * Annotation types for which the generated class should be abstract
     */
    @Parameter
    private List<Annotation> abztract = new ArrayList<Annotation>();

    /**
     * Generate source to here.
     */
    @Parameter(
            property = "plugin.configuration.outputDirectory",
            defaultValue="${project.build.directory}/generated-sources/vespa-documentgen-plugin/",
            required = true)
    private File outputDirectory;

    private Map<String, Schema> searches;
    private Map<String, String> docTypes;
    private Map<String, String> structTypes;
    private Map<String, String> annotationTypes;

    void execute(File schemasDir, File outputDir, String packageName) {
        if ("".equals(packageName)) throw new IllegalArgumentException("You may not use empty package for generated types.");
        searches = new HashMap<>();
        docTypes = new HashMap<>();
        structTypes = new HashMap<>();
        annotationTypes = new HashMap<>();

        outputDir.mkdirs();
        ApplicationBuilder builder = buildSearches(schemasDir);

        boolean annotationsExported=false;
        for (NewDocumentType docType : builder.getModel().getDocumentManager().getTypes()) {
            if ( docType != VespaDocumentType.INSTANCE) {
                exportDocumentSources(outputDir, docType, packageName);

                for (AnnotationType annotationType : docType.getAllAnnotations()) {
                    if (provided(annotationType.getName())!=null) continue;
                    annotationsExported=true;
                    exportAnnotationSources(outputDir, annotationType, docType, packageName);
                }
            }
        }
        exportPackageInfo(outputDir, packageName);
        if (annotationsExported) exportPackageInfo(outputDir, packageName+".annotation");
        exportDocFactory(outputDir, packageName);
        if (project!=null) project.addCompileSourceRoot(outputDirectory.toString());
    }

    private ApplicationBuilder buildSearches(File sdDir) {
        File[] sdFiles = sdDir.listFiles((dir, name) -> name.endsWith(".sd"));
        ApplicationBuilder builder = new ApplicationBuilder(true);
        for (File f : sdFiles) {
            try {
                long modTime = f.lastModified();
                if (modTime > newestModifiedTime) {
                    newestModifiedTime = modTime;
                }
                builder.addSchemaFile(f.getAbsolutePath());
            } catch (ParseException | IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        builder.build(true);
        for (Schema schema : builder.getSchemaList() ) {
            this.searches.put(schema.getName(), schema);
        }
        return builder;
    }

    /**
     * Creates package-info.java, so that the package of the concrete doc types get exported from user's bundle.
     */
    private void exportPackageInfo(File outputDir, String packageName) {
        File dirForSources = new File(outputDir, packageName.replaceAll("\\.", "/"));
        dirForSources.mkdirs();
        File target = new File(dirForSources, "package-info.java");
        if (target.lastModified() > newestModifiedTime) {
            getLog().debug("No changes, not updating "+target);
            return;
        }
        try (Writer out = new FileWriter(target)) {
                out.write("@ExportPackage\n" +
                    "package "+packageName+";\n\n" +
                    "import com.yahoo.osgi.annotation.ExportPackage;\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The class for this type if provided, otherwise null
     */
    private String provided(String annotationType) {
        if (provided==null) return null;
        for (Annotation a : provided) {
            if (a.type.equals(annotationType)) return a.clazz;
        }
        return null;
    }

    private boolean isAbstract(String annotationType) {
        if (abztract==null) return false;
        for (Annotation a : abztract) {
            if (a.type.equals(annotationType)) return true;
        }
        return false;
    }

    private void exportDocFactory(File outputDir, String packageName) {
        File dirForSources = new File(outputDir, packageName.replaceAll("\\.", "/"));
        dirForSources.mkdirs();
        File target = new File(dirForSources, "ConcreteDocumentFactory.java");
        if (target.lastModified() > newestModifiedTime) {
            getLog().debug("No changes, not updating "+target);
            return;
        }
        try (Writer out = new FileWriter(target)) {
                out.write("package "+packageName+";\n\n" +
                    "/**\n" +
                    " *  Registry of generated concrete document, struct and annotation types.\n" +
                    " *\n" +
                    " *  Generated by vespa-documentgen-plugin, do not edit.\n" +
                    " *  Date: "+new Date()+"\n" +
                    " */\n");
            out.write("@com.yahoo.document.Generated\n");
            out.write("public class ConcreteDocumentFactory extends com.yahoo.docproc.AbstractConcreteDocumentFactory {\n");
            out.write(ind()+"private static java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.Document>> dTypes = new java.util.HashMap<java.lang.String, java.lang.Class<? extends com.yahoo.document.Document>>();\n");
            out.write(ind()+"private static java.util.Map<java.lang.String, com.yahoo.document.DocumentType> docTypes = new java.util.HashMap<>();\n");
            out.write(ind()+"private static java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.datatypes.Struct>> sTypes = new java.util.HashMap<java.lang.String, java.lang.Class<? extends com.yahoo.document.datatypes.Struct>>();\n");
            out.write(ind()+"private static java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.annotation.Annotation>> aTypes = new java.util.HashMap<java.lang.String, java.lang.Class<? extends com.yahoo.document.annotation.Annotation>>();\n");
            out.write(ind()+"static {\n");
            for (Map.Entry<String, String> e : docTypes.entrySet()) {
                String docTypeName = e.getKey();
                String fullClassName = e.getValue();
                out.write(ind(2)+"dTypes.put(\""+docTypeName+"\","+fullClassName+".class);\n");
            }
            for (Map.Entry<String, String> e : docTypes.entrySet()) {
                String docTypeName = e.getKey();
                String fullClassName = e.getValue();
                out.write(ind(2)+"docTypes.put(\""+docTypeName+"\","+fullClassName+".type);\n");
            }
            for (Map.Entry<String, String> e : structTypes.entrySet()) {
                String structTypeName = e.getKey();
                String fullClassName = e.getValue();
                out.write(ind(2)+"sTypes.put(\""+structTypeName+"\","+fullClassName+".class);\n");
            }
            for (Map.Entry<String, String> e : annotationTypes.entrySet()) {
                String annotationTypeName = e.getKey();
                String fullClassName = e.getValue();
                out.write(ind(2)+"aTypes.put(\""+annotationTypeName+"\","+fullClassName+".class);\n");
            }
            for (Annotation a : provided) {
                out.write(ind(2)+"aTypes.put(\""+a.type+"\","+a.clazz+".class);\n");
            }
            out.write(ind()+"}\n\n");

            out.write(
                    ind()+"public static final java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.Document>> documentTypes = java.util.Collections.unmodifiableMap(dTypes);\n" +
                    ind()+"public static final java.util.Map<java.lang.String, com.yahoo.document.DocumentType> documentTypeObjects = java.util.Collections.unmodifiableMap(docTypes);\n" +
                    ind()+"public static final java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.datatypes.Struct>> structTypes = java.util.Collections.unmodifiableMap(sTypes);\n" +
                    ind()+"public static final java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.annotation.Annotation>> annotationTypes = java.util.Collections.unmodifiableMap(aTypes);\n\n");
            out.write(
                    ind()+"@Override public java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.Document>> documentTypes() { return ConcreteDocumentFactory.documentTypes; }\n" +
                    ind()+"@Override public java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.datatypes.Struct>> structTypes() { return ConcreteDocumentFactory.structTypes; }\n" +
                    ind()+"@Override public java.util.Map<java.lang.String, java.lang.Class<? extends com.yahoo.document.annotation.Annotation>> annotationTypes() { return ConcreteDocumentFactory.annotationTypes; }\n\n");

            out.write(
                    ind()+"/**\n" +
                    ind()+" * Returns a new Document which will be of the correct concrete type and a copy of the given StructFieldValue.\n" +
                    ind()+" */\n" +
                    ind()+"@Override public com.yahoo.document.Document getDocumentCopy(java.lang.String type, com.yahoo.document.datatypes.StructuredFieldValue src, com.yahoo.document.DocumentId id) {\n");
                    for (Map.Entry<String, String> e : docTypes.entrySet()) {
                        out.write(ind(2)+"if (\""+e.getKey()+"\".equals(type)) return new "+e.getValue()+"(src, id);\n");
                    }
                    out.write(ind(2)+"throw new java.lang.IllegalArgumentException(\"No such concrete document type: \"+type);\n");
                    out.write(ind()+"}\n\n");

            // delete, bad to use reflection?
            out.write(
                    ind()+"/**\n" +
                    ind()+" * Returns a new Document which will be of the correct concrete type.\n" +
                    ind()+" */\n" +
                    ind()+"public static com.yahoo.document.Document getDocument(java.lang.String type, com.yahoo.document.DocumentId id) {\n" +
                    ind(2)+"if (!ConcreteDocumentFactory.documentTypes.containsKey(type)) throw new java.lang.IllegalArgumentException(\"No such concrete document type: \"+type);\n" +
                    ind(2)+"try {\n" +
                    ind(3)+"return ConcreteDocumentFactory.documentTypes.get(type).getConstructor(com.yahoo.document.DocumentId.class).newInstance(id);\n" +
                    ind(2)+"} catch (java.lang.Exception ex) { throw new java.lang.RuntimeException(ex); }\n" +
                    ind()+"}\n\n");

            // delete, bad to use reflection?
            out.write(
                    ind()+"/**\n" +
                    ind()+" * Returns a new Struct which will be of the correct concrete type.\n" +
                    ind()+" */\n" +
                    ind()+"public static com.yahoo.document.datatypes.Struct getStruct(java.lang.String type) {\n" +
                    ind(2)+"if (!ConcreteDocumentFactory.structTypes.containsKey(type)) throw new java.lang.IllegalArgumentException(\"No such concrete struct type: \"+type);\n" +
                    ind(2)+"try {\n" +
                    ind(3)+"return ConcreteDocumentFactory.structTypes.get(type).getConstructor().newInstance();\n" +
                    ind(2)+"} catch (java.lang.Exception ex) { throw new java.lang.RuntimeException(ex); }\n" +
                    ind()+"}\n\n");

            // delete, bad to use reflection?
            out.write(
                    ind()+"/**\n" +
                    ind()+" * Returns a new Annotation which will be of the correct concrete type.\n" +
                    ind()+" */\n" +
                    ind()+"public static com.yahoo.document.annotation.Annotation getAnnotation(java.lang.String type) {\n" +
                    ind(2)+"if (!ConcreteDocumentFactory.annotationTypes.containsKey(type)) throw new java.lang.IllegalArgumentException(\"No such concrete annotation type: \"+type);\n" +
                    ind(2)+"try {\n" +
                    ind(3)+"return ConcreteDocumentFactory.annotationTypes.get(type).getConstructor().newInstance();\n" +
                    ind(2)+"} catch (java.lang.Exception ex) { throw new java.lang.RuntimeException(ex); }\n" +
                    ind()+"}\n\n");

            out.write("}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void exportAnnotationSources(File outputDir, AnnotationType annType, NewDocumentType docType, String packageName) {
        File dirForSources = new File(outputDir, packageName.replaceAll("\\.", "/")+"/annotation/");
        dirForSources.mkdirs();
        String className = className(annType.getName());
        File target = new File(dirForSources, className+".java");
        if (target.lastModified() > newestModifiedTime) {
            getLog().debug("No changes, not updating "+target);
            return;
        }
        try (Writer out = new FileWriter(target)) {
                out.write("package "+packageName+".annotation;\n\n" +
                    "import "+packageName+".ConcreteDocumentFactory;\n" +
                    exportInnerImportsFromDocAndSuperTypes(docType, packageName) +
                    exportImportProvidedAnnotationRefs(annType) +
                    "/**\n" +
                    " *  Generated by vespa-documentgen-plugin, do not edit.\n" +
                    " *  Input annotation type: "+annType.getName()+"\n" +
                    " *  Date: "+new Date()+"\n" +
                    " */\n" +
                    "@com.yahoo.document.Generated\n" +
                    "public "+annTypeModifier(annType)+"class "+className+" extends "+getParentAnnotationType(annType)+" {\n\n");
            if (annType.getDataType() instanceof StructDataType) {
                out.write(ind() + "public "+className+"() {\n" +
                    ind(2) + "setType(new com.yahoo.document.annotation.AnnotationType(\""+annType.getName()+"\", Fields.type));\n" +
                    ind()+"}\n\n");
                StructDataType annStruct = (StructDataType)annType.getDataType();
                StructDataType annStructTmp = new StructDataType("fields"); // Change the type name
                annStructTmp.assign(annStruct);
                Collection<DataType> tmpList = new ArrayList<>();
                tmpList.add(annStructTmp);
                exportStructTypes(tmpList, out, 1, null);
                exportFieldsAndAccessors(className, annStruct.getFieldsThisTypeOnly(), out, 1, false);
                out.write(ind()+"@Override public boolean hasFieldValue() { return true; }\n\n");
                out.write(ind()+"@Override public com.yahoo.document.datatypes.FieldValue getFieldValue() {\n");
                out.write(ind(2)+"com.yahoo.document.datatypes.Struct ret = new Fields();\n");

                for (Field f : annStruct.getFields()) {
                    out.write(
                            ind(2)+"if ("+getter(f.getName()) +"()!=null) {\n" +
                            ind(3)+"com.yahoo.document.Field f = ret.getField(\"" + f.getName() + "\");\n" +
                            ind(3)+"com.yahoo.document.datatypes.FieldValue fv = f.getDataType().createFieldValue(" + getter(f.getName()) + "());\n" +
                            ind(3)+"ret.setFieldValue(f, fv);\n" +
                            ind(2)+"}\n");
                }
                out.write(ind(2)+"return ret;\n");
                out.write(ind()+"}\n\n");

            } else {
                out.write(ind() + "public "+className+"() { setType(new com.yahoo.document.annotation.AnnotationType(\""+annType.getName()+"\")); } \n\n");
                out.write(ind()+"@Override public boolean hasFieldValue() { return false; }\n\n");
                out.write(ind()+"@Override public com.yahoo.document.datatypes.FieldValue getFieldValue() { return null; }\n\n");
            }
            out.write("}\n");
            annotationTypes.put(annType.getName(), packageName+".annotation."+className);
        } catch (IOException e) {
            throw new RuntimeException("Could not export sources for annotation type '"+annType.getName()+"'", e);
        }
    }

    /**
     * Handle the case of an annotation reference with a type that is user provided, we need to know the class name then
     */
    private String exportImportProvidedAnnotationRefs(AnnotationType annType) {
        if (annType.getDataType()==null) return "";
        StringBuilder ret = new StringBuilder();
        for (Field f : ((StructDataType)annType.getDataType()).getFields()) {
            if (f.getDataType() instanceof AnnotationReferenceDataType) {
                AnnotationReferenceDataType refType = (AnnotationReferenceDataType) f.getDataType();
                AnnotationType referenced = refType.getAnnotationType();
                String providedClass = provided(referenced.getName());
                if (providedClass!=null) {
                    // Annotationreference is to a type that is user-provided
                    ret.append("import ").append(providedClass).append(";\n");
                }
            }
        }
        return ret.toString();
    }

    private String annTypeModifier(AnnotationType annType) {
        if (isAbstract(annType.getName())) return "abstract ";
        return "";
    }

    private static String exportInnerImportsFromDocAndSuperTypes(NewDocumentType docType, String packageName) {
        String ret = "";
        ret = ret + "import "+packageName+"."+className(docType.getName())+".*;\n";
        ret = ret + exportInnerImportsFromSuperTypes(docType, packageName);
        return ret;
    }

    private static String exportInnerImportsFromSuperTypes(NewDocumentType docType, String packageName) {
        StringBuilder ret = new StringBuilder();
        for (NewDocumentType inherited : docType.getInherited()) {
            if (inherited.getName().equals("document")) continue;
            ret.append("import ").append(packageName).append(".").append(className(inherited.getName())).append(".*;\n");
        }
        return ret.toString();
    }

    private String getParentAnnotationType(AnnotationType annType) {
        if (annType.getInheritedTypes().isEmpty()) return "com.yahoo.document.annotation.Annotation";
        String superType = annType.getInheritedTypes().iterator().next().getName();
        String providedSuperType = provided(superType);
        if (providedSuperType!=null) return providedSuperType;
        return className(annType.getInheritedTypes().iterator().next().getName());
    }

    private void exportDocumentSources(File outputDir, NewDocumentType docType, String packageName) {
        File dirForSources = new File(outputDir, packageName.replaceAll("\\.", "/"));
        dirForSources.mkdirs();
        File target = new File(dirForSources, className(docType.getName())+".java");
        if (target.lastModified() > newestModifiedTime) {
            getLog().debug("No changes, not updating "+target);
            return;
        }
        try (Writer doc = new FileWriter(target)) {
            exportDocumentClass(docType, doc, packageName);
        } catch (IOException e) {
            throw new RuntimeException("Could not export sources for document type '"+docType.getName()+"'", e);
        }
    }

    /**
     * Generates the .java file for the Document subclass for the given document type
     */
    private void exportDocumentClass(NewDocumentType docType, Writer out, String packageName) throws IOException {
        String className = className(docType.getName());
        Pair<String, Boolean> extendInfo = javaSuperType(docType);
        String superType = extendInfo.getFirst();
        Boolean multiExtends = extendInfo.getSecond();
        out.write(
                "package "+packageName+";\n\n" +
                exportInnerImportsFromSuperTypes(docType, packageName) +
                "/**\n" +
                " *  Generated by vespa-documentgen-plugin, do not edit.\n" +
                " *  Input document type: "+docType.getName()+"\n" +
                " *  Date: "+new Date()+"\n" +
                " */\n" +
                "@com.yahoo.document.Generated\n" +
                "@SuppressWarnings(\"unchecked\")\n" +
                "public class "+className+" extends "+superType+" {\n\n"+
                ind(1)+"/** The doc type of this.*/\n" +
                ind(1)+"public static final com.yahoo.document.DocumentType type = getDocumentType();\n\n");

        // Constructor
        out.write(
                ind(1)+"public "+className+"(com.yahoo.document.DocumentId docId) {\n" +
                ind(2)+"super("+className+".type, docId);\n" +
                ind(1)+"}\n\n");
        out.write(
                ind(1)+"protected "+className+"(com.yahoo.document.DocumentType type, com.yahoo.document.DocumentId docId) {\n" +
                ind(2)+"super(type, docId);\n" +
                ind(1)+"}\n\n");
        // isGenerated()
        out.write(ind(1)+"@Override protected boolean isGenerated() { return true; }\n\n");

        Collection<Field> allUniqueFields = getAllUniqueFields(multiExtends, docType.getAllFields());
        exportExtendedStructTypeGetter(className, docType.getName(), docType.getInherited(), allUniqueFields, docType.getFieldSets(),
                docType.getImportedFieldNames(), out, 1, "getDocumentType", "com.yahoo.document.DocumentType");
        exportCopyConstructor(className, out, 1, true);

        exportFieldsAndAccessors(className, "com.yahoo.document.Document".equals(superType) ? allUniqueFields : docType.getFields(), out, 1, true);
        exportDocumentMethods(allUniqueFields, out, 1);
        exportHashCode(allUniqueFields, out, 1, "(getDataType() != null ? getDataType().hashCode() : 0) + getId().hashCode()");
        exportEquals(className, allUniqueFields, out, 1);
        Set<DataType> exportedStructs = exportStructTypes(docType.getTypes(), out, 1, null);
        if (hasAnyPositionField(allUniqueFields)) {
            exportedStructs = exportStructTypes(List.of(PositionDataType.INSTANCE), out, 1, exportedStructs);
        }
        docTypes.put(docType.getName(), packageName+"."+className);
        for (DataType exportedStruct : exportedStructs) {
            String fullName = packageName+"."+className+"."+className(exportedStruct.getName());
            structTypes.put(exportedStruct.getName(), fullName);
            if (exportedStruct instanceof OwnedStructDataType) {
                var owned = (OwnedStructDataType) exportedStruct;
                structTypes.put(owned.getUniqueName(), fullName);
            }
        }
        out.write("}\n");
    }

    private static boolean hasAnyPositionDataType(DataType dt) {
        if (PositionDataType.INSTANCE.equals(dt)) {
            return true;
        } else if (dt instanceof CollectionDataType) {
            return hasAnyPositionDataType(((CollectionDataType)dt).getNestedType());
        } else if (dt instanceof StructuredDataType) {
            return hasAnyPositionField(((StructuredDataType)dt).getFields());
        } else {
            return false;
        }
    }

    private static boolean hasAnyPositionField(Collection<Field> fields) {
        for (Field f : fields) {
            if (hasAnyPositionDataType(f.getDataType())) {
                return true;
            }
        }
        return false;
    }

    private Collection<Field> getAllUniqueFields(Boolean multipleInheritance, Collection<Field> allFields) {
        if (multipleInheritance) {
            Map<String, Field> seen = new HashMap<>();
            List<Field> unique = new ArrayList<>(allFields.size());
            for (Field f : allFields) {
                if (seen.containsKey(f.getName())) {
                    if ( ! f.equals(seen.get(f.getName()))) {
                        throw new IllegalArgumentException("Field '" + f.getName() + "' has conflicting definitions in multiple inheritance." +
                                "First defined as '" + seen.get(f.getName()) + "', then as '" + f + "'.");
                    }
                } else {
                    unique.add(f);
                    seen.put(f.getName(), f);
                }
            }
            return unique;
        }
        return allFields;
    }

    /**
     * The Java class the class of the given type should inherit from. If the input type inherits from _one_
     * other type, use that, otherwise Document.
     */
    private static Pair<String,Boolean> javaSuperType(NewDocumentType docType) {
        String ret = "com.yahoo.document.Document";
        Collection<NewDocumentType> specInheriteds = specificInheriteds(docType);
        boolean singleExtends = singleInheritance(specInheriteds);
        if (!specInheriteds.isEmpty() && singleExtends) ret = className(specInheriteds.iterator().next().getName());
        return new Pair<>(ret, !singleExtends);
    }

    private static boolean singleInheritance(Collection<NewDocumentType> specInheriteds) {
        if (specInheriteds.isEmpty()) return true;
        if (specInheriteds.size()>1) return false;
        return singleInheritance(specificInheriteds(specInheriteds.iterator().next()));
    }

    /**
     * The inherited types that are not Document
     * @return collection of specific inherited types
     */
    private static Collection<NewDocumentType> specificInheriteds(NewDocumentType type) {
        List<NewDocumentType> ret = new ArrayList<>();
        for (NewDocumentType t : type.getInherited()) {
            if (!"document".equals(t.getName())) ret.add(t);
        }
        return ret;
    }

    /**
     * Exports the copy constructor.
     *
     * NOTE: This is important, the docproc framework uses that constructor.
     */
    private static void exportCopyConstructor(String className, Writer out, int ind, boolean docId) throws IOException {
        out.write(
                ind(ind)+"/**\n"+
                ind(ind)+" * Constructs a "+className+" by taking a deep copy of the provided StructuredFieldValue.\n" +
                ind(ind)+" */\n");
        if (docId) {
            out.write(ind(ind)+"public "+className+"(com.yahoo.document.datatypes.StructuredFieldValue src, com.yahoo.document.DocumentId docId) {\n"+
                    ind(ind+1)+"super("+className+".type,docId);\n");
        } else {
            out.write(ind(ind)+"public "+className+"(com.yahoo.document.datatypes.StructuredFieldValue src) {\n"+
                    ind(ind+1)+"super("+className+".type);\n");
        }
        out.write(ind() + "ConcreteDocumentFactory factory = new ConcreteDocumentFactory();\n");
        out.write(
                ind(ind+1)+"for (java.util.Iterator<java.util.Map.Entry<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue>>i=src.iterator() ; i.hasNext() ; ) {\n" +
                ind(ind+2)+"java.util.Map.Entry<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue> e = i.next();\n" +
                ind(ind+2)+"com.yahoo.document.Field f = e.getKey();\n" +
                ind(ind+2)+"com.yahoo.document.datatypes.FieldValue fv = e.getValue();\n" +
                ind(ind+2)+"setFieldValue(f, factory.optionallyUpgrade(f, fv));\n" +
                ind(ind+1)+"}\n"+
                ind(ind)+"}\n\n");
    }

    private static void addExtendedField(String className, Field f, Writer out, int ind) throws IOException {
        out.write(ind(ind)+ "ret.addField(new com.yahoo.document.ExtendedField(\""+f.getName()+"\", " + toJavaReference(f.getDataType()) + ",\n");
        out.write(ind(ind+1) + "new com.yahoo.document.ExtendedField.Extract() {\n");
        out.write(ind(ind+2) + "public Object get(com.yahoo.document.datatypes.StructuredFieldValue doc) {return ((" + className + ")doc)." + getter(f.getName()) + "(); }\n");
        out.write(ind(ind+2) + "public void set(com.yahoo.document.datatypes.StructuredFieldValue doc, Object value) { ((" + className + ")doc)." + setter(f.getName())+"((" + toJavaType(f.getDataType()) + ")value); }\n");
        out.write(ind(ind+1) + "}\n");
        out.write(ind(ind) + "));\n");
    }
    private static void addExtendedStringField(String className, Field f, Writer out, int ind) throws IOException {
        out.write(ind(ind)+ "ret.addField(new com.yahoo.document.ExtendedStringField(\""+f.getName()+"\", " + toJavaReference(f.getDataType()) + ",\n");
        out.write(ind(ind+1) + "new com.yahoo.document.ExtendedField.Extract() {\n");
        out.write(ind(ind+2) + "public Object get(com.yahoo.document.datatypes.StructuredFieldValue doc) {return ((" + className + ")doc)." + getter(f.getName()) + "(); }\n");
        out.write(ind(ind+2) + "public void set(com.yahoo.document.datatypes.StructuredFieldValue doc, Object value) { ((" + className + ")doc)." + setter(f.getName())+"((" + toJavaType(f.getDataType()) + ")value); }\n");
        out.write(ind(ind+1) + "},\n");
        out.write(ind(ind+1) + "new com.yahoo.document.ExtendedStringField.ExtractSpanTrees() {\n");
        out.write(ind(ind+2) + "public java.util.Map<java.lang.String,com.yahoo.document.annotation.SpanTree> get(com.yahoo.document.datatypes.StructuredFieldValue doc) {return ((" + className + ")doc)." + spanTreeGetter(f.getName()) + "(); }\n");
        out.write(ind(ind+2) + "public void set(com.yahoo.document.datatypes.StructuredFieldValue doc, java.util.Map<java.lang.String,com.yahoo.document.annotation.SpanTree> value) { ((" + className + ")doc)." + spanTreeSetter(f.getName()) + "(value); }\n");
        out.write(ind(ind+1) + "}\n");
        out.write(ind(ind) + "));\n");
    }
    private static void exportFieldSetDefinition(Set<FieldSet> fieldSets, Writer out, int ind) throws IOException {
        out.write(ind(ind) + "java.util.Map<java.lang.String, java.util.Collection<java.lang.String>> fieldSets = new java.util.HashMap<>();\n");
        for (FieldSet fieldSet : fieldSets) {
            out.write(ind(ind) + "fieldSets.put(\"" + fieldSet.getName() + "\", java.util.Arrays.asList(");
            int count = 0;
            for (String field : fieldSet.getFieldNames()) {
                out.write("\"" + field + "\"");
                if (++count != fieldSet.getFieldNames().size()) {
                    out.write(",");
                }
            }
            out.write("));\n");
        }
        out.write(ind(ind) + "ret.addFieldSets(fieldSets);\n");
    }
    private static void exportImportedFields(Set<String> importedFieldNames, Writer out, int ind) throws IOException {
        out.write(ind(ind) + "java.util.Set<java.lang.String> importedFieldNames = new java.util.HashSet<>();\n");
        for (String importedField : importedFieldNames) {
            out.write(ind(ind) + "importedFieldNames.add(\"" + importedField + "\");\n");
        }
    }
    private static void exportExtendedStructTypeGetter(String className, String name, Collection<NewDocumentType> parentTypes,
                                                       Collection<Field> fields, Set<FieldSet> fieldSets,
                                                       Set<String> importedFieldNames, Writer out, int ind,
                                                       String methodName, String retType) throws IOException {
        out.write(ind(ind)+"private static "+retType+" "+methodName+"() {\n");
        String bodyIndent = ind(ind + 1);
        if (importedFieldNames != null) {
            exportImportedFields(importedFieldNames, out, ind + 1);
            out.write(bodyIndent+retType+" ret = new "+retType+"(\"" + name + "\", importedFieldNames);\n");
        } else {
            out.write(bodyIndent+retType+" ret = new "+retType+"(\""+name+"\");\n");
        }
        for (Field f : fields) {
            if (f.getDataType().equals(DataType.STRING)) {
                addExtendedStringField(className, f, out, ind + 1);
            } else {
                addExtendedField(className, f, out, ind + 1);
            }
        }
        if (fieldSets != null) {
            exportFieldSetDefinition(fieldSets, out, ind+1);
        }
        for (NewDocumentType parentType : parentTypes) {
            if (!parentType.getName().equals("document")) {
                out.write("%sret.inherit(%s.type);\n".formatted(bodyIndent, className(parentType.getName())));
            }
        }

        out.write(bodyIndent+"return ret;\n");
        out.write(ind(ind)+"}\n\n");
    }

    /**
     * Exports the necessary overridden methods from Document/StructuredFieldValue
     */
    private static void exportDocumentMethods(Collection<Field> fieldSet, Writer out, int ind) throws IOException {
        exportGetFieldCount(fieldSet, out, ind);
        exportGetField(out, ind);
        exportGetFieldValue(fieldSet, out, ind);
        exportSetFieldValue(out, ind);
        exportRemoveFieldValue(out, ind);
        exportIterator(fieldSet, out, ind);
        exportClear(fieldSet, out, ind);

    }

    private static void exportEquals(String className, Collection<Field> fieldSet, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public boolean equals(Object o) {\n");
        out.write(ind(ind+1)+"if (!(o instanceof "+className+")) return false;\n");
        out.write(ind(ind+1)+className+" other = ("+className+")o;\n");
        for (Field field: fieldSet) {
            out.write(ind(ind+1)+"if ("+getter(field.getName())+"()==null) { if (other."+getter(field.getName())+"()!=null) return false; }\n");
            out.write(ind(ind+2)+"else if (!("+getter(field.getName())+"().equals(other."+getter(field.getName())+"()))) return false;\n");
        }
        out.write(ind(ind+1)+"return true;\n");
        out.write(ind(ind)+"}\n\n");
    }

    private static void exportHashCode(Collection<Field> fieldSet, Writer out, int ind, String hcBase) throws IOException {
        out.write(ind(ind)+"@Override public int hashCode() {\n");
        out.write(ind(ind+1)+"int hc = "+hcBase+";\n");
        for (Field field: fieldSet) {
            out.write(ind(ind+1)+"hc += "+getter(field.getName())+"()!=null ? "+getter(field.getName())+"().hashCode() : 0;\n");
        }
        out.write(ind(ind+1)+"return hc;\n");
        out.write(ind(ind)+"}\n\n");
    }

    private static void exportClear(Collection<Field> fieldSet, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public void clear() {\n");
        for (Field field: fieldSet) {
            out.write(ind(ind+1)+setter(field.getName())+"(null);\n");
        }
        out.write(ind(ind)+"}\n\n");
    }

    private static void exportIterator(Collection<Field> fieldSet, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public java.util.Iterator<java.util.Map.Entry<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue>> iterator() {\n");
        out.write(ind(ind+1)+"java.util.Map<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue> ret = new java.util.HashMap<>();\n");
        for (Field field: fieldSet) {
            String name = field.getName();
            out.write(ind(ind+1)+"if ("+getter(name)+"()!=null) {\n");
            out.write(ind(ind+2)+"com.yahoo.document.Field f = getField(\""+name+"\");\n");
            out.write(ind(ind+2)+"ret.put(f, ((com.yahoo.document.ExtendedField)f).getFieldValue(this));\n" + ind(ind+1)+"}\n");
        }
        out.write(ind(ind+1)+"return ret.entrySet().iterator();\n" +
                ind(ind)+"}\n\n");
    }

    private static void exportRemoveFieldValue(Writer out, int ind) throws IOException {
        out.write(ind(ind) + "@Override public com.yahoo.document.datatypes.FieldValue removeFieldValue(com.yahoo.document.Field field) {\n");
        out.write(ind(ind+1) + "if (field==null) return null;\n");
        out.write(ind(ind+1) + "com.yahoo.document.ExtendedField ef = ensureExtended(field);\n");
        out.write(ind(ind+1) + "return (ef != null) ? ef.setFieldValue(this, null) : super.removeFieldValue(field);\n");
        out.write(ind(ind) + "}\n");
    }

    private static void exportSetFieldValue(Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public com.yahoo.document.datatypes.FieldValue setFieldValue(com.yahoo.document.Field field, com.yahoo.document.datatypes.FieldValue value) {\n");
        out.write(ind(ind+1)+"com.yahoo.document.ExtendedField ef = ensureExtended(field);\n");
        out.write(ind(ind+1)+"return (ef != null) ? ef.setFieldValue(this, value) : super.setFieldValue(field, value);\n");
        out.write(ind(ind)+"}\n\n");
    }

    private static void exportGetFieldValue(Collection<Field> fieldSet, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public com.yahoo.document.datatypes.FieldValue getFieldValue(com.yahoo.document.Field field) {\n");
        out.write(ind(ind+1)+"if (field==null) return null;\n");
        out.write(ind(ind+1)+"if (field.getDataType() instanceof com.yahoo.document.StructDataType) {\n");
        for (Field field: fieldSet) {
            String name = field.getName();
            if (field.getDataType() instanceof StructDataType) {
                out.write(ind(ind+2)+"if (\""+name+"\".equals(field.getName())) return "+name+";\n");
            }
        }
        out.write(ind(ind+1)+"}\n");

        out.write(ind(ind+1)+"com.yahoo.document.ExtendedField ef = ensureExtended(field);\n");
        out.write(ind(ind+1)+"return (ef != null) ? ef.getFieldValue(this) : super.getFieldValue(field);\n");
        out.write(ind(ind)+"}\n\n");
    }

    private static void exportGetField(Writer out, int ind) throws IOException {
        out.write(ind(ind) + "private com.yahoo.document.ExtendedStringField ensureExtendedString(com.yahoo.document.Field f) {\n");
        out.write(ind(ind+1) + "return (com.yahoo.document.ExtendedStringField)((f instanceof com.yahoo.document.ExtendedStringField) ? f : getField(f.getName()));\n");
        out.write(ind(ind) + "}\n\n");
        out.write(ind(ind) + "private com.yahoo.document.ExtendedField ensureExtended(com.yahoo.document.Field f) {\n");
        out.write(ind(ind+1) + "return (com.yahoo.document.ExtendedField)((f instanceof com.yahoo.document.ExtendedField) ? f : getField(f.getName()));\n");
        out.write(ind(ind) + "}\n\n");
        out.write(ind(ind)   + "@Override public com.yahoo.document.Field getField(String fieldName) {\n");
        out.write(ind(ind+1) + "if (fieldName==null) return null;\n");
        out.write(ind(ind+1) + "return type.getField(fieldName);\n");
        out.write(ind(ind) + "}\n\n");
    }

    /**
     * Exports the struct types found in this collection of fields as separate Java classes
     */
    private static Set<DataType> exportStructTypes(Collection<DataType> fields, Writer out, int ind, Set<DataType> exportedStructs) throws IOException {
        if (exportedStructs==null) exportedStructs=new HashSet<>();
        for (DataType f : fields) {
            if ((f instanceof StructDataType) && ! f.getName().contains(".")) {
                if (exportedStructs.contains(f)) continue;
                exportedStructs.add(f);
                StructDataType structType = (StructDataType) f;
                String structClassName = className(structType.getName());
                out.write(ind(ind)+"/**\n" +
                ind(ind)+" *  Generated by vespa-documentgen-plugin, do not edit.\n" +
                ind(ind)+" *  Input struct type: "+structType.getName()+"\n" +
                ind(ind)+" *  Date: "+new Date()+"\n" +
                ind(ind)+" */\n" +
                ind(ind)+"@com.yahoo.document.Generated\n" +
                ind(ind) + "public static class "+structClassName+" extends com.yahoo.document.datatypes.Struct {\n\n" +
                ind(ind+1)+"/** The type of this.*/\n" +
                ind(ind+1)+"public static final com.yahoo.document.StructDataType type = getStructType();\n\n");
                out.write(ind(ind+1)+"public "+structClassName+"() {\n" +
                ind(ind+2)+"super("+structClassName+".type);\n" +
                ind(ind+1)+"}\n\n");
                exportCopyConstructor(structClassName, out, ind+1, false);
                exportExtendedStructTypeGetter(structClassName, structType.getName(), List.of(),
                        structType.getFields(), null, null, out, ind+1, "getStructType",
                        "com.yahoo.document.StructDataType");
                exportAssign(structType, structClassName, out, ind+1);
                exportFieldsAndAccessors(structClassName, structType.getFields(), out, ind+1, true);

                // Need these methods for serialization.
                // This can be improved by generating a method to serialize the struct _here_ (and one in the document), and use that in serialization.
                exportGetFields(structType.getFields(), out, ind+1);
                exportDocumentMethods(structType.getFields(), out, ind+1);
                exportHashCode(structType.getFields(), out, ind+1, "(getDataType() != null ? getDataType().hashCode() : 0)");
                exportEquals(structClassName, structType.getFields(), out, ind+1);
                out.write(ind(ind)+"}\n\n");
            }
        }
        return exportedStructs;
    }

    /**
     * Override this, serialization of structs relies on it
     */
    private static void exportGetFieldCount(Collection<Field> fields, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public int getFieldCount() {\n");
        out.write(ind(ind+1)+"int ret=0;\n");
        for (Field f : fields) {
            out.write(ind(ind+1)+"if ("+getter(f.getName())+"()!=null) ret++;\n");
        }
        out.write(ind(ind+1)+"return ret;\n");
        out.write(ind(ind)+"}\n\n");
    }

    /**
     * Override the getFields() method of Struct, since serialization of Struct relies on it.
     */
    private static void exportGetFields(Collection<Field> fields, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public java.util.Set<java.util.Map.Entry<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue>> getFields() {\n" +
                ind(ind+1)+"java.util.Map<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue> ret = new java.util.LinkedHashMap<com.yahoo.document.Field, com.yahoo.document.datatypes.FieldValue>();\n");
        for (Field f : fields) {
            out.write(ind(ind+1)+"if ("+getter(f.getName())+"()!=null) {\n");
            out.write(ind(ind+2)+"com.yahoo.document.Field f = getField(\""+f.getName()+"\");\n");
            out.write(ind(ind+2)+"ret.put(f, ((com.yahoo.document.ExtendedField)f).getFieldValue(this));\n");
            out.write(ind(ind+1)+"}\n");
        }
        out.write(ind(ind+1)+"return ret.entrySet();\n");
        out.write(ind(ind)+"}\n\n");
    }

    private static void exportAssign(StructDataType structType, String structClassName, Writer out, int ind) throws IOException {
        out.write(ind(ind)+"@Override public void assign(Object o) {\n"+
          ind(ind+1)+"if (!(o instanceof "+structClassName+")) { super.assign(o); return; }\n"+
          ind(ind+1)+structClassName+" other = ("+structClassName+")o;\n");
        for (Field f: structType.getFields()) {
            out.write(ind(ind+1)+setter(f.getName())+"(other."+getter(f.getName())+"());\n");
        }

        out.write(ind(ind)+"}\n\n");
    }

    /**
     * Exports this set of fields with getters and setters
     * @param spanTrees If true, include a reference to the list of span trees for the string fields
     */
    private static void exportFieldsAndAccessors(String className, Collection<Field> fields, Writer out, int ind, boolean spanTrees) throws IOException {
        // List the fields as Java fields
        for (Field field: fields) {
            DataType dt = field.getDataType();
            out.write(
            ind(ind)+toJavaType(dt)+" "+field.getName()+";\n");
            if (spanTrees && dt.equals(DataType.STRING)) {
                out.write(ind(ind)+"java.util.Map<java.lang.String,com.yahoo.document.annotation.SpanTree> "+spanTreeGetter(field.getName())+";\n"); // same name on field as get method
            }
        }
        out.write(ind(ind)+"\n");
        // Getters, setters and annotation spantree lists for string fields
        for (Field field: fields) {
            DataType dt = field.getDataType();
            out.write(
            ind(ind)+"public "+toJavaType(dt)+" "+getter(field.getName())+"() { return "+field.getName()+"; }\n"+
            ind(ind)+"public "+className+" "+setter(field.getName())+"("+toJavaType(dt)+" "+field.getName()+") { this."+field.getName()+"="+field.getName()+"; return this; }\n");
            if (spanTrees && dt.equals(DataType.STRING)) {
                out.write(ind(ind)+"public java.util.Map<java.lang.String,com.yahoo.document.annotation.SpanTree> "+spanTreeGetter(field.getName())+"() { return "+field.getName()+"SpanTrees; }\n" +
                        ind(ind)+"public void "+spanTreeSetter(field.getName())+"(java.util.Map<java.lang.String,com.yahoo.document.annotation.SpanTree> spanTrees) { this."+field.getName()+"SpanTrees=spanTrees; }\n");
            }
        }
        out.write("\n");
    }

    private static String spanTreeSetter(String field) {
        return setter(field)+"SpanTrees";
    }

    private static String spanTreeGetter(String field) {
        return field+"SpanTrees";
    }

    /**
     * Returns spaces corresponding to the given levels of indentations
     */
    private static String ind(int levels) {
        int indent = levels*STD_INDENT;
        StringBuilder sb = new StringBuilder();
        for (int i = 0 ; i<indent ; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Returns spaces corresponding to 1 level of indentation
     */
    private static String ind() {
        return ind(1);
    }

    private static String getter(String field) {
        return "get"+upperCaseFirstChar(field);
    }

    private static String setter(String field) {
        return "set"+upperCaseFirstChar(field);
    }

    private static String className(String field) {
        return upperCaseFirstChar(field);
    }

    private static String toJavaType(DataType dt) {
        if (DataType.NONE.equals(dt)) return "void";
        if (DataType.INT.equals(dt)) return "java.lang.Integer";
        if (DataType.FLOAT.equals(dt)) return "java.lang.Float";
        if (DataType.STRING.equals(dt)) return "java.lang.String";
        if (DataType.RAW.equals(dt)) return "java.nio.ByteBuffer";
        if (DataType.LONG.equals(dt)) return "java.lang.Long";
        if (DataType.DOUBLE.equals(dt)) return "java.lang.Double";
        if (DataType.DOCUMENT.equals(dt)) return "com.yahoo.document.Document";
        if (DataType.URI.equals(dt)) return "java.net.URI";
        if (DataType.BYTE.equals(dt)) return "java.lang.Byte";
        if (DataType.BOOL.equals(dt)) return "java.lang.Boolean";
        if (DataType.TAG.equals(dt)) return "java.lang.String";
        if (dt instanceof StructDataType) return className(dt.getName());
        if (dt instanceof WeightedSetDataType) return "java.util.Map<"+toJavaType(((WeightedSetDataType)dt).getNestedType())+",java.lang.Integer>";
        if (dt instanceof ArrayDataType) return "java.util.List<"+toJavaType(((ArrayDataType)dt).getNestedType())+">";
        if (dt instanceof MapDataType) return "java.util.Map<"+toJavaType(((MapDataType)dt).getKeyType())+","+toJavaType(((MapDataType)dt).getValueType())+">";
        if (dt instanceof AnnotationReferenceDataType) return className(((AnnotationReferenceDataType) dt).getAnnotationType().getName());
        if (dt instanceof NewDocumentReferenceDataType) {
            return "com.yahoo.document.DocumentId";
        }
        if (dt instanceof TensorDataType) {
            return "com.yahoo.tensor.Tensor";
        }
        return "byte[]";
    }

    // bit stupid...
    private static String toJavaReference(DataType dt) {
        if (DataType.NONE.equals(dt)) return "com.yahoo.document.DataType.NONE";
        if (DataType.INT.equals(dt)) return "com.yahoo.document.DataType.INT";
        if (DataType.FLOAT.equals(dt)) return "com.yahoo.document.DataType.FLOAT";
        if (DataType.STRING.equals(dt)) return "com.yahoo.document.DataType.STRING";
        if (DataType.RAW.equals(dt)) return "com.yahoo.document.DataType.RAW";
        if (DataType.LONG.equals(dt)) return "com.yahoo.document.DataType.LONG";
        if (DataType.DOUBLE.equals(dt)) return "com.yahoo.document.DataType.DOUBLE";
        if (DataType.DOCUMENT.equals(dt)) return "com.yahoo.document.DataType.DOCUMENT";
        if (DataType.URI.equals(dt)) return "com.yahoo.document.DataType.URI";
        if (DataType.BYTE.equals(dt)) return "com.yahoo.document.DataType.BYTE";
        if (DataType.BOOL.equals(dt)) return "com.yahoo.document.DataType.BOOL";
        if (DataType.TAG.equals(dt)) return "com.yahoo.document.DataType.TAG";
        if (dt instanceof StructDataType) return  className(dt.getName()) +".type";
        if (dt instanceof WeightedSetDataType) return "new com.yahoo.document.WeightedSetDataType("+toJavaReference(((WeightedSetDataType)dt).getNestedType())+", "+
            ((WeightedSetDataType)dt).createIfNonExistent()+", "+ ((WeightedSetDataType)dt).removeIfZero()+","+dt.getId()+")";
        if (dt instanceof ArrayDataType) return "new com.yahoo.document.ArrayDataType("+toJavaReference(((ArrayDataType)dt).getNestedType())+")";
        if (dt instanceof MapDataType) return "new com.yahoo.document.MapDataType("+toJavaReference(((MapDataType)dt).getKeyType())+", "+
            toJavaReference(((MapDataType)dt).getValueType())+", "+dt.getId()+")";
        // For annotation references and generated types, the references are to the actual objects of the correct types, so most likely this is never needed,
        // but there might be scenarios where we want to look up the AnnotationType in the AnnotationTypeRegistry here instead.
        if (dt instanceof AnnotationReferenceDataType) return "new com.yahoo.document.annotation.AnnotationReferenceDataType(new com.yahoo.document.annotation.AnnotationType(\""+((AnnotationReferenceDataType)dt).getAnnotationType().getName()+"\"))";
        if (dt instanceof NewDocumentReferenceDataType) {
            // All concrete document types have a public `type` constant with their DocumentType.
            return String.format("new com.yahoo.document.ReferenceDataType(%s.type, %d)",
                    className(((NewDocumentReferenceDataType) dt).getTargetType().getName()), dt.getId());
        }
        if (dt instanceof TensorDataType) {
            return String.format("new com.yahoo.document.TensorDataType(com.yahoo.tensor.TensorType.fromSpec(\"%s\"))",
                    ((TensorDataType)dt).getTensorType().toString());
        }
        return "com.yahoo.document.DataType.RAW";
    }

    @Override
    public void execute() {
        execute(this.schemasDirectory, this.outputDirectory, packageName);
    }

    Map<String, Schema> getSearches() {
        return searches;
    }

    private static String upperCaseFirstChar(String s) {
        return s.substring(0, 1).toUpperCase()+s.substring(1);
    }

}
