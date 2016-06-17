// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

/**
 * @author baldersheim
 */
public class ComponentIdBenchmark {
    public void run() {
         boolean result=true;
         String strings[] = createStrings(1000);
         // Warm-up
         out("Warming up...");
         for (int i=0; i<30*1000; i++)
             result = result ^ createComponentId(strings);

         long startTime=System.currentTimeMillis();
         out("Running...");
         for (int i=0; i<100*1000; i++)
             result = result ^ createComponentId(strings);
         out("Ignore this: " + result); // Make sure we are not fooled by optimization by creating an observable result
         long endTime=System.currentTimeMillis();
         out("Create anonymous component ids of 1000 strings 100.000 times took " + (endTime-startTime) + " ms");
     }

     private final String [] createStrings(int num) {
         String strings [] = new String [num];
         for(int i=0; i < strings.length; i++) {
             strings[i] = "this.is.a.short.compound.name." + i;
         }
         return strings;
     }

     private final boolean createComponentId(String [] strings) {
         boolean retval = true;
         for (int i=0; i < strings.length; i++) {
             ComponentId n = ComponentId.createAnonymousComponentId(strings[i]);
             retval = retval ^ n.isAnonymous();
         }
         return retval;
     }

     private void out(String string) {
         System.out.println(string);
     }

     public static void main(String[] args) {
         new ComponentIdBenchmark().run();
     }

}
