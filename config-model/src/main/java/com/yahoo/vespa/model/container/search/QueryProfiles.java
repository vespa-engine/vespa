// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.BackedOverridableQueryProfile;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.SubstituteString;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.tensor.TensorType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

/**
 * Owns the query profiles and query profile types to be handed to the qrs nodes.
 * Owned by a container cluster
 *
 * @author bratseth
 */
public class QueryProfiles implements Serializable, QueryProfilesConfig.Producer {

    private final QueryProfileRegistry registry;

    /**
     * Creates a new set of query profiles for which the config can be returned at request
     *
     * @param registry the registry containing the query profiles and types of this.
     *        The given registry cannot be frozen on calling this.
     */
    public QueryProfiles(QueryProfileRegistry registry, DeployLogger logger) {
        this.registry = registry;
        validate(registry, logger);
    }

    public QueryProfiles() {
        this.registry = new QueryProfileRegistry();
    }

    public QueryProfileRegistry getRegistry() {
        return registry;
    }

    /** Emits warnings/hints on some common configuration errors */
    private void validate(QueryProfileRegistry registry, DeployLogger logger) {
        Set<String> tensorFields = new HashSet<>();
        for (QueryProfileType type : registry.getTypeRegistry().allComponents()) {
            for (var fieldEntry : type.fields().entrySet()) {
                validateTensorField(fieldEntry.getKey(), fieldEntry.getValue().getType().asTensorType());
                if (fieldEntry.getValue().getType().asTensorType().rank() > 0)
                    tensorFields.add(fieldEntry.getKey());
            }
        }

        if ( registry.getTypeRegistry().hasApplicationTypes() && registry.allComponents().isEmpty()) {
            logger.logApplicationPackage(Level.WARNING, "This application define query profile types, but has " +
                                                        "no query profiles referencing them so they have no effect. "  +
                                                        (tensorFields.isEmpty() ? ""
                                                                                : "In particular, the tensors (" +
                                                                                  String.join(", ", tensorFields) +
                                                                                  ") will be interpreted as strings, " +
                                                                                  "not tensors if sent in requests. ") +
                                                        "See https://docs.vespa.ai/en/query-profiles.html");
        }

    }

    private void validateTensorField(String fieldName, TensorType type) {
        if (type.dimensions().stream().anyMatch(d -> d.isIndexed() && d.size().isEmpty()))
            throw new IllegalArgumentException("Illegal type in field " + fieldName + " type " + type +
                                               ": Dense tensor dimensions must have a size");
    }

    @Override
    public void getConfig(QueryProfilesConfig.Builder builder) {
        for (QueryProfile profile : registry.allComponents()) {
            builder.queryprofile(createConfig(profile));
        }
        for (QueryProfileType profileType : registry.getTypeRegistry().allComponents()) {
            if ( ! profileType.isBuiltin())
                builder.queryprofiletype(createConfig(profileType));
        }
    }

    private QueryProfilesConfig.Queryprofile.Builder createConfig(QueryProfile profile) {
        QueryProfilesConfig.Queryprofile.Builder qB = new QueryProfilesConfig.Queryprofile.Builder();
        qB.id(profile.getId().stringValue());
        if (profile.getType() != null)
            qB.type(profile.getType().getId().stringValue());
        for (QueryProfile inherited : profile.inherited())
            qB.inherit(inherited.getId().stringValue());

        if (profile.getVariants() != null) {
            for (String dimension : profile.getVariants().getDimensions())
                qB.dimensions(dimension);            
        }
        addFieldChildren(qB, profile, "");
        addVariants(qB, profile);
        return qB;
    }

    private void addFieldChildren(QueryProfilesConfig.Queryprofile.Builder qpB, QueryProfile profile, String namePrefix) {
        List<Map.Entry<String, Object>> content = new ArrayList<>(profile.declaredContent().entrySet());
        content.sort(new MapEntryKeyComparator());
        if (profile.getValue() != null) { // Add "prefix with dot removed"=value:
            QueryProfilesConfig.Queryprofile.Property.Builder propB = new QueryProfilesConfig.Queryprofile.Property.Builder();
            String fullName = namePrefix.substring(0, namePrefix.length() - 1);
            Object value = profile.getValue();
            if (value instanceof SubstituteString)
                value = value.toString(); // Send only types understood by configBuilder downwards
            propB.name(fullName);
            if (value != null) propB.value(value.toString());
            qpB.property(propB);
        }
        for (Map.Entry<String,Object> field : content) {
            addField(qpB, profile, field, namePrefix);
        }
    }

    private void addVariantFieldChildren(QueryProfilesConfig.Queryprofile.Queryprofilevariant.Builder qpB,
                                         QueryProfile profile,
                                         String namePrefix) {
        List<Map.Entry<String, Object>> content = new ArrayList<>(profile.declaredContent().entrySet());
        content.sort(new MapEntryKeyComparator());
        if (profile.getValue() != null) { // Add "prefix with dot removed"=value:
            QueryProfilesConfig.Queryprofile.Queryprofilevariant.Property.Builder propB = new QueryProfilesConfig.Queryprofile.Queryprofilevariant.Property.Builder();
            String fullName = namePrefix.substring(0, namePrefix.length() - 1);
            Object value = profile.getValue();
            if (value instanceof SubstituteString)
                value = value.toString(); // Send only types understood by configBuilder downwards
            propB.name(fullName);
            if (value != null)
                propB.value(value.toString());
            qpB.property(propB);
        }
        for (Map.Entry<String, Object> entry : content) {
            addVariantField(qpB, entry, profile.isDeclaredOverridable(entry.getKey(), Map.of()), namePrefix);
        }
    }

    private void addField(QueryProfilesConfig.Queryprofile.Builder qpB,
            QueryProfile profile, Entry<String, Object> field, String namePrefix) {
        String fullName=namePrefix + field.getKey();
        if (field.getValue() instanceof QueryProfile subProfile) {
            if ( ! subProfile.isExplicit()) { // Implicitly defined profile - add content
                addFieldChildren(qpB, subProfile,fullName + ".");
            }
            else { // Reference to an id'ed profile - output reference plus any local overrides
                QueryProfilesConfig.Queryprofile.Reference.Builder refB = new QueryProfilesConfig.Queryprofile.Reference.Builder();
                createReferenceFieldConfig(refB, profile, fullName, field.getKey(), ((BackedOverridableQueryProfile) subProfile).getBacking().getId().stringValue());
                qpB.reference(refB);
                addFieldChildren(qpB, subProfile,fullName + ".");
            }
        }
        else { // a primitive
            qpB.property(createPropertyFieldConfig(profile, fullName, field.getKey(), field.getValue()));
        }
    }

    private void addVariantField(QueryProfilesConfig.Queryprofile.Queryprofilevariant.Builder qpB,
                                 Entry<String, Object> field, Boolean overridable, String namePrefix) {
        String fullName = namePrefix + field.getKey();
        if (field.getValue() instanceof QueryProfile subProfile) {
            if ( ! subProfile.isExplicit()) { // Implicitly defined profile - add content
                addVariantFieldChildren(qpB, subProfile,fullName + ".");
            }
            else { // Reference to an id'ed profile - output reference plus any local overrides
                QueryProfilesConfig.Queryprofile.Queryprofilevariant.Reference.Builder refB = new QueryProfilesConfig.Queryprofile.Queryprofilevariant.Reference.Builder();
                createVariantReferenceFieldConfig(refB, fullName, ((BackedOverridableQueryProfile) subProfile).getBacking().getId().stringValue());
                qpB.reference(refB);
                addVariantFieldChildren(qpB, subProfile, fullName + ".");
            }
        }
        else { // a primitive
            qpB.property(createVariantPropertyFieldConfig(fullName, field.getValue(), overridable));
        }
    }

    private void addVariants(QueryProfilesConfig.Queryprofile.Builder qB, QueryProfile profile) {
        if (profile.getVariants() == null) return;
        DeclaredQueryProfileVariants declaredVariants = new DeclaredQueryProfileVariants(profile);
        for (DeclaredQueryProfileVariants.VariantQueryProfile variant : declaredVariants.getVariantQueryProfiles().values()) {
            QueryProfilesConfig.Queryprofile.Queryprofilevariant.Builder varB = new QueryProfilesConfig.Queryprofile.Queryprofilevariant.Builder();
            for (String dimensionValue : variant.getDimensionValues()) {
                if (dimensionValue == null)
                    dimensionValue = "*";
                varB.fordimensionvalues(dimensionValue);
            }
            for (QueryProfile inherited : variant.inherited())
                varB.inherit(inherited.getId().stringValue());

            List<Map.Entry<String,Object>> content = new ArrayList<>(variant.getValues().entrySet());
            content.sort(new MapEntryKeyComparator());
            for (Map.Entry<String, Object> entry : content) {
                addVariantField(varB, entry, variant.getOverriable().get(entry.getKey()), "");
            }
            qB.queryprofilevariant(varB);
        }
    }

    private void createReferenceFieldConfig(QueryProfilesConfig.Queryprofile.Reference.Builder refB, QueryProfile profile,
            String fullName, String localName, String stringValue) {
        refB.name(fullName);
        if (stringValue!=null) refB.value(stringValue);
        Boolean overridable=null;
        if (profile!=null)
            overridable=profile.isDeclaredOverridable(localName, null);
        if (overridable!=null)
            refB.overridable(""+overridable);
    }

    private void createVariantReferenceFieldConfig(QueryProfilesConfig.Queryprofile.Queryprofilevariant.Reference.Builder refB,
            String fullName, String stringValue) {
        refB.name(fullName);
        if (stringValue!=null) refB.value(stringValue);
    }

    private QueryProfilesConfig.Queryprofile.Property.Builder createPropertyFieldConfig(QueryProfile profile,
                                                                                        String fullName,
                                                                                        String localName,
                                                                                        Object value) {
        QueryProfilesConfig.Queryprofile.Property.Builder propB = new QueryProfilesConfig.Queryprofile.Property.Builder();
        Boolean overridable=null;
        if (value instanceof SubstituteString)
            value=value.toString(); // Send only types understood by configBuilder downwards
        propB.name(fullName);        
        if (value!=null) propB.value(value.toString());
        if (profile!=null)
            overridable=profile.isDeclaredOverridable(localName, null);
        if (overridable!=null)
            propB.overridable(""+overridable);
        return propB;
    }

    private QueryProfilesConfig.Queryprofile.Queryprofilevariant.Property.Builder createVariantPropertyFieldConfig(String fullName,
                                                                                                                   Object value,
                                                                                                                   Boolean overridable) {
        QueryProfilesConfig.Queryprofile.Queryprofilevariant.Property.Builder propB = new QueryProfilesConfig.Queryprofile.Queryprofilevariant.Property.Builder();
        if (value instanceof SubstituteString)
            value = value.toString(); // Send only types understood by configBuilder downwards

        propB.name(fullName);
        if (value != null)
            propB.value(value.toString());
        if (overridable != null)
            propB.overridable(overridable.toString());
        return propB;
    }

    private QueryProfilesConfig.Queryprofiletype.Builder createConfig(QueryProfileType profileType) {
        QueryProfilesConfig.Queryprofiletype.Builder qtB = new QueryProfilesConfig.Queryprofiletype.Builder();
        qtB.id(profileType.getId().stringValue());
        if (profileType.isDeclaredStrict())
            qtB.strict(true);
        if (profileType.getDeclaredMatchAsPath())
            qtB.matchaspath(true);
        for (QueryProfileType inherited : profileType.inherited())
            qtB.inherit(inherited.getId().stringValue());
        List<FieldDescription> fields = new ArrayList<>(profileType.declaredFields().values());
        Collections.sort(fields);
        for (FieldDescription field : fields)
            qtB.field(createConfig(field));
        return qtB;
    }

    private QueryProfilesConfig.Queryprofiletype.Field.Builder createConfig(FieldDescription field) {
        QueryProfilesConfig.Queryprofiletype.Field.Builder fB = new QueryProfilesConfig.Queryprofiletype.Field.Builder();
        fB.name(field.getName()).type(field.getType().stringValue());
        if ( ! field.isOverridable())
            fB.overridable(false);
        if (field.isMandatory())
            fB.mandatory(true);
        String aliases = toSpaceSeparatedString(field.getAliases());
        if ( ! aliases.isEmpty())
            fB.alias(aliases);
        return fB;
    }

    private String toSpaceSeparatedString(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (Iterator<String> i = list.iterator(); i.hasNext(); ) {
            b.append(i.next());
            if (i.hasNext())
                b.append(" ");
        }
        return b.toString();
    }

    private static class MapEntryKeyComparator implements Comparator<Map.Entry<String,Object>> {
        @Override
        public int compare(Map.Entry<String,Object> e1,Map.Entry<String,Object> e2) {
            return e1.getKey().compareTo(e2.getKey());
        }
    }

    /** Returns the config produced by this */
    public QueryProfilesConfig getConfig() {
        QueryProfilesConfig.Builder qB = new QueryProfilesConfig.Builder();
        getConfig(qB);
        return new QueryProfilesConfig(qB);
    }

}
