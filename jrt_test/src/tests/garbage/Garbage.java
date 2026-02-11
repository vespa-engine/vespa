// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.jrt.*;
import java.net.Socket;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;

public class Garbage implements FatalErrorHandler {

    boolean error = false;
    int     port  = 0;

    public void handleFailure(Throwable t, Object o) {
	System.err.println("FATAL ERROR -> " + o);
	t.printStackTrace();
	error = true;
    }

    class Closer implements Runnable {
        private boolean done  = false;
        private Socket socket = null;

        public Closer(Socket s) {
            socket = s;
        }

        public void done() {
            synchronized (this) {
                done = true;
                notify();
            }
        }

        public void run() {
            long t = System.currentTimeMillis();
            long end = t + 60000;
            synchronized (this) {
                while (!done) {
                    if (t >= end) {
                        error = true;
                        try { socket.close(); } catch (IOException e) {
                            System.err.println("AAARGH!!");
                            System.exit(1);
                        }
                        return;
                    }
                    try { wait(end - t); } catch (InterruptedException ignore) {}
                    t = System.currentTimeMillis();
                }
            }
        }
    }

    public void myMain() {
	Supervisor server = new Supervisor(new Transport(this));
	try {
	    port = server.listen(new Spec(0)).port(); // random port
	} catch (ListenFailedException e) {
	    System.err.println("Listen failed");
	    System.exit(1);
	}

        try {
            Socket s = new Socket("localhost", port);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            byte[] data = {(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                           (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                           (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};
            out.write(data);
            out.flush();
            Closer closer = new Closer(s);
            new Thread(closer).start();
            try {
                if (in.read() != -1) {
                    error = true;
                }
            } finally {
                closer.done();
            }
        } catch (IOException e) {
	    System.err.println(e);
            error = true;
        }

	server.transport().shutdown().join();

	if (error) {
	    System.err.println("TEST FAILED!");
	    System.exit(1);
	} else {
	    System.err.println("test passed");
        }
    }

    public static void main(String[] args) {
	new Garbage().myMain();
    }
}
