// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRestartAction;

/**
 * @author geirst
 */
public class StructFieldAttributeChangeValidatorTestCase {

    private static class Fixture extends ContentClusterFixture {

        private final StructFieldAttributeChangeValidator structFieldAttributeValidator;
        private final DocumentTypeChangeValidator docTypeValidator;

        public Fixture(String currentSd, String nextSd) throws Exception {
            super(currentSd, nextSd);
            structFieldAttributeValidator = new StructFieldAttributeChangeValidator(ClusterSpec.Id.from("test"),
                                                                                    currentDocType(),
                                                                                    currentDb().getDerivedConfiguration().getAttributeFields(),
                                                                                    nextDocType(),
                                                                                    nextDb().getDerivedConfiguration().getAttributeFields());
            docTypeValidator = new DocumentTypeChangeValidator(ClusterSpec.Id.from("test"), currentDocType(), nextDocType());
        }

        @Override
        public List<VespaConfigChangeAction> validate() {
            List<VespaConfigChangeAction> result = new ArrayList<>();
            result.addAll(structFieldAttributeValidator.validate());
            result.addAll(docTypeValidator.validate());
            return result;
        }
    }

    private static void validate(String currentSd, String nextSd) throws Exception {
        new Fixture(currentSd, nextSd).assertValidation();
    }

    private static void validate(String currentSd, String nextSd, VespaConfigChangeAction expAction) throws Exception {
        new Fixture(currentSd, nextSd).assertValidation(expAction);
    }

    @Test
    void adding_attribute_aspect_to_struct_field_requires_restart() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), ""),
                arrayOfStruct(oneFieldStruct(), structAttribute("s1")),
                newRestartAction(ClusterSpec.Id.from("test"), "Field 'f1.s1' changed: add attribute aspect"));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(oneFieldStruct(), structAttribute("key")),
                newRestartAction(ClusterSpec.Id.from("test"), "Field 'f1.key' changed: add attribute aspect"));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(oneFieldStruct(), structAttribute("value.s1")),
                newRestartAction(ClusterSpec.Id.from("test"), "Field 'f1.value.s1' changed: add attribute aspect"));

        validate(mapOfPrimitive(""), mapOfPrimitive(structAttribute("key")),
                newRestartAction(ClusterSpec.Id.from("test"), "Field 'f1.key' changed: add attribute aspect"));

        validate(mapOfPrimitive(""), mapOfPrimitive(structAttribute("value")),
                newRestartAction(ClusterSpec.Id.from("test"), "Field 'f1.value' changed: add attribute aspect"));
    }

    @Test
    void removing_attribute_aspect_from_struct_field_is_ok() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), structAttribute("s1")),
                arrayOfStruct(oneFieldStruct(), ""));

        validate(mapOfStruct(oneFieldStruct(), structAttribute("key")),
                mapOfStruct(oneFieldStruct(), ""));

        validate(mapOfStruct(oneFieldStruct(), structAttribute("value.s1")),
                mapOfStruct(oneFieldStruct(), ""));

        validate(mapOfPrimitive(structAttribute("key")), mapOfPrimitive(""));

        validate(mapOfPrimitive(structAttribute("value")), mapOfPrimitive(""));
    }

    @Test
    void adding_struct_field_with_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), ""),
                arrayOfStruct(twoFieldStruct(), structAttribute("s2")));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(twoFieldStruct(), structAttribute("value.s2")));
    }

    @Test
    void removing_struct_field_with_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(twoFieldStruct(), structAttribute("s2")),
                arrayOfStruct(oneFieldStruct(), ""));

        validate(mapOfStruct(twoFieldStruct(), structAttribute("value.s2")),
                mapOfStruct(oneFieldStruct(), ""));
    }

    @Test
    void adding_struct_field_without_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), ""),
                arrayOfStruct(twoFieldStruct(), ""));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(twoFieldStruct(), ""));
    }

    @Test
    void removing_struct_field_without_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(twoFieldStruct(), ""),
                arrayOfStruct(oneFieldStruct(), ""));

        validate(mapOfStruct(twoFieldStruct(), ""),
                mapOfStruct(oneFieldStruct(), ""));
    }

    private static String oneFieldStruct() {
        return "struct s { field s1 type string {} }";
    }

    private static String twoFieldStruct() {
        return "struct s { field s1 type string {} field s2 type int {} }";
    }

    private static String structAttribute(String fieldName) {
        return "struct-field " + fieldName + " { indexing: attribute }";
    }

    private static String arrayOfStruct(String struct, String structField) {
        return struct + " field f1 type array<s> { " + structField + "}";
    }

    private static String mapOfStruct(String struct, String structField) {
        return struct + " field f1 type map<string,s> { " + structField + " }";
    }

    private static String mapOfPrimitive(String structField) {
        return "field f1 type map<string,int> { " + structField + " }";
    }

}
