// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.interpretation;

// TODO: Javadoc
// TODO: Eventually we need to define the set of classes available here

public class AnnotationClass {

    private String clazz;

    public AnnotationClass(String clazz) {
        this.clazz = clazz;
    }

    public String getClazz() {
        return clazz;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnotationClass)) {
            return false;
        }
        AnnotationClass aClass = (AnnotationClass)o;
        return aClass.clazz == null ? clazz == null : clazz.equals(aClass.getClazz());
    }

    @Override
    public int hashCode() {
        return clazz == null ? 0 : clazz.hashCode();
    }


}
