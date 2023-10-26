// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;

import com.yahoo.fsa.FSA;


/**
 * Class for accessing meta-data (dat-files) used by FSA applications.
 *
 * @author  <a href="mailto:boros@yahoo-inc.com">Peter Boros</a>
 **/
public class MetaData {

  private boolean          _ok = false;
  private MappedByteBuffer _header;
  private MappedByteBuffer _data;
  private Charset          _charset;


  public MetaData(String filename){
    init(filename, "utf-8");
  }

  public MetaData(String filename, String charsetname){
    init(filename, charsetname);
  }

  public boolean isOk(){
    return _ok;
  }

  private void init(String filename, String charsetname){

    _charset = Charset.forName(charsetname);

    FileInputStream file;
    try {
      file = new FileInputStream(filename);
    }
    catch (FileNotFoundException e) {
      System.out.print("MetaData file " + filename + " not found.\n");
      return;
    }

    try {
      _header = file.getChannel().map(MapMode.READ_ONLY,0,256);
      _header.order(ByteOrder.LITTLE_ENDIAN);
      if(h_magic()!=-2025936501){
        System.out.print("MetaData bad magic " + h_magic() +"\n");
        return;
      }
      _data = file.getChannel().map(MapMode.READ_ONLY,
                                    256,
                                    h_size());
      _data.order(ByteOrder.LITTLE_ENDIAN);
      _ok=true;
    }
    catch (IOException e) {
      System.out.print("MetaData IO exception.\n");
      return;
    }
  }

  private int h_magic(){
    return _header.getInt(0);
  }
  private int h_version(){
    return _header.getInt(4);
  }
  private int h_checksum(){
    return _header.getInt(8);
  }
  private int h_size(){
    return _header.getInt(12);
  }
  private int h_reserved(int i){
    if(i<0||i>9){
      return 0;
    }
    return _header.getInt(16+4*i);
  }
  private int h_user(int i){
    if(i<0||i>49){
      return 0;
    }
    return _header.getInt(56+4*i);
  }


  private ByteBuffer encode(CharBuffer chrbuf){
    return _charset.encode(chrbuf);
  }

  private String decode(ByteBuffer buf){
    return _charset.decode(buf).toString();
  }


  public int user(int i){
    if(!_ok){
      return 0;
    }
    return h_user(i);
  }

  public int getIntEntry(int idx)
  {
    if(_ok){
      return _data.getInt(idx*4);
    }
    else
      return 0;
  }

  public ByteBuffer getDirectRecordEntry(int idx, int size)
  {
    if(_ok){
      ByteBuffer meta = ByteBuffer.allocate(size);
      meta.order(ByteOrder.LITTLE_ENDIAN);
      _data.position(idx*size);
      _data.get(meta.array(),0,size);
      return meta;
    }
    else
      return null;
  }

  public ByteBuffer getIndirectRecordEntry(int idx, int size)
  {
    if(_ok){
      int offset = _data.getInt(idx*4);
      ByteBuffer meta = ByteBuffer.allocate(size);
      meta.order(ByteOrder.LITTLE_ENDIAN);
      _data.position(offset);
      _data.get(meta.array(),0,size);
      return meta;
    }
    else
      return null;
  }

  public ByteBuffer getIndirectRecordEntry(int idx)
  {
    if(_ok){
      int offset = _data.getInt(idx*4);
      int size = _data.getInt(offset);
      ByteBuffer meta = ByteBuffer.allocate(size);
      meta.order(ByteOrder.LITTLE_ENDIAN);
      _data.position(offset+4);
      _data.get(meta.array(),0,size);
      return meta;
    }
    else
      return null;
  }

  public String getStringEntry(int stringOffset){
    if(_ok){
      int length = 0;
      _data.position(stringOffset);
      while(_data.get()!=0){
        length++;
      }
      ByteBuffer meta = ByteBuffer.allocate(length);
      meta.order(ByteOrder.LITTLE_ENDIAN);
      _data.position(stringOffset);
      _data.get(meta.array(),0,length);
      return decode(meta);
    }
    return null;
  }

  public String[] getStringArrayEntry(int stringOffset, int numStrings){
    if(_ok && numStrings>0){
      String[] stringArray = new String[numStrings];
      int pos=stringOffset;
      for(int i=0;i<numStrings;i++){
        int length = 0;
        _data.position(pos);
        while(_data.get()!=0){
          length++;
        }
        ByteBuffer meta = ByteBuffer.allocate(length);
        meta.order(ByteOrder.LITTLE_ENDIAN);
        _data.position(pos);
        _data.get(meta.array(),0,length);
        stringArray[i] = decode(meta);
        pos += length+1;
      }
      return stringArray;
    }
    return null;
  }

  //// test ////
  public static void main(String[] args) {
    String file = "dmozPred_2.dat";

    MetaData metaData = new MetaData(file);

    System.out.println("Loading MetaData "+file+": "+metaData.isOk());
  }



}
