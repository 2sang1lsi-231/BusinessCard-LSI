package com.lsi.businesscard;
/** Stability of the detected card itself, with bounded jitter and brief missed frames. */
public final class ScanStability {
 static final double MIN_SHARPNESS=4;
 private float[] reference,smoothed;private long started,lastSeen;private int count,missing;
 public void reset(){reference=smoothed=null;started=lastSeen=0;count=missing=0;}
 public float[] corners(){return smoothed;}
 public boolean update(CardEdges.Detection result,long now){
  if(result.corners==null){if(++missing>2||now-lastSeen>650)reset();return false;}
  float[] aligned=align(reference,result.corners);missing=0;lastSeen=now;
  if(reference==null||CardEdges.movement(reference,aligned)>.035f){reference=aligned.clone();smoothed=aligned.clone();started=now;count=1;return false;}
  for(int i=0;i<8;i++)smoothed[i]=smoothed[i]*.65f+aligned[i]*.35f;
  if(result.sharpness<MIN_SHARPNESS){started=now;count=0;return false;}
  return ++count>=5&&now-started>=1100;
 }
 static float[] align(float[] reference,float[] next){if(reference==null)return next.clone();float[] best=next.clone();float score=Float.MAX_VALUE;for(int shift=0;shift<4;shift++){float[] rotated=new float[8];for(int i=0;i<4;i++){rotated[2*i]=next[2*((i+shift)%4)];rotated[2*i+1]=next[2*((i+shift)%4)+1];}float d=CardEdges.movement(reference,rotated);if(d<score){score=d;best=rotated;}}return best;}
 public ScanStability(){}
}
