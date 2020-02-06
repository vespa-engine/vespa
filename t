diff --git a/jrt/src/com/yahoo/jrt/Connector.java b/jrt/src/com/yahoo/jrt/Connector.java
index 98bf512454..0fdb6cfa38 100644
--- a/jrt/src/com/yahoo/jrt/Connector.java
+++ b/jrt/src/com/yahoo/jrt/Connector.java
@@ -4,13 +4,16 @@ package com.yahoo.jrt;
 import com.yahoo.concurrent.ThreadFactoryFactory;
 
 import java.util.concurrent.ExecutorService;
-import java.util.concurrent.Executors;
+import java.util.concurrent.LinkedBlockingQueue;
 import java.util.concurrent.RejectedExecutionException;
+import java.util.concurrent.ThreadPoolExecutor;
 import java.util.concurrent.TimeUnit;
 
 class Connector {
 
-    private final ExecutorService executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector"));
+    private final ExecutorService executor = new ThreadPoolExecutor(1, 8, 10L, TimeUnit.MILLISECONDS,
+                                                                    new LinkedBlockingQueue<>(),
+                                                                    ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector"));
 
     private void connect(Connection conn) {
         conn.transportThread().addConnection(conn.connect());
