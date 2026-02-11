// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


import com.yahoo.jrt.*;
import com.yahoo.jrt.slobrok.api.*;
import java.util.Comparator;
import java.util.Arrays;
import java.util.ArrayList;


public class SlobrokAPITest {

    private static class SpecList extends ArrayList {
	public SpecList add(String name, String spec) {
	    add(new Mirror.Entry(name, spec));
	    return this;
	}
    }

    String[]   slobroks;
    SlobrokList slist = new SlobrokList();
    boolean    error    = false;
    Supervisor server   = new Supervisor(new Transport());
    Supervisor client   = new Supervisor(new Transport());
    Acceptor   acceptor = null;
    Mirror     mirror   = null;
    Register   register = null;
    String     mySpec   = null;

    public SlobrokAPITest(String slobrokSpec) throws ListenFailedException {
	slobroks = new String[1];
	slobroks[0] = slobrokSpec;
        slist.setup(slobroks);
	acceptor = server.listen(new Spec(0));
	mirror = new Mirror(client, slist);
	register = new Register(server, slist,
				"localhost", acceptor.port());
	mySpec = new Spec("localhost", acceptor.port()).toString();
    }

    void shutdown() {
	register.shutdown();
	mirror.shutdown();
	acceptor.shutdown();
	client.transport().shutdown();
	server.transport().shutdown();
    }

    void check(String pattern, ArrayList result) {
	Comparator cmp = new Comparator() {
		public int compare(Object a, Object b) {
		    Mirror.Entry x = (Mirror.Entry) a;
		    Mirror.Entry y = (Mirror.Entry) b;
                    return x.compareTo(y);
		}
	    };
	Mirror.Entry[] expect
	    = (Mirror.Entry[]) result.toArray(new Mirror.Entry[result.size()]);
	Arrays.sort(expect, cmp);
	Mirror.Entry[] actual = new Mirror.Entry[0];
	for (int i = 0; i < 600; i++) {
	    actual = mirror.lookup(pattern);
	    Arrays.sort(actual, cmp);
	    if (Arrays.equals(actual, expect)) {
		err("lookup successful for pattern: " + pattern);
		return;
	    }
	    try { Thread.sleep(100); } catch (InterruptedException e) {}
	}
	error = true;
	err("lookup failed for pattern: " + pattern);
	err("actual values:");
	if (actual.length == 0) {
	    err("  { EMPTY }");
	}
	for (int i = 0; i < actual.length; i++) {
	    err("  {" + actual[i].getName() + ", " + actual[i].getSpec() + "}");
	}
	err("expected values:");
	if (expect.length == 0) {
	    err("  { EMPTY }");
	}
	for (int i = 0; i < expect.length; i++) {
	    err("  {" + expect[i].getName() + ", " + expect[i].getSpec() + "}");
	}
    }

    public void runTests() throws Exception {
	try {
	    register.registerName("A/x/w");
	    check("A/x/w", new SpecList().add("A/x/w", mySpec));
	    check("*/*", new SpecList());
	    check("*/*/*", new SpecList().add("A/x/w", mySpec));

	    register.registerName("B/x");
	    check("B/x", new SpecList().add("B/x", mySpec));
	    check("*/*", new SpecList().add("B/x", mySpec));
	    check("*/*/*", new SpecList().add("A/x/w", mySpec));

	    register.registerName("C/x/z");
	    check("C/x/z", new SpecList().add("C/x/z", mySpec));
	    check("*/*", new SpecList().add("B/x", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec));

	    register.registerName("D/y/z");
	    check("D/y/z", new SpecList().add("D/y/z", mySpec));
	    check("*/*", new SpecList().add("B/x", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec)
		  .add("D/y/z", mySpec));

	    register.registerName("E/y");
	    check("E/y", new SpecList().add("E/y", mySpec));
	    check("*/*", new SpecList()
		  .add("B/x", mySpec)
		  .add("E/y", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec)
		  .add("D/y/z", mySpec));

	    register.registerName("F/y/w");
	    check("F/y/w", new SpecList().add("F/y/w", mySpec));
	    check("*/*", new SpecList()
		  .add("B/x", mySpec)
		  .add("E/y", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec)
		  .add("D/y/z", mySpec)
		  .add("F/y/w", mySpec));

	    check("*", new SpecList());

	    check("B/*", new SpecList()
		  .add("B/x", mySpec));

	    check("*/y", new SpecList()
		  .add("E/y", mySpec));

	    check("*/x/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec));

	    check("*/*/z", new SpecList()
		  .add("C/x/z", mySpec)
		  .add("D/y/z", mySpec));

	    check("A/*/z", new SpecList());

	    check("A/*/w", new SpecList()
		  .add("A/x/w", mySpec));

	    register.unregisterName("E/y");
	    register.unregisterName("C/x/z");
	    register.unregisterName("F/y/w");
	    check("*/*", new SpecList()
		  .add("B/x", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("D/y/z", mySpec));

	    register.registerName("E/y");
	    register.registerName("C/x/z");
	    register.registerName("F/y/w");
	    check("*/*", new SpecList()
		  .add("B/x", mySpec)
		  .add("E/y", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec)
		  .add("D/y/z", mySpec)
		  .add("F/y/w", mySpec));

	    register.unregisterName("E/y");
	    register.unregisterName("C/x/z");
	    register.unregisterName("F/y/w");
	    check("*/*", new SpecList()
		  .add("B/x", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("D/y/z", mySpec));

	    register.registerName("E/y");
	    register.registerName("C/x/z");
	    register.registerName("F/y/w");
	    check("*/*", new SpecList()
		  .add("B/x", mySpec)
		  .add("E/y", mySpec));
	    check("*/*/*", new SpecList()
		  .add("A/x/w", mySpec)
		  .add("C/x/z", mySpec)
		  .add("D/y/z", mySpec)
		  .add("F/y/w", mySpec));

	    if (error) {
		throw new Exception("Test failed");
	    }
	} finally {
	    shutdown();
	}
    }

    public static void main(String[] args) {
	if (args.length != 1) {
	    err("usage: SlobrokAPITest slobrok-spec");
	    System.exit(1);
	}
	try {
	    new SlobrokAPITest(args[0]).runTests();
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(1);
	}
    }

    public static void err(String msg) {
	System.err.println(msg);
    }
}
