// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * A term type enumeration.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class TermType {

    public static final TermType RANK = new TermType("rank", Item.ItemType.RANK, RankItem.class, null, "$");

    public static final TermType AND = new TermType("and", Item.ItemType.AND, AndItem.class, null, "+");

    public static final TermType OR = new TermType("or", Item.ItemType.OR, OrItem.class, null, "?");

    public static final TermType NOT = new TermType("not", Item.ItemType.NOT, NotItem.class, null, "-");

    public static final TermType PHRASE = new TermType("phrase", Item.ItemType.PHRASE, PhraseItem.class, null, "\"");

    public static final TermType EQUIV = new TermType("equiv", Item.ItemType.EQUIV, EquivItem.class, null, "");

    public static final TermType DEFAULT = new TermType("", Item.ItemType.AND, CompositeItem.class, AndItem.class, "");

    public final String name;

    private final Item.ItemType itemType;
    private final String sign;
    private final Class<? extends CompositeItem> instanceClass;
    private final Class<? extends CompositeItem> itemClass;

    private TermType(String name,
                     Item.ItemType itemType,
                     Class<? extends CompositeItem> itemClass,
                     Class<? extends CompositeItem> instanceClass,
                     String sign) {
        this.name = name;
        this.itemType = itemType;
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

    public Item.ItemType toItemType() { return itemType; }

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
