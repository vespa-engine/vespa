// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1IA5String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

    public SubjectAlternativeName decode() {
        return new SubjectAlternativeName(new GeneralName(type.tag, value));
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
                return ASN1IA5String.getInstance(name).getString();
            case GeneralName.directoryName:
                return X500Name.getInstance(name).toString();
            case GeneralName.iPAddress:
                byte[] octets = DEROctetString.getInstance(name.toASN1Primitive()).getOctets();
                try {
                    return InetAddress.getByAddress(octets).getHostAddress();
                } catch (UnknownHostException e) {
                    // Only thrown if IP address is of invalid length, which is an illegal argument
                    throw new IllegalArgumentException(e);
                }
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
        OTHER(0),
        EMAIL(1),
        DNS(2),
        X400(3),
        DIRECTORY(4),
        EDI_PARITY(5),
        URI(6),
        IP(7),
        REGISTERED(8);

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
