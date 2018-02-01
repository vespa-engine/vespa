// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.search.query.profile.DimensionValues;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.text.BooleanParser;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bratseth
 */
public class QueryProfileConfigurer implements ConfigSubscriber.SingleSubscriber<QueryProfilesConfig> {

    private final ConfigSubscriber subscriber = new ConfigSubscriber();

    private volatile QueryProfileRegistry currentRegistry;

    public QueryProfileConfigurer(String configId) {
        subscriber.subscribe(this, QueryProfilesConfig.class, configId);
    }

    /** Returns the registry created by the last occurring call to configure */
    public QueryProfileRegistry getCurrentRegistry() { return currentRegistry; }

    private void setCurrentRegistry(QueryProfileRegistry registry) {
        this.currentRegistry=registry;
    }

    public void configure(QueryProfilesConfig config) {
        QueryProfileRegistry registry = createFromConfig(config);
        setCurrentRegistry(registry);
    }

    public static QueryProfileRegistry createFromConfig(QueryProfilesConfig config) {
        QueryProfileRegistry registry=new QueryProfileRegistry();

        // Pass 1: Create all profiles and profile types
        for (QueryProfilesConfig.Queryprofiletype profileTypeConfig : config.queryprofiletype()) {
            createProfileType(profileTypeConfig,registry.getTypeRegistry());
        }
        for (QueryProfilesConfig.Queryprofile profileConfig : config.queryprofile()) {
            createProfile(profileConfig,registry);
        }

        // Pass 2: Resolve references and add content
        for (QueryProfilesConfig.Queryprofiletype profileTypeConfig : config.queryprofiletype()) {
            fillProfileType(profileTypeConfig,registry.getTypeRegistry());
        }

        // To ensure topological sorting, using DPS. This will _NOT_ detect cycles (but it will not fail if they
        // exist either)
        Set<ComponentId> filled = new HashSet<>();
        for (QueryProfilesConfig.Queryprofile profileConfig : config.queryprofile()) {
            fillProfile(profileConfig, config, registry, filled);
        }

        registry.freeze();
        return registry;
    }

    /** Stop subscribing from this configurer */
    public void shutdown() {
        subscriber.close();
    }

    private static void createProfile(QueryProfilesConfig.Queryprofile config,QueryProfileRegistry registry) {
        QueryProfile profile=new QueryProfile(config.id());
        try {
            String typeId=config.type();
            if (typeId!=null && !typeId.isEmpty())
                profile.setType(registry.getType(typeId));

            if (config.dimensions().size()>0) {
                String[] dimensions=new String[config.dimensions().size()];
                for (int i=0; i<config.dimensions().size(); i++)
                    dimensions[i]=config.dimensions().get(i);
                profile.setDimensions(dimensions);
            }

            registry.register(profile);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + profile,e);
        }
    }

    private static void createProfileType(QueryProfilesConfig.Queryprofiletype config, QueryProfileTypeRegistry registry) {
        QueryProfileType type=new QueryProfileType(config.id());
        type.setStrict(config.strict());
        type.setMatchAsPath(config.matchaspath());
        registry.register(type);
    }

    private static void fillProfile(QueryProfilesConfig.Queryprofile config,
                                    QueryProfilesConfig queryProfilesConfig,
                                    QueryProfileRegistry registry,
                                    Set<ComponentId> filled) {
        QueryProfile profile=registry.getComponent(new ComponentSpecification(config.id()).toId());
        if (filled.contains(profile.getId())) return;
        filled.add(profile.getId());
        try {
            for (String inheritedId : config.inherit()) {
                QueryProfile inherited=registry.getComponent(inheritedId);
                if (inherited==null)
                    throw new IllegalArgumentException("Inherited query profile '" + inheritedId + "' in " + profile + " was not found");
                fillProfile(inherited, queryProfilesConfig, registry, filled);
                profile.addInherited(inherited);
            }

            for (QueryProfilesConfig.Queryprofile.Reference referenceConfig : config.reference()) {
                QueryProfile referenced=registry.getComponent(referenceConfig.value());
                if (referenced==null)
                    throw new IllegalArgumentException("Query profile '" + referenceConfig.value() + "' referenced as '" +
                            referenceConfig.name() + "' in " + profile + " was not found");
                profile.set(referenceConfig.name(),referenced, registry);
                if (referenceConfig.overridable()!=null && !referenceConfig.overridable().isEmpty())
                    profile.setOverridable(referenceConfig.name(),BooleanParser.parseBoolean(referenceConfig.overridable()),null);
            }

            for (QueryProfilesConfig.Queryprofile.Property propertyConfig : config.property()) {
                profile.set(propertyConfig.name(),propertyConfig.value(), registry);
                if (propertyConfig.overridable()!=null && !propertyConfig.overridable().isEmpty())
                    profile.setOverridable(propertyConfig.name(),BooleanParser.parseBoolean(propertyConfig.overridable()),null);
            }

            for (QueryProfilesConfig.Queryprofile.Queryprofilevariant variantConfig : config.queryprofilevariant()) {
                String[] forDimensionValueArray=new String[variantConfig.fordimensionvalues().size()];
                for (int i=0; i<variantConfig.fordimensionvalues().size(); i++) {
                    forDimensionValueArray[i]=variantConfig.fordimensionvalues().get(i).trim();
                    if ("*".equals(forDimensionValueArray[i]))
                        forDimensionValueArray[i]=null;
                }
                DimensionValues forDimensionValues=DimensionValues.createFrom(forDimensionValueArray);

                for (String inheritedId : variantConfig.inherit()) {
                    QueryProfile inherited=registry.getComponent(inheritedId);
                    if (inherited==null)
                        throw new IllegalArgumentException("Inherited query profile '" + inheritedId + "' in " + profile +
                                                           " for '" + forDimensionValues + "' was not found");
                    fillProfile(inherited, queryProfilesConfig, registry, filled);
                    profile.addInherited(inherited, forDimensionValues);
                }

                for (QueryProfilesConfig.Queryprofile.Queryprofilevariant.Reference referenceConfig : variantConfig.reference()) {
                    QueryProfile referenced=registry.getComponent(referenceConfig.value());
                    if (referenced==null)
                        throw new IllegalArgumentException("Query profile '" + referenceConfig.value() + "' referenced as '" +
                                referenceConfig.name() + "' in " + profile + " for '" + forDimensionValues + "' was not found");
                    profile.set(referenceConfig.name(), referenced, forDimensionValues, registry);
                }

                for (QueryProfilesConfig.Queryprofile.Queryprofilevariant.Property propertyConfig : variantConfig.property()) {
                    profile.set(propertyConfig.name(), propertyConfig.value(), forDimensionValues, registry);
                }

            }

        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + profile,e);
        }
    }

    /** Fill a given profile by locating its config */
    private static void fillProfile(QueryProfile inherited,
                                    QueryProfilesConfig queryProfilesConfig,
                                    QueryProfileRegistry registry,
                                    Set<ComponentId> visited) {
        for (QueryProfilesConfig.Queryprofile inheritedConfig : queryProfilesConfig.queryprofile()) {
            if (inherited.getId().stringValue().equals(inheritedConfig.id())) {
                fillProfile(inheritedConfig, queryProfilesConfig, registry, visited);
            }
        }
    }

    private static void fillProfileType(QueryProfilesConfig.Queryprofiletype config, QueryProfileTypeRegistry registry) {
        QueryProfileType type = registry.getComponent(new ComponentSpecification(config.id()).toId());
        try {

            for (String inheritedId : config.inherit()) {
                QueryProfileType inherited = registry.getComponent(inheritedId);
                if (inherited == null)
                    throw new IllegalArgumentException("Inherited query profile type '" + inheritedId + "' in " + type + " was not found");
                else
                    type.inherited().add(inherited);

            }

            for (QueryProfilesConfig.Queryprofiletype.Field fieldConfig : config.field())
                instantiateFieldDescription(fieldConfig,type,registry);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + type,e);
        }
    }

    private static void instantiateFieldDescription(QueryProfilesConfig.Queryprofiletype.Field fieldConfig,
                                                    QueryProfileType type,
                                                    QueryProfileTypeRegistry registry) {
        try {
            FieldType fieldType = FieldType.fromString(fieldConfig.type(), registry);
            FieldDescription field = new FieldDescription(fieldConfig.name(),
                                                          fieldType,
                                                          fieldConfig.alias(),
                                                          fieldConfig.mandatory(),
                                                          fieldConfig.overridable()
            );
            type.addField(field, registry);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid field '" + fieldConfig.name() + "' in " + type,e);
        }
    }


}
