// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.conceptnet;

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
 * Class for accessing the concept network automata.
 *
 * @author  <a href="mailto:boros@yahoo-inc.com">Peter Boros</a>
 **/
public class ConceptNet {

  private FSA              _fsa;
  private boolean          _ok = false;
  private MappedByteBuffer _header;
  private MappedByteBuffer _index;
  private MappedByteBuffer _info;
  private MappedByteBuffer _catindex;
  private MappedByteBuffer _strings;
  private Charset          _charset;


  public ConceptNet(String domain){
    init(domain, "utf-8");
  }

  public ConceptNet(String domain, String charsetname){
    init(domain, charsetname);
  }

  public boolean isOk(){
    return _ok;
  }

  private void init(String domain, String charsetname){

    _charset = Charset.forName(charsetname);

    _fsa = new FSA(domain + ".fsa",charsetname);

    if(!_fsa.isOk()){
      return;
    }

    FileInputStream file;
    try {
      file = new FileInputStream(domain + ".dat");
    }
    catch (FileNotFoundException e) {
      System.out.print("ConceptNet data file " + domain + ".dat" + " not found.\n");
      return;
    }

    try {
      _header = file.getChannel().map(MapMode.READ_ONLY,0,256);
      _header.order(ByteOrder.LITTLE_ENDIAN);
      if(h_magic()!=238579428){
        System.out.print("ConceptNet bad magic " + h_magic() +"\n");
        return;
      }
      _index    = file.getChannel().map(MapMode.READ_ONLY,
                                        256,
                                        8*4*h_index_size());
      _index.order(ByteOrder.LITTLE_ENDIAN);
      _info     = file.getChannel().map(MapMode.READ_ONLY,
                                        256+8*4*h_index_size(),
                                        4*h_info_size());
      _info.order(ByteOrder.LITTLE_ENDIAN);
      _catindex = file.getChannel().map(MapMode.READ_ONLY,
                                        256+8*4*h_index_size()+4*h_info_size(),
                                        4*h_catindex_size());
      _catindex.order(ByteOrder.LITTLE_ENDIAN);
      _strings  = file.getChannel().map(MapMode.READ_ONLY,
                                        256+8*4*h_index_size()+4*h_info_size()+4*h_catindex_size(),
                                        h_strings_size());
      _strings.order(ByteOrder.LITTLE_ENDIAN);
      _ok=true;
    }
    catch (IOException e) {
      System.out.print("ConceptNet IO exception.\n");
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
  private int h_index_size(){
    return _header.getInt(12);
  }
  private int h_info_size(){
    return _header.getInt(16);
  }
  private int h_catindex_size(){
    return _header.getInt(20);
  }
  private int h_strings_size(){
    return _header.getInt(24);
  }
  private int h_max_freq(){
    return _header.getInt(28);
  }
  private int h_max_cfreq(){
    return _header.getInt(32);
  }
  private int h_max_qfreq(){
    return _header.getInt(36);
  }
  private int h_max_sfreq(){
    return _header.getInt(40);
  }
  private int h_max_efreq(){
    return _header.getInt(44);
  }
  private int h_max_afreq(){
    return _header.getInt(48);
  }


  private ByteBuffer encode(CharBuffer chrbuf){
    return _charset.encode(chrbuf);
  }

  private String decode(ByteBuffer buf){
    return _charset.decode(buf).toString();
  }

  public int lookup(String unit)
  {
    FSA.State state = _fsa.getState();
    // state.start(); // getState does this for us
    state.delta(unit);
    if(state.isFinal()){
      return state.hash();
    }
    return -1;
  }

  public String lookup(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return null;
    }
    int termoffset = _index.getInt(4*8*idx);
    return getString(termoffset);
  }

  private String getString(int stringOffset){
    if(_ok){
      int length = 0;
      _strings.position(stringOffset);
      while(_strings.get()!=0){
        length++;
      }
      ByteBuffer meta = ByteBuffer.allocate(length);
      _strings.position(stringOffset);
      _strings.get(meta.array(),0,length);
      return decode(meta);
    }
    return null;
  }

  public int frq(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return -1;
    }
    return _index.getInt(4*8*idx+4);
  }

  public int cFrq(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return -1;
    }
    return _index.getInt(4*8*idx+8);
  }

  public int qFrq(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return -1;
    }
    return _index.getInt(4*8*idx+12);
  }

  public int sFrq(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return -1;
    }
    return _index.getInt(4*8*idx+16);
  }

  public double score(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return -1.0;
    }
    return 100.0*cFrq(idx)/qFrq(idx);
  }

  public double strength(int idx)
  {
    if(!_ok || idx<0 || idx>=h_index_size()){
      return -1.0;
    }
    return 100.0*qFrq(idx)/sFrq(idx);
  }

  public int numExt(int idx)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+20);
    if(offset==0){
      return 0;
    }
    return _info.getInt(4*offset);
  }

  public int ext(int idx, int i)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+20);
    if(offset==0){
      return -1;
    }
    if(i>=_info.getInt(4*offset)){
      return -1;
    }
    return _info.getInt(4*offset+4+8*i);
  }

  public int extFrq(int idx, int i)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+20);
    if(offset==0){
      return -1;
    }
    if(i>=_info.getInt(4*offset)){
      return -1;
    }
    return _info.getInt(4*offset+8+8*i);
  }

  public int numAssoc(int idx)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+24);
    if(offset==0){
      return 0;
    }
    return _info.getInt(4*offset);
  }

  public int assoc(int idx, int i)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+24);
    if(offset==0){
      return -1;
    }
    if(i>=_info.getInt(4*offset)){
      return -1;
    }
    return _info.getInt(4*offset+4+8*i);
  }

  public int assocFrq(int idx, int i)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+24);
    if(offset==0){
      return -1;
    }
    if(i>=_info.getInt(4*offset)){
      return -1;
    }
    return _info.getInt(4*offset+8+8*i);
  }

  public int numCat(int idx)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+28);
    if(offset==0){
      return 0;
    }
    return _info.getInt(4*offset);
  }

  public int cat(int idx, int i)
  {
    if(idx<0 || idx>=h_index_size()){
      return -1;
    }
    int offset = _index.getInt(4*8*idx+28);
    if(offset==0){
      return -1;
    }
    if(i>=_info.getInt(4*offset)){
      return -1;
    }
    return _info.getInt(4*offset+4+8*i);
  }

  public String catName(int catidx)
  {
    if(!_ok || catidx<0 || catidx>=h_catindex_size()){
      return null;
    }
    int catoffset = _catindex.getInt(4*catidx);
    return getString(catoffset);
  }

  //// test ////
  public static void main(String[] args) {
    String domain = "/home/gv/fsa/automata/us_main_20041002_20041008";

    ConceptNet cn = new ConceptNet(domain);

    System.out.println("Loading ConceptNet domain "+domain+": "+cn.isOk());
    int idx = cn.lookup("new york");
    System.out.println("  lookup(\"new york\") -> "+idx);
    System.out.println("  lookup("+idx+")     -> "+cn.lookup(idx)+"("+cn.score(idx)+","+cn.strength(idx)+")");
    System.out.println("    extensions("+cn.numExt(idx)+"):");
    for(int i=0;i<5 && i<cn.numExt(idx);i++){
      System.out.println("      "+cn.lookup(cn.ext(idx,i))+","+cn.extFrq(idx,i));
    }
    if(5<cn.numExt(idx)){
      System.out.println("      ...");
    }
    System.out.println("    associations("+cn.numAssoc(idx)+"):");
    for(int i=0;i<5 && i<cn.numAssoc(idx);i++){
      System.out.println("      "+cn.lookup(cn.assoc(idx,i))+","+cn.assocFrq(idx,i));
    }
    if(5<cn.numAssoc(idx)){
      System.out.println("      ...");
    }
    System.out.println("    categories("+cn.numCat(idx)+"):");
    for(int i=0;i<5 && i<cn.numCat(idx);i++){
      System.out.println("      "+cn.catName(cn.cat(idx,i)));
    }
    if(5<cn.numCat(idx)){
      System.out.println("      ...");
    }
  }



}
