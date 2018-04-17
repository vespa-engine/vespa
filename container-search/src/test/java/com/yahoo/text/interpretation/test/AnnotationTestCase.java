// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.interpretation.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.yahoo.text.interpretation.AnnotationClass;
import com.yahoo.text.interpretation.Annotations;
import com.yahoo.text.interpretation.Interpretation;
import com.yahoo.text.interpretation.Span;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Arne Bergene Fossaa
 */
public class AnnotationTestCase {

    @Test
    public void testSimpleAnnotations() {
        Interpretation i= new Interpretation("new york hotel");
        i.annotate("sentence").put("isValid",true);
        i.annotate(0,3,"token");
        i.annotate(0,8,"place_name").put("^taxonomy:place_category","city");
        i.annotate(0,8,"place_name").put("woeid","12345678");
        //i.getInterpretationAnnotation().put("domain","jaffa");
        i.setProbability(0.5);

        assertNotNull(i.get("sentence"));
    }

    @Test
    public void testAnnotationAPI() {
        Interpretation a = new Interpretation("new york hotel");

        a.annotate(0,3,"token");
        a.annotate(0,8,"state").put("name","New York");
        a.annotate(0,8,"state").put("country","US");
        a.annotate(0,8,"state").put("coast","east");
        a.annotate(9,14,"business");
        a.annotate(4,8,"token");
        a.annotate(9,14,"token");

        for(Span span : a.getTokens()) {
            assertTrue(span.hasClass(new AnnotationClass("token")));
        }

        Set<AnnotationClass> annotationClasses = a.getClasses(0,3);
        Set<AnnotationClass> testClass = new HashSet<>(Arrays.asList(
                new AnnotationClass("token"), new AnnotationClass("state")));
        assertEquals(testClass,annotationClasses);

        assertNull(a.get("state","country"));
        assertEquals("US", a.get(0,8,"state","country"));

        assertEquals("new york", a.root().getSubSpans().get(0).getText());
        assertEquals("hotel", a.root().getSubSpans().get(1).getText());
        assertEquals(2,a.root().getSubSpans().size());


        //Test scoring
        a.setProbability(5);
        Interpretation b = new Interpretation("new york hotel");
        b.setProbability(3);

        //Test the interpretation API
        a.annotate("vespa_query");

        assertNotNull(a.get("vespa_query"));

        //This is bad about the API, getTokens may not necessairily return what a user thinks a token is
        //But it should still be tested
        a.annotate(0,1,"n");
        Set<String> testSet = new HashSet<>(Arrays.asList("n","york","hotel"));
        for(Span span:a.getTokens()) {
            assertTrue(testSet.remove(span.getText()));
        }
        assertEquals(0,testSet.size());
    }

    //The following testcase is a test with the api on a use_case, no cornercases here
    @Test
    public void testUsability() {

        Interpretation interpretation = new Interpretation("new york crab pizza");
        interpretation.annotate(0,8,"place_name").put("^taxonomy:place_category","city");
        interpretation.annotate(0,8,"place_name").put("woe_id",2459115);
        interpretation.annotate(9,13,"food");
        interpretation.annotate(14,19,"food");

        //Here we want to write code that finds out if the interpretation
        //matches pizza and toppings.

        List<Span> pizzaSpans = interpretation.getTermSpans("pizza");

        if(pizzaSpans.size() > 0) {
            //We know that we have pizza, now we want to get some topping
            //In a perfect world, pizza topping would have its own annotation class
            //but for now, we'll just accept terms that have been tokenized with food

            List<String> toppings = new ArrayList<>();
            for(Annotations annotations :interpretation.getAll("food")) {
                if(!annotations.getSubString().equalsIgnoreCase("pizza")) {
                    toppings.add(annotations.getSubString());
                }
            }
            //We also want to find out where we should search for pizza places
            //Since we know that our interpreter engine is smart, we know
            //that all spans that has the annotation "place_name" has a "woe_id".
            int woe_id = 0;

            for(Annotations annotations :interpretation.getAll("place_name")) {
                //This will return either 0 or throw a bad exception
                //if a number is not found
                woe_id = annotations.getInteger("woe_id");
            }
            assertEquals(Arrays.asList("crab"),toppings);
            assertEquals(2459115,woe_id);

        }
    }

}
