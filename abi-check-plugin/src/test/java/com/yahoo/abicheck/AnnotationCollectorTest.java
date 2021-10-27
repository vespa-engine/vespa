// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import com.yahoo.abicheck.collector.AnnotationCollector;
import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

public class AnnotationCollectorTest {

  @Test
  public void testCollection() throws IOException {
    ClassReader r = new ClassReader(
        "com.yahoo.abicheck.AnnotationCollectorTest$ClassWithAnnotations");
    AnnotationCollector collector = new AnnotationCollector();
    r.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    assertThat(collector.getAnnotations(), containsInAnyOrder(Disabled.class.getCanonicalName()));
  }

  @Disabled // Any RetentionPolicy.RUNTIME annotation is fine for testing
  private static class ClassWithAnnotations {

  }
}
