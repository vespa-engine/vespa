// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.segmenter;

import java.util.LinkedList;

/**
 * Contains the segmentation() method.
 *
 * @author  Peter Boros
 */
public class Segments extends LinkedList<Segment> {

    public final static int SEGMENTATION_WEIGHTED            =  0;
    public final static int SEGMENTATION_WEIGHTED_BIAS10     =  1;
    public final static int SEGMENTATION_WEIGHTED_BIAS20     =  2;
    public final static int SEGMENTATION_WEIGHTED_BIAS50     =  3;
    public final static int SEGMENTATION_WEIGHTED_BIAS100    =  4;
    public final static int SEGMENTATION_WEIGHTED_LEFTMOST   =  5;
    public final static int SEGMENTATION_WEIGHTED_RIGHTMOST  =  6;
    public final static int SEGMENTATION_WEIGHTED_LONGEST    =  7;
    public final static int SEGMENTATION_LEFTMOST_LONGEST    =  8;
    public final static int SEGMENTATION_LEFTMOST_WEIGHTED   =  9;
    public final static int SEGMENTATION_RIGHTMOST_LONGEST   = 10;
    public final static int SEGMENTATION_RIGHTMOST_WEIGHTED  = 11;
    public final static int SEGMENTATION_LONGEST_WEIGHTED    = 12;
    public final static int SEGMENTATION_LONGEST_LEFTMOST    = 13;
    public final static int SEGMENTATION_LONGEST_RIGHTMOST   = 14;
    public final static int SEGMENTATION_METHODS             = 15;

    private String[] _tokens;
    private int      _size;
    private int[][]  _map;

    public Segments(String[] tokens)
    {
        _tokens = tokens;
        _size = tokens.length;
        _map = new int[_size+1][_size+1];
        for(int i=0; i<=_size; i++){
            for(int j=0; j<=_size; j++){
                _map[i][j]=-1;
            }
        }
    }

    @Override
    public boolean add(Segment s)
    {
        var result = super.add(s);
        _map[s.beg()][s.end()]=super.size()-1;
        return result;
    }

    private void addMissingSingles()
    {
        for(int i=0; i<_size; i++){
            if(_map[i][i+1]==-1){
                super.add(new Segment(i,i+1,0));
                _map[i][i+1]=super.size()-1;
            }
        }
    }

    private void reMap()
    {
        for(int i=0; i<=_size; i++){
            for(int j=0; j<=_size; j++){
                _map[i][j]=-1;
            }
        }
        for(int i=0; i<super.size(); i++){
            _map[beg(i)][end(i)] = i;
        }
    }

    public String sgm(int idx)
    {
        if(idx<0 || idx>=super.size()){
            return null;
        }
        String s = new String(_tokens[super.get(idx).beg()]);
        for(int i = super.get(idx).beg() + 1; i < super.get(idx).end(); i++){
            s += " " + _tokens[i];
        }
        return s;
    }

    public int beg(int idx)
    {
        if(idx<0 || idx>=super.size()){
            return -1;
        }
        return super.get(idx).beg();
    }

    public int end(int idx)
    {
        if(idx<0 || idx>=super.size()){
            return -1;
        }
        return super.get(idx).end();
    }

    public int len(int idx)
    {
        if(idx<0 || idx>=super.size()){
            return -1;
        }
        return super.get(idx).len();
    }

    public int conn(int idx)
    {
        if(idx<0 || idx>=super.size()){
            return -1;
        }
        return super.get(idx).conn();
    }

    @SuppressWarnings("fallthrough")
    public Segments segmentation(int method)
    {
        Segments smnt = new Segments(_tokens);

        addMissingSingles();

        int maxsc, id, bestid=-1, bias=0, c, pos, bestval, temp=0, next=-1;
        int[] maxScore = new int[super.size()];
        int[] nextid   = new int[super.size()];
        for(int i=0;i<nextid.length;i++){
            nextid[i]=-1;
        }

        switch(method){
        case SEGMENTATION_WEIGHTED_BIAS100:
            bias+=50;
        case SEGMENTATION_WEIGHTED_BIAS50:
            bias+=30;
        case SEGMENTATION_WEIGHTED_BIAS20:
            bias+=10;
        case SEGMENTATION_WEIGHTED_BIAS10:
            bias+=10;
        case SEGMENTATION_WEIGHTED:
            bestid=-1;
            for(int i=_tokens.length;i>=0;i--){
                bestid=-1;maxsc=0;
                for(int j=i+1;j<=_tokens.length;j++){
                    id=_map[i][j];
                    if(id>=0 && maxScore[id]+1>maxsc) {
                        bestid=id;
                        maxsc=maxScore[id]+1;
                    }
                }
                if(maxsc>0){
                    maxsc--;
                }
                for(int j=0;j<i;j++){
                    id=_map[j][i];
                    if(id>=0){
                        nextid[id] = bestid;
                        c = conn(id);
                        if(i-j<=1){
                            maxScore[id] = maxsc;
                        }
                        else if(bias>0){
                            maxScore[id] = maxsc + ((100+(i-j-2)*bias)*c)/100;
                        }
                        else{
                            maxScore[id] = maxsc + c;
                        }
                    }
                }
            }
            id = bestid;
            while(id!=-1){
                smnt.add(super.get(id));
                id=nextid[id];
            }
            break;
        case SEGMENTATION_LEFTMOST_LONGEST:
        case SEGMENTATION_LEFTMOST_WEIGHTED:
            pos = 0;
            while(pos<_tokens.length){
                bestid = -1; bestval = -1;
                for(int i=pos+1;i<=_tokens.length;i++){
                    id = _map[pos][i];
                    if(id>=0 &&
                       (method==SEGMENTATION_LEFTMOST_LONGEST ||
                        (temp=(len(id)>1)? conn(id) :0)>bestval) ){
                        bestid = id;
                        bestval = temp;
                        next = i;
                    }
                }
                smnt.add(super.get(bestid));
                pos=next;
            }
            break;
        case SEGMENTATION_RIGHTMOST_LONGEST:
        case SEGMENTATION_RIGHTMOST_WEIGHTED:
            pos = _tokens.length;
            while(pos>0){
                bestid = -1; bestval = -1;
                for(int i=pos-1;i>=0;i--){
                    id = _map[i][pos];
                    if(id>=0 &&
                       (method==SEGMENTATION_RIGHTMOST_LONGEST ||
                        (temp=(len(id)>1)? conn(id) :0)>bestval) ){
                        bestid = id;
                        bestval = temp;
                        next = i;
                    }
                }
                smnt.addFirst(super.get(bestid));
                pos=next;
            }
            smnt.reMap();
            break;
        case SEGMENTATION_LONGEST_WEIGHTED:
        case SEGMENTATION_LONGEST_LEFTMOST:
        case SEGMENTATION_LONGEST_RIGHTMOST:
        case SEGMENTATION_WEIGHTED_LONGEST:
        case SEGMENTATION_WEIGHTED_LEFTMOST:
        case SEGMENTATION_WEIGHTED_RIGHTMOST:
            buildSegmentationRecursive(method,smnt,0,_tokens.length);
            break;
        }

        return smnt;
    }

    private void buildSegmentationRecursive(int method, Segments smnt, int b, int e)
    {
        int bestid, bestval1, bestval2, temp;

        bestid=-1;bestval1=-1;bestval2=-1;
        for(int i=0;i<super.size();i++){
            if(b<=beg(i) && e>=end(i)){
                switch(method){
                case SEGMENTATION_LONGEST_WEIGHTED:
                    if(len(i)>bestval1 ||
                       (len(i)==bestval1 && conn(i)>bestval2) ){
                        bestid=i;
                        bestval1=len(i);
                        bestval2=conn(i);
                    }
                    break;
                case SEGMENTATION_LONGEST_LEFTMOST:
                    if(len(i)>bestval1 ||
                       (len(i)==bestval1 && beg(i)<bestval2) ){
                        bestid=i;
                        bestval1=len(i);
                        bestval2=beg(i);
                    }
                    break;
                case SEGMENTATION_LONGEST_RIGHTMOST:
                    if(len(i)>bestval1 ||
                       (len(i)==bestval1 && end(i)>bestval2) ){
                        bestid=i;
                        bestval1=len(i);
                        bestval2=end(i);
                    }
                    break;
                case SEGMENTATION_WEIGHTED_LONGEST:
                    temp = (len(i)>1)?conn(i):0;
                    if(temp>bestval1 ||
                       (temp==bestval1 && len(i)>bestval2) ){
                        bestid=i;
                        bestval1=temp;
                        bestval2=len(i);
                    }
                    break;
                case SEGMENTATION_WEIGHTED_LEFTMOST:
                    temp = (len(i)>1)? conn(i) :0;
                    if(temp>bestval1 ||
                       (temp==bestval1 && beg(i)<bestval2) ){
                        bestid=i;
                        bestval1=temp;
                        bestval2=beg(i);
                    }
                    break;
                case SEGMENTATION_WEIGHTED_RIGHTMOST:
                    temp = len(i)>1?conn(i):0;
                    if(temp>bestval1 ||
                       (temp==bestval1 && end(i)>bestval2) ){
                        bestid=i;
                        bestval1=temp;
                        bestval2=end(i);
                    }
                    break;
                default: // dummy defult pick first possible
                    if(bestid<0){
                        bestid=i;
                    }
                    break;
                }
            }
        }
        if(bestid<0) {
            return; // this should never happen, as all one-word segments are created
        }

        if(b<beg(bestid)){
            buildSegmentationRecursive(method,smnt,b,beg(bestid));
        }

        // add segment
        smnt.add(super.get(bestid));

        // check right side
        if(e>end(bestid)){
            buildSegmentationRecursive(method,smnt,end(bestid),e);
        }
    }

}
