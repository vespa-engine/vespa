// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.Struct;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
public class StructDataTypeTestCase {

    @Test
    public void testSimpleInheritance() {
        StructDataType personType = new StructDataType("person");
        Field firstName = new Field("firstname", DataType.STRING);
        Field lastName = new Field("lastname", DataType.STRING);
        personType.addField(firstName);
        personType.addField(lastName);

        StructDataType employeeType = new StructDataType("employee");
        Field empId = new Field("employeeid", DataType.INT);
        employeeType.addField(empId);

        assertEquals(2, personType.getFieldCount());
        assertEquals("firstname", personType.getFields().toArray(new Field[0])[0].getName());
        assertEquals("lastname", personType.getFields().toArray(new Field[0])[1].getName());
        assertEquals(1, employeeType.getFieldCount());
        assertEquals("employeeid", employeeType.getFields().toArray(new Field[0])[0].getName());

        employeeType.inherit(personType);

        assertEquals(2, personType.getFieldCount());
        assertEquals("firstname", personType.getFields().toArray(new Field[0])[0].getName());
        assertEquals("lastname", personType.getFields().toArray(new Field[0])[1].getName());
        assertEquals(3, employeeType.getFieldCount());
        assertEquals("employeeid", employeeType.getFields().toArray(new Field[0])[0].getName());
        assertEquals("firstname", employeeType.getFields().toArray(new Field[0])[1].getName());
        assertEquals("lastname", employeeType.getFields().toArray(new Field[0])[2].getName());
    }

    @Test
    public void testCompatibleWith() {
        StructDataType personType = new StructDataType("person");
        Field firstName = new Field("firstname", DataType.STRING);
        Field lastName = new Field("lastname", DataType.STRING);
        personType.addField(firstName);
        personType.addField(lastName);

        StructDataType employeeType = new StructDataType("employee");
        Field empId = new Field("employeeid", DataType.INT);
        employeeType.addField(empId);
        employeeType.inherit(personType);

        Struct person = new Struct(personType);
        Struct employee = new Struct(employeeType);

        assertTrue(personType.isValueCompatible(person));
        assertTrue(personType.isValueCompatible(employee));

        assertTrue(employeeType.isValueCompatible(employee));
        assertFalse(employeeType.isValueCompatible(person));

        StructDataType containerType = new StructDataType("containerstruct");
        Field structPolymorphic = new Field("structpolymorphic", personType);
        containerType.addField(structPolymorphic);

        Struct container = new Struct(containerType);
        container.setFieldValue(structPolymorphic, person);
        container.setFieldValue(structPolymorphic, employee);
    }

}
