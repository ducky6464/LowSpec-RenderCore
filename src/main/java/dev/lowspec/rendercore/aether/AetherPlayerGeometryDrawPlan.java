package dev.lowspec.rendercore.aether;
import java.util.Arrays;
/** Precomputed per-player vertex ranges grouped by skin for a future GPU draw pass. */
public final class AetherPlayerGeometryDrawPlan {
 public static final int FLOATS_PER_VERTEX=AetherModelGeometryBuffer.FLOATS_PER_VERTEX;
 private final int capacity; private final int[] batchSkinGroups,batchStarts,batchSizes,packetPlayers,firstVertices,vertexCounts;
 private int batchCount,packetCount,missingGeometryCount,rejectedRangeCount;
 public AetherPlayerGeometryDrawPlan(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity must be positive");this.capacity=capacity;batchSkinGroups=new int[capacity];batchStarts=new int[capacity];batchSizes=new int[capacity];packetPlayers=new int[capacity];firstVertices=new int[capacity];vertexCounts=new int[capacity];}
 public void build(AetherPlayerBatchPlan source,int[] offsets,int[] floatCounts,int[] perPlayerVertexCounts,int playerCount){
  if(source==null||offsets==null||floatCounts==null||perPlayerVertexCounts==null)throw new NullPointerException("draw-plan input");
  if(playerCount<0||playerCount>capacity||playerCount>offsets.length||playerCount>floatCounts.length||playerCount>perPlayerVertexCounts.length||source.playerCount()!=playerCount)throw new IllegalArgumentException("player count does not match draw-plan capacity and source");
  clear();
  for(int batch=0;batch<source.batchCount();batch++){int start=packetCount,skinGroup=source.batchSkinGroup(batch);
   for(int offset=0;offset<source.batchSize(batch);offset++){int player=source.playerIndex(batch,offset),arenaOffset=offsets[player],floats=floatCounts[player],vertices=perPlayerVertexCounts[player];
    if(arenaOffset<0||floats==0||vertices==0){missingGeometryCount++;continue;}
    if(arenaOffset%FLOATS_PER_VERTEX!=0||floats%FLOATS_PER_VERTEX!=0||vertices!=floats/FLOATS_PER_VERTEX||packetCount>=capacity){rejectedRangeCount++;continue;}
    packetPlayers[packetCount]=player;firstVertices[packetCount]=arenaOffset/FLOATS_PER_VERTEX;vertexCounts[packetCount]=vertices;packetCount++;
   }
   if(packetCount>start){batchSkinGroups[batchCount]=skinGroup;batchStarts[batchCount]=start;batchSizes[batchCount]=packetCount-start;batchCount++;}
  }
 }
 public void clear(){Arrays.fill(batchSizes,0);batchCount=packetCount=missingGeometryCount=rejectedRangeCount=0;}
 public int batchCount(){return batchCount;} public int packetCount(){return packetCount;} public int missingGeometryCount(){return missingGeometryCount;} public int rejectedRangeCount(){return rejectedRangeCount;}
 public int batchSkinGroup(int batch){checkBatch(batch);return batchSkinGroups[batch];} public int batchSize(int batch){checkBatch(batch);return batchSizes[batch];}
 public int packetPlayer(int batch,int offset){return packetIndex(batch,offset,packetPlayers);} public int firstVertex(int batch,int offset){return packetIndex(batch,offset,firstVertices);} public int vertexCount(int batch,int offset){return packetIndex(batch,offset,vertexCounts);}
 private int packetIndex(int batch,int offset,int[] values){checkBatch(batch);if(offset<0||offset>=batchSizes[batch])throw new IndexOutOfBoundsException("draw packet "+offset+" of "+batchSizes[batch]);return values[batchStarts[batch]+offset];}
 private void checkBatch(int batch){if(batch<0||batch>=batchCount)throw new IndexOutOfBoundsException("draw batch "+batch+" of "+batchCount);}
}
