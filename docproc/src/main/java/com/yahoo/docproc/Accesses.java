// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Docprocs tagged with this will read and/or write annotations on the given field(s).
 * 
 * @author vegardh
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Accesses {

    Field[] value();

    /**
     * Describes the annotations produced and consumed on one field in a document
     *
     * @author vegardh
     */
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Field {

        /** The name of the document field */
        String name();
        /** The datatype of the field */
        String dataType();
        /** The trees of annotations that this docproc accesses on this field */
        Tree[] annotations() default {};
        String description();

        /**
         * Describes the annotations produced and consumed in one tree on a field
         *
         * @author vegardh
         */
        @Documented
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface Tree {
            /** The name of the tree */
            String name() default "";
            /** The annotation types that this docproc writes in this tree */
            String[] produces() default {};
            /** The annotation types that this docproc requires in this tree */
            String[] consumes() default {};
        }
    }

}
