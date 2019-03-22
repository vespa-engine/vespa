package com.yahoo.container.core;

public class AccessLogConfig {

  public class FileHandler { 

     public boolean compressOnRotation() { return true; }

     public String pattern() { return ""; }

     public String rotation() { return ""; }

     public String symlink() { return ""; }
}

 public FileHandler fileHandler() { return null; };

  

}
