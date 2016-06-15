// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at http://www.sun.com/cddl/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * The Original Software is SezPoz. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 */package org.jvnet.hudson.annotation_indexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class Index {
    /**
     * Lists up all the elements annotated by the given annotation and of the given {@link AnnotatedElement} subtype.
     */
    public static <T extends AnnotatedElement> Iterable<T> list(Class<? extends Annotation> type, ClassLoader cl, final Class<T> subType) throws IOException {
        final Iterable<AnnotatedElement> base = list(type,cl);
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return new FilterIterator(base.iterator()) {
                    protected boolean filter(Object o) {
                        return subType.isInstance(o);
                    }
                };
            }
        };
    }

    /**
     * Lists up all the elements annotated by the given annotation.
     */
    public static Iterable<AnnotatedElement> list(final Class<? extends Annotation> type, final ClassLoader cl) throws IOException {
        if (!type.isAnnotationPresent(Indexed.class))
            throw new IllegalArgumentException(type+" doesn't have @Indexed");

        final Set<String> ids = new TreeSet<String>();

        final Enumeration<URL> res = cl.getResources("META-INF/annotations/"+type.getName());
        while (res.hasMoreElements()) {
            URL url = res.nextElement();
            BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            String line;
            while ((line=r.readLine())!=null)
                ids.add(line);
        }

        return new Iterable<AnnotatedElement>() {
            public Iterator<AnnotatedElement> iterator() {
                return new Iterator<AnnotatedElement>() {
                    /**
                     * Next element to return.
                     */
                    private AnnotatedElement next;

                    private Iterator<String> iditr = ids.iterator();

                    private List<AnnotatedElement> lookaheads = new LinkedList<AnnotatedElement>();

                    public boolean hasNext() {
                        fetch();
                        return next!=null;
                    }

                    public AnnotatedElement next() {
                        fetch();
                        AnnotatedElement r = next;
                        next = null;
                        return r;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void fetch() {
                        while (next==null) {
                            if (!lookaheads.isEmpty()) {
                                next = lookaheads.remove(0);
                                return;
                            }

                            if (!iditr.hasNext())   return;
                            String name = iditr.next();

                            try {
                                Class<?> c = cl.loadClass(name);

                                if (c.isAnnotationPresent(type))
                                    lookaheads.add(c);
                                listAnnotatedElements(c.getDeclaredMethods());
                                listAnnotatedElements(c.getDeclaredFields());
                            } catch (ClassNotFoundException e) {
                                LOGGER.log(Level.FINE, "Failed to load: "+name,e);
                            }
                        }
                    }

                    private void listAnnotatedElements(AnnotatedElement[] elements) {
                        for (AnnotatedElement m : elements) {
                            // this means we don't correctly handle
                            if (m.isAnnotationPresent(type))
                                lookaheads.add(m);
                        }
                    }
                };
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(Index.class.getName());
}
