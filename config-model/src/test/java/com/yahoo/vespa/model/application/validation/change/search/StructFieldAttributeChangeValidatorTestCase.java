package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRefeedAction;

/**
 * @author geirst
 */
public class StructFieldAttributeChangeValidatorTestCase {

    private static class Fixture extends ContentClusterFixture {
        private StructFieldAttributeChangeValidator structFieldAttributeValidator;
        private DocumentTypeChangeValidator docTypeValidator;

        public Fixture(String currentSd, String nextSd) throws Exception {
            super(currentSd, nextSd);
            structFieldAttributeValidator = new StructFieldAttributeChangeValidator(currentDocType(),
                    currentDb().getDerivedConfiguration().getAttributeFields(),
                    nextDocType(),
                    nextDb().getDerivedConfiguration().getAttributeFields());
            docTypeValidator = new DocumentTypeChangeValidator(currentDocType(), nextDocType());
        }

        @Override
        public List<VespaConfigChangeAction> validate() {
            List<VespaConfigChangeAction> result = new ArrayList<>();
            result.addAll(structFieldAttributeValidator.validate(ValidationOverrides.empty, Instant.now()));
            result.addAll(docTypeValidator.validate(ValidationOverrides.empty, Instant.now()));
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
    public void adding_attribute_aspect_to_struct_field_requires_refeed() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), ""),
                arrayOfStruct(oneFieldStruct(), structAttribute("s1")),
                newRefeedAction("field-type-change", "Field 'f1.s1' changed: add attribute aspect"));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(oneFieldStruct(), structAttribute("key")),
                newRefeedAction("field-type-change", "Field 'f1.key' changed: add attribute aspect"));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(oneFieldStruct(), structAttribute("value.s1")),
                newRefeedAction("field-type-change", "Field 'f1.value.s1' changed: add attribute aspect"));

        validate(mapOfPrimitive(""), mapOfPrimitive(structAttribute("key")),
                newRefeedAction("field-type-change", "Field 'f1.key' changed: add attribute aspect"));

        validate(mapOfPrimitive(""), mapOfPrimitive(structAttribute("value")),
                newRefeedAction("field-type-change", "Field 'f1.value' changed: add attribute aspect"));
    }

    @Test
    public void removing_attribute_aspect_from_struct_field_is_ok() throws Exception {
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
    public void adding_struct_field_with_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), ""),
                arrayOfStruct(twoFieldStruct(), structAttribute("s2")));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(twoFieldStruct(), structAttribute("value.s2")));
    }

    @Test
    public void removing_struct_field_with_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(twoFieldStruct(), structAttribute("s2")),
                arrayOfStruct(oneFieldStruct(), ""));

        validate(mapOfStruct(twoFieldStruct(), structAttribute("value.s2")),
                mapOfStruct(oneFieldStruct(), ""));
    }

    @Test
    public void adding_struct_field_without_attribute_aspect_is_ok() throws Exception {
        validate(arrayOfStruct(oneFieldStruct(), ""),
                arrayOfStruct(twoFieldStruct(), ""));

        validate(mapOfStruct(oneFieldStruct(), ""),
                mapOfStruct(twoFieldStruct(), ""));
    }

    @Test
    public void removing_struct_field_without_attribute_aspect_is_ok() throws Exception {
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
