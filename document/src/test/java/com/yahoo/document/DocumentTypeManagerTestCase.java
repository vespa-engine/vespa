// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Thomas Gundersen
 */
public class DocumentTypeManagerTestCase {

    // Verify that we can register and retrieve fields.
    @Test
    public void testRegisterAndGet() {
        DocumentTypeManager manager = new DocumentTypeManager();

        DocumentType newDocType = new DocumentType("testdoc");
        newDocType.addField("Fjomp", DataType.INT);
        newDocType.addField("Fjols", DataType.STRING);

        manager.registerDocumentType(newDocType);

        DocumentType fetchedDocType = manager.getDocumentType(new DataTypeName("testdoc"));

        Field fetched4 = fetchedDocType.getField("Fjomp");

        assertEquals("Fjomp", fetched4.getName());
        assertEquals(fetched4.getDataType(), DataType.INT);
    }

    @Test
    public void testBasicTypes() {
        DocumentTypeManager dtm = new DocumentTypeManager();
        DataType intType = dtm.getDataTypeInternal("int");
        assertSame(DataType.INT, intType);

        DataType stringType = dtm.getDataTypeInternal("string");
        assertSame(DataType.STRING, stringType);

        DataType longType = dtm.getDataTypeInternal("long");
        assertSame(DataType.LONG, longType);

        DataType doubleType = dtm.getDataTypeInternal("double");
        assertSame(DataType.DOUBLE, doubleType);
    }

    @Test
    public void testRecursiveRegister() {
        StructDataType struct = new StructDataType("mystruct");
        DataType wset1 = DataType.getWeightedSet(DataType.getArray(DataType.INT));
        DataType wset2 = DataType.getWeightedSet(DataType.getArray(DataType.TAG));
        struct.addField(new Field("foo", wset1));
        struct.addField(new Field("bar", wset2));
        DataType array = DataType.getArray(struct);
        DocumentType docType = new DocumentType("mydoc");
        docType.addField("hmm", array);
        DocumentType docType2 = new DocumentType("myotherdoc");
        docType2.addField("myint", DataType.INT);
        docType2.inherit(docType);

        DocumentTypeManager manager = new DocumentTypeManager();
        manager.register(docType2);

        assertEquals(struct, manager.getDataTypeByCode(struct.getId()));
        assertEquals(struct, manager.getDataTypeInternal("mystruct"));
        assertEquals(wset1, manager.getDataTypeByCode(wset1.getId()));
        assertEquals(wset2, manager.getDataTypeByCode(wset2.getId()));
        assertEquals(array, manager.getDataTypeByCode(array.getId()));
        assertEquals(docType, manager.getDataTypeInternal("mydoc"));
        assertEquals(docType2, manager.getDataTypeInternal("myotherdoc"));
        assertEquals(docType, manager.getDocumentType(new DataTypeName("mydoc")));
        assertEquals(docType2, manager.getDocumentType(new DataTypeName("myotherdoc")));
    }

    @Test
    public void testMultipleDocuments() {
        DocumentType docType1 = new DocumentType("foo0");
        docType1.addField("bar", DataType.INT);

        DocumentType docType2 = new DocumentType("foo1");
        docType2.addField("bar", DataType.STRING);

        DocumentType docType3 = new DocumentType("foo2");
        docType3.addField("bar", DataType.FLOAT);

        DocumentType docType4 = new DocumentType("foo3");
        docType4.addField("bar", DataType.RAW);

        DocumentTypeManager manager = new DocumentTypeManager();
        manager.internalClear();
        manager.register(docType1);
        manager.register(docType2);
        manager.register(docType3);
        manager.register(docType4);

        assertSame(docType1, manager.getDocumentType(new DataTypeName("foo0")));
        assertSame(docType2, manager.getDocumentType(new DataTypeName("foo1")));
        assertSame(docType3, manager.getDocumentType(new DataTypeName("foo2")));
        assertSame(docType4, manager.getDocumentType(new DataTypeName("foo3")));

        assertEquals(manager.getDocumentTypes().size(), 5);
        assertNotNull(manager.getDocumentTypes().get(new DataTypeName("document")));
        assertEquals(manager.getDocumentTypes().get(new DataTypeName("foo0")), docType1);
        assertEquals(manager.getDocumentTypes().get(new DataTypeName("foo0")), new DocumentType("foo0"));
        assertEquals(manager.getDocumentTypes().get(new DataTypeName("foo1")), docType2);
    }

    @Test
    public void testReverseMapOrder() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.map.cfg");

        assertNotNull(manager.getDataTypeByCode(1000));
        assertNotNull(manager.getDataTypeByCode(1001));
    }

    @Test
    public void testConfigure() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.cfg");

        Iterator typeIt = manager.documentTypeIterator();
        DocumentType type = null;

        while (typeIt.hasNext()) {
            type = (DocumentType) typeIt.next();
            if (type.getName().equals("foobar")) {
                break;
            }
        }
        assertNotNull(type);

        assertTrue(type.hasField("foobarfield0"));
        assertTrue(type.hasField("foobarfield1"));

        Field foobarfield0 = type.getField("foobarfield0");
        assertTrue(foobarfield0.getDataType().getCode() == 2);

        Field foobarfield1 = type.getField("foobarfield1");
        assertTrue(foobarfield1.getDataType().getCode() == 4);


        typeIt = manager.documentTypeIterator();
        while (typeIt.hasNext()) {
            type = (DocumentType) typeIt.next();
            if (type.getName().equals("banana")) {
                break;
            }
        }

        assertTrue(type.hasField("bananafield0"));

        Field bananafield0 = type.getField("bananafield0");
        assertTrue(bananafield0.getDataType().getCode() == 16);

        //inheritance:
        Iterator inhIt = type.getInheritedTypes().iterator();

        DocumentType inhType = (DocumentType) inhIt.next();
        assertTrue(inhType.getName().equals("foobar"));

        assertFalse(inhIt.hasNext());

        typeIt = manager.documentTypeIterator();
        while (typeIt.hasNext()) {
            type = (DocumentType) typeIt.next();
            if (type.getName().equals("customtypes")) {
                break;
            }
        }

        assertTrue(type.hasField("arrayfloat"));
        assertTrue(type.hasField("arrayarrayfloat"));

        Field arrayfloat = type.getField("arrayfloat");
        ArrayDataType dataType = (ArrayDataType) arrayfloat.getDataType();
        // assertTrue(dataType.getCode() == 99);
        assertTrue(dataType.getValueClass().equals(Array.class));
        assertTrue(dataType.getNestedType().getCode() == 1);
        assertTrue(dataType.getNestedType().getValueClass().equals(FloatFieldValue.class));


        Field arrayarrayfloat = type.getField("arrayarrayfloat");
        ArrayDataType subType = (ArrayDataType) arrayarrayfloat.getDataType();
        // assertTrue(subType.getCode() == 4003);
        assertTrue(subType.getValueClass().equals(Array.class));
        // assertTrue(subType.getNestedType().getCode() == 99);
        assertTrue(subType.getNestedType().getValueClass().equals(Array.class));
        ArrayDataType subSubType = (ArrayDataType) subType.getNestedType();
        assertTrue(subSubType.getNestedType().getCode() == 1);
        assertTrue(subSubType.getNestedType().getValueClass().equals(FloatFieldValue.class));
    }

    @Test
    public void testConfigureUpdate() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.cfg");

        DocumentType banana = manager.getDocumentType(new DataTypeName("banana"));
        DocumentType customtypes = manager.getDocumentType(new DataTypeName("customtypes"));

        assertNull(banana.getField("newfield"));
        assertEquals(new Field("arrayfloat", 9489, new ArrayDataType(DataType.FLOAT)), customtypes.getField("arrayfloat"));

        var sub = DocumentTypeManagerConfigurer.configure(manager, "file:src/test/document/documentmanager.updated.cfg");
        sub.close();
        banana = manager.getDocumentType(new DataTypeName("banana"));
        customtypes = manager.getDocumentType(new DataTypeName("customtypes"));

        assertEquals(new Field("newfield", 12345, DataType.STRING), banana.getField("newfield"));
        assertNull(customtypes.getField("arrayfloat"));
    }

    @Test
    public void testConfigureWithAnnotations() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.annotationtypes1.cfg");

        /*
  annotation banana {
    field brand type string { }
  }

  annotation food {
    field what type annotationreference<banana> { }
  }

  annotation cyclic {
    field blah type annotationreference<cyclic> { }
  }

  annotation a {
    field foo type annotationreference<b> { }
  }

  annotation b {
  }
         */

        AnnotationTypeRegistry atr = manager.getAnnotationTypeRegistry();
        assertEquals(AnnotationTypes.ALL_TYPES.size() + 5, atr.getTypes().size());

        AnnotationType banana = atr.getType("banana");
        assertTrue(banana.getDataType() instanceof StructDataType);
        assertEquals("annotation.banana", banana.getDataType().getName());
        assertEquals(1, ((StructDataType) banana.getDataType()).getFields().size());
        assertEquals(DataType.STRING, ((StructDataType) banana.getDataType()).getField("brand").getDataType());

        AnnotationType food = atr.getType("food");
        assertTrue(food.getDataType() instanceof StructDataType);
        assertEquals("annotation.food", food.getDataType().getName());
        assertEquals(1, ((StructDataType) food.getDataType()).getFields().size());
        AnnotationReferenceDataType what = (AnnotationReferenceDataType) ((StructDataType) food.getDataType()).getField("what").getDataType();
        assertSame(banana, what.getAnnotationType());

        AnnotationType cyclic = atr.getType("cyclic");
        assertTrue(cyclic.getDataType() instanceof StructDataType);
        assertEquals("annotation.cyclic", cyclic.getDataType().getName());
        assertEquals(1, ((StructDataType) cyclic.getDataType()).getFields().size());
        AnnotationReferenceDataType blah = (AnnotationReferenceDataType) ((StructDataType) cyclic.getDataType()).getField("blah").getDataType();
        assertSame(cyclic, blah.getAnnotationType());

        AnnotationType b = atr.getType("b");
        assertNull(b.getDataType());

        AnnotationType a = atr.getType("a");
        assertTrue(a.getDataType() instanceof StructDataType);
        assertEquals("annotation.a", a.getDataType().getName());
        assertEquals(1, ((StructDataType) a.getDataType()).getFields().size());
        AnnotationReferenceDataType foo = (AnnotationReferenceDataType) ((StructDataType) a.getDataType()).getField("foo").getDataType();
        assertSame(b, foo.getAnnotationType());

    }

    @Test
    public void testConfigureWithAnnotationsWithInheritance() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.annotationtypes2.cfg");

        /*
  annotation fruit {
  }

  annotation banana inherits fruit {
    field brand type string { }
  }

  annotation vehicle {
    field numwheels type int { }
  }

  annotation car inherits vehicle {
    field color type string { }
  }

  annotation intern inherits employee {
    field enddate type long { }
  }

  annotation employee inherits worker {
    field employeeid type int { }
  }

  annotation worker inherits person {
  }

  annotation person {
    field name type string { }
  }
         */

        AnnotationTypeRegistry atr = manager.getAnnotationTypeRegistry();
        assertEquals(AnnotationTypes.ALL_TYPES.size() + 8, atr.getTypes().size());

        AnnotationType fruit = atr.getType("fruit");
        assertNull(fruit.getDataType());

        AnnotationType banana = atr.getType("banana");
        assertTrue(banana.getDataType() instanceof StructDataType);
        assertEquals("annotation.banana", banana.getDataType().getName());
        assertEquals(1, ((StructDataType) banana.getDataType()).getFields().size());
        assertEquals(DataType.STRING, ((StructDataType) banana.getDataType()).getField("brand").getDataType());
        assertEquals(0, ((StructDataType) banana.getDataType()).getInheritedTypes().size());

        AnnotationType vehicle = atr.getType("vehicle");
        assertTrue(vehicle.getDataType() instanceof StructDataType);
        assertEquals("annotation.vehicle", vehicle.getDataType().getName());
        assertEquals(1, ((StructDataType) vehicle.getDataType()).getFields().size());
        assertEquals(DataType.INT, ((StructDataType) vehicle.getDataType()).getField("numwheels").getDataType());
        assertEquals(0, ((StructDataType) vehicle.getDataType()).getInheritedTypes().size());

        AnnotationType car = atr.getType("car");
        assertTrue(car.getDataType() instanceof StructDataType);
        assertEquals("annotation.car", car.getDataType().getName());
        assertEquals(2, ((StructDataType) car.getDataType()).getFields().size());
        assertEquals(DataType.INT, ((StructDataType) car.getDataType()).getField("numwheels").getDataType());
        assertEquals(DataType.STRING, ((StructDataType) car.getDataType()).getField("color").getDataType());
        assertEquals(1, ((StructDataType) car.getDataType()).getInheritedTypes().size());
        assertSame(vehicle.getDataType(), ((StructDataType) car.getDataType()).getInheritedTypes().toArray()[0]);

        AnnotationType person = atr.getType("person");
        assertTrue(person.getDataType() instanceof StructDataType);
        assertEquals("annotation.person", person.getDataType().getName());
        assertEquals(1, ((StructDataType) person.getDataType()).getFields().size());
        assertEquals(DataType.STRING, ((StructDataType) person.getDataType()).getField("name").getDataType());
        assertEquals(0, ((StructDataType) person.getDataType()).getInheritedTypes().size());

        AnnotationType worker = atr.getType("worker");
        assertTrue(worker.getDataType() instanceof StructDataType);
        assertEquals("annotation.worker", worker.getDataType().getName());
        assertEquals(1, ((StructDataType) worker.getDataType()).getFields().size());
        assertEquals(DataType.STRING, ((StructDataType) worker.getDataType()).getField("name").getDataType());
        assertEquals(1, ((StructDataType) worker.getDataType()).getInheritedTypes().size());
        assertSame(person.getDataType(), ((StructDataType) worker.getDataType()).getInheritedTypes().toArray()[0]);

        AnnotationType employee = atr.getType("employee");
        assertTrue(employee.getDataType() instanceof StructDataType);
        assertEquals("annotation.employee", employee.getDataType().getName());
        assertEquals(2, ((StructDataType) employee.getDataType()).getFields().size());
        assertEquals(DataType.STRING, ((StructDataType) employee.getDataType()).getField("name").getDataType());
        assertEquals(DataType.INT, ((StructDataType) employee.getDataType()).getField("employeeid").getDataType());
        assertEquals(1, ((StructDataType) employee.getDataType()).getInheritedTypes().size());
        assertSame(worker.getDataType(), ((StructDataType) employee.getDataType()).getInheritedTypes().toArray()[0]);

        AnnotationType intern = atr.getType("intern");
        assertTrue(intern.getDataType() instanceof StructDataType);
        assertEquals("annotation.intern", intern.getDataType().getName());
        assertEquals(3, ((StructDataType) intern.getDataType()).getFields().size());
        assertEquals(DataType.STRING, ((StructDataType) intern.getDataType()).getField("name").getDataType());
        assertEquals(DataType.INT, ((StructDataType) intern.getDataType()).getField("employeeid").getDataType());
        assertEquals(DataType.LONG, ((StructDataType) intern.getDataType()).getField("enddate").getDataType());
        assertEquals(1, ((StructDataType) intern.getDataType()).getInheritedTypes().size());
        assertSame(employee.getDataType(), ((StructDataType) intern.getDataType()).getInheritedTypes().toArray()[0]);

    }

    @Test
    public void testStructsAnyOrder() {
        /*
search annotationsimplicitstruct {

  document annotationsimplicitstruct {
      field structfield type sct {
      }

      field structarrayfield type array<sct> {
      }
  }

  struct sct {
    field s1 type string {}
    field s2 type string {}
    field s3 type sct {}
    field s4 type foo {}
  }

  struct foo {
    field s1 type int {}
  }
}
         */

        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.structsanyorder.cfg");

        StructDataType foo = (StructDataType) manager.getDataTypeInternal("foo");
        assertNotNull(foo);
        assertEquals(1, foo.getFields().size());
        Field foos1 = foo.getField("s1");
        assertSame(DataType.INT, foos1.getDataType());

        StructDataType sct = (StructDataType) manager.getDataTypeInternal("sct");
        assertNotNull(sct);
        assertEquals(4, sct.getFields().size());
        Field s1 = sct.getField("s1");
        assertSame(DataType.STRING, s1.getDataType());
        Field s2 = sct.getField("s2");
        assertSame(DataType.STRING, s2.getDataType());
        Field s3 = sct.getField("s3");
        assertSame(sct, s3.getDataType());
        Field s4 = sct.getField("s4");
        assertSame(foo, s4.getDataType());
    }

    @Test
    public void testSombrero1() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.sombrero1.cfg");

        {
            StringFieldValue sfv = new StringFieldValue("ballooooo");
            String text = sfv.getString();
            AnnotationType type = manager.getAnnotationTypeRegistry().getType("base");
            StructuredFieldValue value = (StructuredFieldValue) type.getDataType().createFieldValue();
            value.setFieldValue("x", 10);
            SpanNode span = new Span(0, text.length());
            SpanTree tree = new SpanTree("span", span);
            tree.annotate(span, new Annotation(type, value));
            sfv.setSpanTree(tree);
        }

        {
            StringFieldValue sfv = new StringFieldValue("ballooooo");
            String text = sfv.getString();
            AnnotationType type = manager.getAnnotationTypeRegistry().getType("derived");
            StructuredFieldValue value = (StructuredFieldValue) type.getDataType().createFieldValue();
            value.setFieldValue("x", 10);
            SpanNode span = new Span(0, text.length());
            SpanTree tree = new SpanTree("span", span);
            tree.annotate(span, new Annotation(type, value));
            sfv.setSpanTree(tree);
        }
    }

    @Test
    public void testPolymorphy() {
        /*
  annotation super {
  }
  annotation sub inherits super {
  }
  annotation blah {
    field a type annotationreference<super> {}
  }
         */


        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.annotationspolymorphy.cfg");

        AnnotationType suuper = manager.getAnnotationTypeRegistry().getType("super");
        AnnotationType sub = manager.getAnnotationTypeRegistry().getType("sub");


        //reference type for super annotation type
        AnnotationReferenceDataType refType = (AnnotationReferenceDataType) ((StructDataType) manager.getAnnotationTypeRegistry().getType("blah").getDataType()).getField("a").getDataType();

        Annotation superAnnotation = new Annotation(suuper);
        Annotation subAnnotation = new Annotation(sub);

        new AnnotationReference(refType, superAnnotation);
        //this would fail without polymorphy support:
        new AnnotationReference(refType, subAnnotation);

    }

    @Test
    public void single_reference_type_is_mapped_to_correct_document_target_type() {
        final DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.singlereference.cfg");

        assertReferenceTypePresentInManager(manager, 12345678, "referenced_type");
    }

    private static void assertReferenceTypePresentInManager(DocumentTypeManager manager, int refTypeId,
                                                            String refTargetTypeName) {
        DataType type = manager.getDataTypeByCode(refTypeId);
        assertTrue(type instanceof ReferenceDataType);
        ReferenceDataType refType = (ReferenceDataType) type;

        DocumentType targetDocType = manager.getDocumentType(refTargetTypeName);
        assertTrue(refType.getTargetType() == targetDocType);
    }

    private static DocumentTypeManager createConfiguredManager(String configFilePath) {
        DocumentTypeManager manager = new DocumentTypeManager();
        var sub = DocumentTypeManagerConfigurer.configure(manager, configFilePath);
        sub.close();
        return manager;
    }

    @Test
    public void multiple_reference_types_are_mapped_to_correct_document_target_types() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.multiplereferences.cfg");

        assertReferenceTypePresentInManager(manager, 12345678, "referenced_type");
        assertReferenceTypePresentInManager(manager, 87654321, "referenced_type2");
    }

    @Test
    public void no_temporary_targets_in_references_or_names() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.replaced_temporary.cfg");
        DocumentType docType = manager.getDocumentType("ad");
        Field f = docType.getField("campaign_ref");
        assertTrue(f.getDataType() instanceof ReferenceDataType);
        assertEquals("Reference<mystiqueCampaign>", f.getDataType().getName());
    }

    @Test
    public void can_have_reference_type_pointing_to_own_document_type() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.selfreference.cfg");

        assertReferenceTypePresentInManager(manager, 12345678, "type_with_ref");
    }

    @Test
    public void reference_field_has_correct_reference_type() {
        DocumentTypeManager manager = createConfiguredManager("file:src/test/document/documentmanager.singlereference.cfg");

        DocumentType docType = manager.getDocumentType("type_with_ref");
        Field field = docType.getField("my_ref_field");

        assertTrue(field.getDataType() instanceof ReferenceDataType);
        ReferenceDataType fieldRefType = (ReferenceDataType) field.getDataType();

        DocumentType targetDocType = manager.getDocumentType("referenced_type");
        assertTrue(fieldRefType.getTargetType() == targetDocType);
    }

    @Test
    public void imported_fields_are_empty_if_no_fields_provided_in_config() {
        var manager = createConfiguredManager("file:src/test/document/documentmanager.singlereference.cfg");
        var docType = manager.getDocumentType("type_with_ref");

        assertNotNull(docType.getImportedFieldNames());
        assertEquals(docType.getImportedFieldNames().size(), 0);
        assertFalse(docType.hasImportedField("foo"));
    }

    @Test
    public void imported_fields_are_populated_from_config() {
        var manager = createConfiguredManager("file:src/test/document/documentmanager.importedfields.cfg");
        var docType = manager.getDocumentType("type_with_ref");

        var expectedFields = new HashSet<String>();
        expectedFields.add("my_cool_imported_field");
        expectedFields.add("my_awesome_imported_field");
        assertEquals(docType.getImportedFieldNames(), expectedFields);

        assertTrue(docType.hasImportedField("my_cool_imported_field"));
        assertTrue(docType.hasImportedField("my_awesome_imported_field"));
        assertFalse(docType.hasImportedField("a_missing_imported_field"));
    }

    @Test
    public void position_type_is_recognized_as_v8() {
        var manager = DocumentTypeManager.fromFile("src/test/document/documentmanager.testv8pos.cfg");
        var docType = manager.getDocumentType("foobar");
        var simplepos = docType.getField("simplepos").getDataType();
        assertTrue(simplepos instanceof StructDataType);
        var arraypos = docType.getField("arraypos").getDataType();
        assertTrue(arraypos instanceof ArrayDataType);
        var array = (ArrayDataType) arraypos;
        assertTrue(array.getNestedType() instanceof StructDataType);
    }

    @Test
    public void declared_struct_types_available() {
        var manager = DocumentTypeManager.fromFile("src/test/document/documentmanager.declstruct.cfg");
        var docType = manager.getDocumentType("foo");
        var struct = docType.getDeclaredStructType("mystructinfoo");
        assertNotNull(struct);
        struct = docType.getStructType("mystructinfoo");
        assertNotNull(struct.getField("f1"));
        struct = docType.getDeclaredStructType("mystructinbar");
        assertNull(struct);
        struct = docType.getStructType("mystructinbar");
        assertNull(struct);
        struct = docType.getDeclaredStructType("mystructinfoobar");
        assertNull(struct);
        struct = docType.getStructType("mystructinfoobar");
        assertNull(struct);
        struct = docType.getDeclaredStructType("mystruct");
        assertNull(struct);
        struct = docType.getStructType("mystruct");
        assertNotNull(struct);
        assertNotNull(struct.getField("f0"));

        docType = manager.getDocumentType("bar");
        struct = docType.getDeclaredStructType("mystructinfoo");
        assertNull(struct);
        struct = docType.getStructType("mystructinfoo");
        assertNull(struct);
        struct = docType.getDeclaredStructType("mystructinbar");
        assertNotNull(struct);
        assertNotNull(struct.getField("f2"));
        struct = docType.getStructType("mystructinbar");
        assertNotNull(struct);
        assertNotNull(struct.getField("f2"));
        struct = docType.getDeclaredStructType("mystructinfoobar");
        assertNull(struct);
        struct = docType.getStructType("mystructinfoobar");
        assertNull(struct);
        struct = docType.getDeclaredStructType("mystruct");
        assertNull(struct);
        struct = docType.getStructType("mystruct");
        assertNotNull(struct);
        assertNotNull(struct.getField("f0"));

        docType = manager.getDocumentType("foobar");
        struct = docType.getDeclaredStructType("mystructinfoo");
        assertNull(struct);
        struct = docType.getStructType("mystructinfoo");
        assertNotNull(struct);
        assertNotNull(struct.getField("f1"));
        struct = docType.getDeclaredStructType("mystructinbar");
        assertNull(struct);
        struct = docType.getStructType("mystructinbar");
        assertNotNull(struct);
        assertNotNull(struct.getField("f2"));
        struct = docType.getDeclaredStructType("mystructinfoobar");
        assertNotNull(struct);
        assertNotNull(struct.getField("f3"));
        struct = docType.getStructType("mystructinfoobar");
        assertNotNull(struct);
        assertNotNull(struct.getField("f3"));
        struct = docType.getDeclaredStructType("mystruct");
        assertNull(struct);
        struct = docType.getStructType("mystruct");
        assertNotNull(struct);
        assertNotNull(struct.getField("f0"));

        assertNull(manager.getDataTypeInternal("mystruct@common"));
        assertNull(manager.getDataTypeInternal("mystructinfoo@foo"));
        assertNull(manager.getDataTypeInternal("mystructinbar@bar"));
        assertNull(manager.getDataTypeInternal("mystructinfoobar@foobar"));
        assertNull(manager.getDataTypeInternal("mystruct"));
        assertNull(manager.getDataTypeInternal("mystructinfoo"));
        assertNull(manager.getDataTypeInternal("mystructinbar"));
        assertNull(manager.getDataTypeInternal("mystructinfoobar"));
        assertNull(manager.getDataTypeInternal("foo.header"));
        assertNull(manager.getDataTypeInternal("position"));
    }

    // TODO test clone(). Also fieldSets not part of clone()..!

    // TODO add imported field to equals()/hashCode() for DocumentType? fieldSets not part of this...

}
