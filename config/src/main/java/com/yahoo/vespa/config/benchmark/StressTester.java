// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.benchmark;

import com.yahoo.jrt.*;
import com.yahoo.system.CommandLineParser;

import java.io.*;
import java.util.*;

/**
 * /**
 * A class for stress-testing config server and config proxy.
 * Includes an RPC server interface for communicating
 * with test classes that implement the {@link Tester} interface.
 *
 * @author hmusum
 * @since 5.1.5
 */
public class StressTester {
    private static boolean debug = false;
    private final String testClassName;
    private final List<Thread> threadList = new ArrayList<>();
    private final List<TestRunner> testRunners = new ArrayList<>();

    public StressTester(String testClass) {
        this.testClassName = testClass;
    }

    /**
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        CommandLineParser parser = new CommandLineParser("StressTester", args);
        parser.addLegalUnarySwitch("-d", "debug");
        parser.addRequiredBinarySwitch("-c", "host (config proxy or server)");
        parser.addRequiredBinarySwitch("-p", "port");
        parser.addLegalBinarySwitch("-class", "Use class with this name from test bundle (must be given in class path)");
        parser.addLegalBinarySwitch("-serverport", "port for rpc server");
        parser.parse();
        // TODO Handle other hosts and ports
        String host = parser.getBinarySwitches().get("-c");
        int port = Integer.parseInt(parser.getBinarySwitches().get("-p"));
        debug = parser.getUnarySwitches().contains("-d");
        String classNameInBundle = parser.getBinarySwitches().get("-class");
        int serverPort = Integer.parseInt(parser.getBinarySwitches().get("-serverport"));
        RpcServer rpcServer = new RpcServer(null, serverPort, new StressTester(classNameInBundle));
        new Thread(rpcServer).start();
    }

    static class TestRunner implements Runnable {
        private final Tester tester;
        private volatile boolean stop = false;

        TestRunner(Tester tester) {
            this.tester = tester;
        }

        @Override
        public void run() {
            tester.subscribe();
            while (!stop) {
                tester.fetch();
            }
            tester.close();
        }

        public void stop() {
            stop = true;
        }
    }

    private Map<String, Map<String, String>> getVerificationMap(String verificationFile) {
        // Read verification file into a map that test stubs should verify against
        Map<String, Map<String, String>> verificationMap = new HashMap<>();
        if (verificationFile != null) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(verificationFile));
                String l;
                while ((l = reader.readLine()) != null) {
                    String[] line = l.split(",");
                    String defFile = line[0];
                    String fieldName = line[1];
                    String expectedValue = line[2];
                    Map<String, String> defExpected = verificationMap.get(defFile);
                    if (defExpected == null)
                        defExpected = new HashMap<>();
                    defExpected.put(fieldName, expectedValue);
                    verificationMap.put(defFile, defExpected);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to load verification file " + verificationFile);
            } finally {
                if (reader != null) try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return verificationMap;
    }

    private void startTesters(int threads) {
        // Load and run actual test stub
        Class<?> testClass;
        try {
            testClass = Class.forName(testClassName);
            threadList.clear();
            testRunners.clear();
            for (int i = 0; i < threads; i++) {
                Tester tester = (Tester) testClass.getDeclaredConstructor().newInstance();
                TestRunner testRunner = new TestRunner(tester);
                testRunners.add(testRunner);
                Thread t = new Thread(testRunner);
                threadList.add(t);
            }
            debug("Starting testers");
            // Now that all testers have been created, start them
            for (Thread t : threadList) {
                debug("Starting thread");
                t.start();
            }
        } catch (Exception e) {
            debug("error in startTesters");
            throw new IllegalArgumentException("Unable to load class with name " + testClassName, e);
        }
        debug("After starting testers");
    }

    public boolean verify(long generation, long timeout, String verificationFile) throws InterruptedException {
        Map<String, Map<String, String>> verificationMap = getVerificationMap(verificationFile);
        for (TestRunner testRunner : testRunners) {
            long start = System.currentTimeMillis();
            boolean ok = false;
            do {
                if (testRunner.tester.verify(verificationMap, generation)) {
                    ok = true;
                }
                Thread.sleep(10);
            } while (!ok && (System.currentTimeMillis() - start < timeout));
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public void stop() {
        debug("Stopping test runners");
        for (TestRunner testRunner : testRunners) {
            testRunner.stop();
        }
        debug("Stopping threads");
        for (Thread t : threadList) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        debug("End of stop");
    }

    private static void debug(String s) {
        if (debug) {
            System.out.println(s);
        }
    }

    public static class RpcServer implements Runnable {
        private Transport transport = new Transport();
        protected Supervisor supervisor = new Supervisor(transport);
        private final Spec spec;
        private final StressTester tester;

        RpcServer(String host, int port, StressTester tester) {
            this.tester = tester;
            setUp(this);
            spec = new Spec(host, port);
        }

        public void run() {
            try {
                Acceptor acceptor = supervisor.listen(spec);
                supervisor.transport().join();
                acceptor.shutdown().join();
            } catch (ListenFailedException e) {
                throw new RuntimeException("Could not listen to " + spec);
            }
        }

        public void shutdown() {
            supervisor.transport().shutdown().join();
        }

        public final void start(Request request) {
            debug("start: Got " + request);
            int ret = 1;
            int clients = request.parameters().get(0).asInt32();
            debug("start: starting testers");
            try {
                tester.startTesters(clients);
                ret = 0;
            } catch (Exception e) {
                debug("start: error: " + e.getMessage());
                e.printStackTrace();
            }
            debug("start: Returning " + ret);
            request.returnValues().add(new Int32Value(ret));
        }

        public final void verify(Request request) {
            debug("verify: Got " + request);
            long generation = request.parameters().get(0).asInt64();
            String verificationFile = request.parameters().get(1).asString();
            long timeout = request.parameters().get(2).asInt64();
            int ret = 0;
            String errorMessage = "";
            try {
                if (!tester.verify(generation, timeout, verificationFile)) {
                    ret = 1;
                    errorMessage = "Unable to get generation " + generation + " within timeout " + timeout;
                }
            } catch (Exception e) {
                ret = 1;
                errorMessage = e.getMessage();
                e.printStackTrace();
            } catch (AssertionError e) {
                ret = 1;
                errorMessage = e.getMessage();
            }
            debug("verify: Returning " + ret);
            request.returnValues().add(new Int32Value(ret));
            request.returnValues().add(new StringValue(errorMessage));
        }

        public final void stop(Request request) {
            debug("stop: Got " + request);
            int ret = 1;
            try {
                tester.stop();
                ret = 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
            debug("stop: Returning " + ret);
            request.returnValues().add(new Int32Value(ret));
        }

        /**
         * Set up RPC method handlers.
         *
         * @param handler a MethodHandler that will handle the RPC methods
         */

        protected void setUp(Object handler) {
            supervisor.addMethod(new Method("start", "i", "i",
                    handler, "start")
                    .methodDesc("start")
                    .paramDesc(0, "clients", "number of clients")
                    .returnDesc(0, "ret code", "return code, 0 is OK"));
            supervisor.addMethod(new Method("verify", "lsl", "is",
                    handler, "verify")
                    .methodDesc("verify")
                    .paramDesc(0, "generation", "config generation")
                    .paramDesc(1, "verification file", "name of verification file")
                    .paramDesc(2, "timeout", "timeout when verifying")
                    .returnDesc(0, "ret code", "return code, 0 is OK")
                    .returnDesc(1, "error message", "error message, if non zero return code"));
            supervisor.addMethod(new Method("stop", "", "i",
                    handler, "stop")
                    .methodDesc("stop")
                    .returnDesc(0, "ret code", "return code, 0 is OK"));
        }
    }
}
