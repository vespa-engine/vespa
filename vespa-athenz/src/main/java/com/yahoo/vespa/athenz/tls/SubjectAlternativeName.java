// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class SubjectAlternativeName {

    private final Type type;
    private final String value;

    public SubjectAlternativeName(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    SubjectAlternativeName(GeneralName bcGeneralName) {
        this.type = Type.fromTag(bcGeneralName.getTagNo());
        this.value = getValue(bcGeneralName);
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    GeneralName toGeneralName() {
        return new GeneralName(type.tag, value);
    }

    static List<SubjectAlternativeName> fromGeneralNames(GeneralNames generalNames) {
        return Arrays.stream(generalNames.getNames()).map(SubjectAlternativeName::new).collect(toList());
    }

    private String getValue(GeneralName bcGeneralName) {
        ASN1Encodable name = bcGeneralName.getName();
        switch (bcGeneralName.getTagNo()) {
            case GeneralName.rfc822Name:
            case GeneralName.dNSName:
            case GeneralName.uniformResourceIdentifier:
                return DERIA5String.getInstance(name).getString();
            case GeneralName.directoryName:
                return X500Name.getInstance(name).toString();
            default:
                return name.toString();
        }
    }

    @Override
    public String toString() {
        return "SubjectAlternativeName{" +
                "type=" + type +
                ", value='" + value + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubjectAlternativeName that = (SubjectAlternativeName) o;
        return type == that.type &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    public enum Type {
        OTHER_NAME(0),
        RFC822_NAME(1),
        DNS_NAME(2),
        X400_ADDRESS(3),
        DIRECTORY_NAME(4),
        EDI_PARITY_NAME(5),
        UNIFORM_RESOURCE_IDENTIFIER(6),
        IP_ADDRESS(7),
        REGISTERED_ID(8);

        final int tag;

        Type(int tag) {
            this.tag = tag;
        }

        public static Type fromTag(int tag) {
            return Arrays.stream(Type.values())
                    .filter(type -> type.tag == tag)
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid tag: " + tag));
        }

        public int getTag() {
            return tag;
        }
    }
}
