// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.jrt.*;


public class TestErrors {

    boolean      error = false;
    Supervisor   client;
    Target       target;

    public void init(String spec) {
	client = new Supervisor(new Transport());
	target = client.connect(new Spec(spec));
    }

    public void fini() {
	target.close();
	client.transport().shutdown().join();
	if (!error) {
	    System.err.println("Conclusion: PASS");
	} else {
	    System.err.println("Conclusion: FAIL");
	    System.exit(1);
	}
    }

    public void assertTrue(boolean value) {
	if (!value) {
	    Throwable tmp = new Throwable();
	    System.out.println("<ASSERT FAILED>");
	    tmp.printStackTrace();
	    error = true;
	}
    }

    public void assertEquals(int e, int a) {
	if (e != a) {
	    Throwable tmp = new Throwable();
	    System.out.println("<ASSERT FAILED>: expected <" + e +
			       ">, but was <" + a + ">");
	    tmp.printStackTrace();
	    error = true;
	}
    }

    public void testNoError() {
	Request req1 = new Request("test");
	req1.parameters().add(new Int32Value(42));
	req1.parameters().add(new Int32Value(0));
	req1.parameters().add(new Int8Value((byte)0));
	target.invokeSync(req1, 60.0);
	assertTrue(!req1.isError());
	assertEquals(1, req1.returnValues().size());
	assertEquals(42, req1.returnValues().get(0).asInt32());
    }

    public void testNoSuchMethod() {
	Request req1 = new Request("bogus");
	target.invokeSync(req1, 60.0);
	assertTrue(req1.isError());
	assertEquals(0, req1.returnValues().size());
	assertEquals(ErrorCode.NO_SUCH_METHOD, req1.errorCode());
    }

    public void testWrongParameters() {
	Request req1 = new Request("test");
	req1.parameters().add(new Int32Value(42));
	req1.parameters().add(new Int32Value(0));
	req1.parameters().add(new Int32Value(0));
	target.invokeSync(req1, 60.0);
	assertTrue(req1.isError());
	assertEquals(0, req1.returnValues().size());
	assertEquals(ErrorCode.WRONG_PARAMS, req1.errorCode());

	Request req2 = new Request("test");
	req2.parameters().add(new Int32Value(42));
	req2.parameters().add(new Int32Value(0));
	target.invokeSync(req2, 60.0);
	assertTrue(req2.isError());
	assertEquals(0, req2.returnValues().size());
	assertEquals(ErrorCode.WRONG_PARAMS, req2.errorCode());

	Request req3 = new Request("test");
	req3.parameters().add(new Int32Value(42));
	req3.parameters().add(new Int32Value(0));
	req3.parameters().add(new Int8Value((byte)0));
	req3.parameters().add(new Int8Value((byte)0));
	target.invokeSync(req3, 60.0);
	assertTrue(req3.isError());
	assertEquals(0, req3.returnValues().size());
	assertEquals(ErrorCode.WRONG_PARAMS, req3.errorCode());
    }

    public void testWrongReturnValues() {
	Request req1 = new Request("test");
	req1.parameters().add(new Int32Value(42));
	req1.parameters().add(new Int32Value(0));
	req1.parameters().add(new Int8Value((byte)1));
	target.invokeSync(req1, 60.0);
	assertTrue(req1.isError());
	assertEquals(0, req1.returnValues().size());
	assertEquals(ErrorCode.WRONG_RETURN, req1.errorCode());	
    }

    public void testMethodFailed() {
	Request req1 = new Request("test");
	req1.parameters().add(new Int32Value(42));
	req1.parameters().add(new Int32Value(75000));
	req1.parameters().add(new Int8Value((byte)0));
	target.invokeSync(req1, 60.0);
	assertTrue(req1.isError());
	assertEquals(0, req1.returnValues().size());
	assertEquals(75000, req1.errorCode());	

	Request req2 = new Request("test");
	req2.parameters().add(new Int32Value(42));
	req2.parameters().add(new Int32Value(75000));
	req2.parameters().add(new Int8Value((byte)1));
	target.invokeSync(req2, 60.0);
	assertTrue(req2.isError());
	assertEquals(0, req2.returnValues().size());
	assertEquals(75000, req2.errorCode());	
    }

    public static void main(String[] args) {
	if (args.length != 1) {
	    System.err.println("Usage: TestErrors spec");
	    System.exit(1);
	}
	TestErrors test = new TestErrors();
	test.init(args[0]);
	test.testNoError();
	test.testNoSuchMethod();
	test.testWrongParameters();
	test.testWrongReturnValues();
	test.testMethodFailed();
	test.fini();
    }
}
