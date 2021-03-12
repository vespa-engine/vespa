// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * A term type enumeration
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class TermType {

    public static TermType RANK = new TermType("rank", RankItem.class, null, "$");

    public static TermType AND = new TermType("and", AndItem.class, null, "+");

    public static TermType OR = new TermType("or", OrItem.class, null, "?");

    public static TermType NOT = new TermType("not", NotItem.class, null, "-");

    public static TermType PHRASE = new TermType("phrase", PhraseItem.class, null, "\"");

    public static TermType EQUIV = new TermType("equiv", EquivItem.class, null, "");

    public static TermType DEFAULT = new TermType("", CompositeItem.class, AndItem.class, "");

    public final String name;

    private final String sign;
    private final Class<? extends CompositeItem> instanceClass;
    private final Class<? extends CompositeItem> itemClass;

    private TermType(String name, Class<? extends CompositeItem> itemClass, Class<? extends CompositeItem> instanceClass, String sign) {
        this.name = name;
        this.itemClass = itemClass;
        if (instanceClass == null) {
            this.instanceClass = itemClass;
        } else {
            this.instanceClass = instanceClass;
        }
        this.sign = sign;
    }

    public String getName() {
        return name;
    }

    /** Returns the CompositeItem type this type corresponds to, or CompositeItem if it's the default */
    public Class<? extends CompositeItem> getItemClass() {
        return itemClass;
    }

    /** Returns true if the class corresponding to this type is the given class */
    public boolean hasItemClass(Class<?> theClass) {
        return getItemClass()==theClass;
    }

    /**
     * Returns an instance of the class corresponding to the given type, AndItem
     * if this is the DEFAULT type
     *
     * @throws RuntimeException if an instance could not be created
     */
    public Item createItemClass() {
        try {
            return instanceClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not create an instance for item " + this, e);
        }
    }

    public String toSign() {
        return sign;
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof TermType)) return false;

        TermType other = (TermType) o;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() { return "term type '" + name + "'"; }

}
