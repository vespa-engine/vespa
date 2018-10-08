package com.yahoo.vespa.hosted.controller.tenant;

import org.junit.Test;

import java.net.URI;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ContactTest {

    @Test
    public void testSlimeSerialization() {
        Contact contact = new Contact(URI.create("https://localhost:4444/"), URI.create("https://localhost:4444/"), URI.create("https://localhost:4444/"), Arrays.asList(Arrays.asList("foo", "bar")));
        assertEquals(contact, Contact.fromSlime(contact.toSlime()));
    }

}