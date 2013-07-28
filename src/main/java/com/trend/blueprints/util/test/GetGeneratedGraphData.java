/**
 * 
 */
package com.trend.blueprints.util.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.time.StopWatch;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.trend.blueprints.Graph;
import com.trend.blueprints.HBaseGraphConstants;
import com.trend.blueprints.HBaseGraphFactory;

/**
 * Get <code>Graph</code> data generated by <code>GeneratedTestData</code>.
 * 
 * Also print-out elapsed time for performance verification.
 * @author scott_miao
 *
 */
public class GetGeneratedGraphData extends Configured implements Tool {
  
  private static final Logger LOG = LoggerFactory.getLogger(GetGeneratedGraphData.class);
  
  private static final int DEFAULT_LEVEL_TO_TRAVERSE = 3;
  
  private int levelToTraverse = DEFAULT_LEVEL_TO_TRAVERSE;
  private String vertexTableName = null;
  private String edgeTableName = null;
  private String startVertexId = null;
  
  
  @Override
  public int run(String[] args) throws Exception {
    int countIndex = -1;
    // Process command line args
    for (int i = 0; i < args.length; i++) {
      String cmd = args[i];

      if (cmd.startsWith("-")) {
        if (countIndex >= 0) {
          // command line args must be in the form: [opts] [count1
          // [count2 ...]]
          System.err.println("Invalid command line options");
          printUsageAndExit();
        }

        if (cmd.equals("--help") || cmd.equals("-h")) {
          // user asked for help, print the help and quit.
          printUsageAndExit();
        } else if (cmd.equals("-l")) {
          i++;
          
          if (i == args.length) {
            System.err
            .println("-l needs a numeric value argument.");
            printUsageAndExit();
          }
          
          try{
            this.levelToTraverse = Integer.parseInt(args[i]);
          } catch(NumberFormatException e) {
            System.err.println("-l needs a numeric value argument.");
            printUsageAndExit();
          }
        } else if(cmd.equals("-i")) {
          i++;
          
          if (i == args.length) {
            System.err
            .println("-i needs a String value argument.");
            printUsageAndExit();
          }
          
          this.startVertexId = args[i];
        }
        
      } else if (countIndex < 0) {
        // keep track of first count specified by the user
        countIndex = i;
      }
    }
    
    if(args.length != countIndex + 2) {
      System.err.println("Missing vertex-table-name or edge-table-name");
      printUsageAndExit();
    }
    
    // set {vertex,edge}-table-name
    Configuration conf = this.getConf();
    this.vertexTableName = args[countIndex];
    conf.set(HBaseGraphConstants.HBASE_GRAPH_TABLE_VERTEX_NAME_KEY, this.vertexTableName);
    
    this.edgeTableName = args[countIndex + 1];
    conf.set(HBaseGraphConstants.HBASE_GRAPH_TABLE_EDGE_NAME_KEY, this.edgeTableName);
    
    // if user not specify start-vertex-id, random choose one
    if(null == this.startVertexId || this.startVertexId.isEmpty()) {
      this.startVertexId = this.getSampleDataRowKey();
    }
    
    LOG.info("-l:" + this.levelToTraverse + ", -i:" + this.startVertexId + 
        ", vertex-table-name:" + this.vertexTableName + ", edge-table-name:" + this.edgeTableName);
    
    this.doGetGeneratedGraphData(this.startVertexId);
    return 0;
  }
  
  private String getSampleDataRowKey() throws IOException {
    String rowKey = null;
    HTable table = null;
    ResultScanner rs = null;
    try {
      table = new HTable(this.getConf(), this.vertexTableName);
    
      Scan scan = new Scan();
      scan.setFilter(new FirstKeyOnlyFilter());
      rs = table.getScanner(scan);
      Result r = rs.next();
      if(null == r) throw new IllegalStateException("No sample data row key found !!");
      rowKey = Bytes.toString(r.getRow());
    } catch (IOException e) {
      LOG.error("getSampleDataRowKey failed", e);
      throw e;
    } finally {
      if(null != rs) rs.close();
      if(null != table) table.close();
    }
    return rowKey;
  }
  
  private void doGetGeneratedGraphData(String rowKey) {
    LOG.info("Sample Graph Vertex:" + rowKey);
    StopWatch timer = new StopWatch();
    Graph graph = null;
    Vertex vertex = null;
    Iterable<Vertex> vertices = null;
    Iterable<Edge> edges = null;
    int level = 0;
    long totalVertexCount = 0;
    
    try {
      timer.start();
      graph = HBaseGraphFactory.open(this.getConf());
      while(level < this.levelToTraverse) {
        level++;
        LOG.info("***HEAD:level:" + level + " output***");
        if(level == 1) {
          vertex = graph.getVertex(rowKey);
          vertices = Arrays.asList(vertex);
          LOG.info("vertices count:" + 1);
          totalVertexCount = 1;
        } else {
          List<Vertex> subVertices = new ArrayList<Vertex>();
          for(Iterator<Vertex> verticesIt = vertices.iterator(); 
              verticesIt.hasNext();) {
            vertex = verticesIt.next();
            edges = ((com.trend.blueprints.Vertex)vertex).getEdges();
            for(Edge edge : edges) {
              subVertices.add(edge.getVertex(Direction.OUT));
            }
          }
          LOG.info("vertices count:" + subVertices.size());
          totalVertexCount += subVertices.size();
          vertices = subVertices;
        }
      
        LOG.info(vertices.toString());
        LOG.info("***TAIL:level:" + level + " output***");
      }
      timer.stop();
      LOG.info("Time elapsed:" + timer.toString() + " for getting " + 
          totalVertexCount + " of vertices");
    
    } finally {
      graph.shutdown();
    }
  }
  
  private static void printUsageAndExit() {
    System.out.print(GetGeneratedGraphData.class.getSimpleName() + " Usage:");
    System.out.println(" [-l level-to-traverse] [-i start-vertex-id] <vertex-table-name> <edge-table-name>");
    System.out.println("-l level-to-traverse default value:" + DEFAULT_LEVEL_TO_TRAVERSE);
    System.out.println("-i start-vertex-id user can specify what vertex-id to start");
    System.exit(1);
  }
  
  
  public static int main(String args[]) {
    
    return 0;
  }
  
}
