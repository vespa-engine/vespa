// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;

import java.io.IOException;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "document-version", visible = true)
@JsonTypeIdResolver(SignedIdentityDocumentEntityTypeResolver.class)
public interface SignedIdentityDocumentEntity {
    int documentVersion();
}

class SignedIdentityDocumentEntityTypeResolver implements TypeIdResolver {
    JavaType javaType;

    @Override
    public void init(JavaType javaType) {
        this.javaType = javaType;
    }

    @Override
    public String idFromValue(Object o) {
        return idFromValueAndType(o, o.getClass());
    }

    @Override
    public String idFromValueAndType(Object o, Class<?> aClass) {
        if (Objects.isNull(o)) {
            throw new IllegalArgumentException("Cannot serialize null oject");
        } else {
            if (o instanceof SignedIdentityDocumentEntity s) {
                return Integer.toString(s.documentVersion());
            } else {
                throw new IllegalArgumentException("Cannot serialize class: " + o.getClass());
            }
        }
    }

    @Override
    public String idFromBaseType() {
        return idFromValueAndType(null, javaType.getRawClass());
    }

    @Override
    public JavaType typeFromId(DatabindContext databindContext, String s) throws IOException {
        try {
            int version = Integer.parseInt(s);
            Class<? extends SignedIdentityDocumentEntity> cls = version <= SignedIdentityDocument.LEGACY_DEFAULT_DOCUMENT_VERSION
                    ? LegacySignedIdentityDocumentEntity.class
                    : DefaultSignedIdentityDocumentEntity.class;
            return TypeFactory.defaultInstance().constructSpecializedType(javaType,cls);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unable to deserialize document with version: \"%s\"".formatted(s));
        }
    }

    @Override
    public String getDescForKnownTypeIds() {
        return "Type resolver for SignedIdentityDocumentEntity";
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}