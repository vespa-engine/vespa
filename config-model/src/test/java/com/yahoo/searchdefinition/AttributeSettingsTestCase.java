// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.StructDataType;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Sorting;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Attribute settings
 *
 * @author  bratseth
 */
public class AttributeSettingsTestCase extends SearchDefinitionTestCase {

    @Test
    public void testAttributeSettings() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/attributesettings.sd");

        SDField f1=(SDField) search.getDocument().getField("f1");
        assertTrue(f1.getAttributes().size() == 1);
        Attribute a1 = f1.getAttributes().get(f1.getName());
        assertThat(a1.getType(), is(Attribute.Type.LONG));
        assertThat(a1.getCollectionType(), is(Attribute.CollectionType.SINGLE));
        assertTrue(a1.isHuge());
        assertFalse(a1.isFastSearch());
        assertFalse(a1.isFastAccess());
        assertFalse(a1.isRemoveIfZero());
        assertFalse(a1.isCreateIfNonExistent());

        SDField f2=(SDField) search.getDocument().getField("f2");
        assertTrue(f2.getAttributes().size() == 1);
        Attribute a2 = f2.getAttributes().get(f2.getName());
        assertThat(a2.getType(), is(Attribute.Type.LONG));
        assertThat(a2.getCollectionType(), is(Attribute.CollectionType.SINGLE));
        assertFalse(a2.isHuge());
        assertTrue(a2.isFastSearch());
        assertFalse(a2.isFastAccess());
        assertFalse(a2.isRemoveIfZero());
        assertFalse(a2.isCreateIfNonExistent());
        assertThat(f2.getAliasToName().get("f2alias"), is("f2"));
        SDField f3=(SDField) search.getDocument().getField("f3");
        assertTrue(f3.getAttributes().size() == 1);
        assertThat(f3.getAliasToName().get("f3alias"), is("f3"));

        Attribute a3 = f3.getAttributes().get(f3.getName());
        assertThat(a3.getType(), is(Attribute.Type.LONG));
        assertThat(a3.getCollectionType(), is(Attribute.CollectionType.SINGLE));
        assertFalse(a3.isHuge());
        assertFalse(a3.isFastSearch());
        assertFalse(a3.isFastAccess());
        assertFalse(a3.isRemoveIfZero());
        assertFalse(a3.isCreateIfNonExistent());

        assertWeightedSet(search,"f4",true,true);
        assertWeightedSet(search,"f5",true,true);
        assertWeightedSet(search,"f6",true,true);
        assertWeightedSet(search,"f7",true,false);
        assertWeightedSet(search,"f8",true,false);
        assertWeightedSet(search,"f9",false,true);
        assertWeightedSet(search,"f10",false,true);
    }

    private void assertWeightedSet(Search search, String name, boolean createIfNonExistent, boolean removeIfZero) {
        SDField f4 = (SDField) search.getDocument().getField(name);
        assertTrue(f4.getAttributes().size() == 1);
        Attribute a4 = f4.getAttributes().get(f4.getName());
        assertThat(a4.getType(), is(Attribute.Type.STRING));
        assertThat(a4.getCollectionType(), is(Attribute.CollectionType.WEIGHTEDSET));
        assertFalse(a4.isHuge());
        assertFalse(a4.isFastSearch());
        assertFalse(a4.isFastAccess());
        assertThat(removeIfZero, is(a4.isRemoveIfZero()));
        assertThat(createIfNonExistent, is(a4.isCreateIfNonExistent()));
    }

    @Test
    public void requireThatFastAccessCanBeSet() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/attributesettings.sd");
        SDField field = (SDField) search.getDocument().getField("fast_access");
        assertTrue(field.getAttributes().size() == 1);
        Attribute attr = field.getAttributes().get(field.getName());
        assertTrue(attr.isFastAccess());
    }

    @Test
    public void attribute_convert_to_array_copies_internal_state() {
        StructDataType refType = new StructDataType("my_struct");
        Attribute single = new Attribute("foo", Attribute.Type.STRING, Attribute.CollectionType.SINGLE,
                Optional.of(TensorType.fromSpec("tensor(x{})")), Optional.of(refType));
        single.setRemoveIfZero(true);
        single.setCreateIfNonExistent(true);
        single.setPrefetch(Boolean.TRUE);
        single.setEnableBitVectors(true);
        single.setEnableOnlyBitVector(true);
        single.setFastSearch(true);
        single.setHuge(true);
        single.setFastAccess(true);
        single.setPosition(true);
        single.setArity(5);
        single.setLowerBound(7);
        single.setUpperBound(11);
        single.setDensePostingListThreshold(13.3);
        single.getSorting().setAscending();
        single.getAliases().add("foo");

        Attribute array = single.convertToArray();
        assertEquals("foo", array.getName());
        assertEquals(Attribute.Type.STRING, array.getType());
        assertEquals(Attribute.CollectionType.ARRAY, array.getCollectionType());
        assertEquals(Optional.of(TensorType.fromSpec("tensor(x{})")), array.tensorType());
        assertSame(single.referenceDocumentType(), array.referenceDocumentType());
        assertTrue(array.isRemoveIfZero());
        assertTrue(array.isCreateIfNonExistent());
        assertTrue(array.isPrefetch());
        assertTrue(array.isEnabledBitVectors());
        assertTrue(array.isEnabledOnlyBitVector());
        assertTrue(array.isFastSearch());
        assertTrue(array.isHuge());
        assertTrue(array.isFastAccess());
        assertTrue(array.isPosition());
        assertEquals(5, array.arity());
        assertEquals(7, array.lowerBound());
        assertEquals(11, array.upperBound());
        assertEquals(13.3, array.densePostingListThreshold(), 0.00001);
        assertSame(single.getSorting(), array.getSorting());
        assertSame(single.getAliases(), array.getAliases());
    }

}
