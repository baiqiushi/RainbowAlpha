package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query {
    public String clusterKey; // key of the cluster
    public String order; // if the given cluster does not exist, order will be used to build the cluster, the same as order definition in Command
    public int zoom;
    public double[] bbox; //x0, y0, x1, y1
    public String algorithm; // "SuperCluster"(default) / "SuperClusterInBatch" / "iSuperCluster" /
    // "AiSuperCluster" / "BiSuperCluster" / "LBiSuperCluster" / "QuadTreeAggregator" /
    // "DataExplorer" / "DataAggregator" / "SBiSuperCluster"
    public String indexType; // "KDTree"(default) / "GridIndex"
    public boolean treeCut;  // true / false
    public String measure; // for treeCut: "avg" / "min" / "max" distance of children
    public double pixels; // for treeCut: number of pixels for differentiable distance in tree-cut algorithm
    public boolean bipartite; // for treeCut: true / false
    public int resX; // frontend resolution x
    public int resY; // frontend resolution y
    public String aggregator; // for DataAggregator and QuadTreeAggregators: "gl-pixel" / "leaflet" / "deck-gl"
}
