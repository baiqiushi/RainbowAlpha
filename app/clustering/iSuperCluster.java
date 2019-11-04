package clustering;

import model.Cluster;
import model.Advocator;
import util.KDTree;
import util.MyTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class iSuperCluster extends SuperCluster {

    // switch of keeping data points or not,
    // turn it on when we need to return the labels for all data points
    public static final boolean keepPoints = false;

    List<Cluster> maxZoomClusters;
    KDTree<Advocator> advocatorsTree;
    int pointIdSeq;
    int advocatorSeq;

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing = new HashMap<>();
    //-Timing-//

    public iSuperCluster() {
        this.trees = new KDTree[maxZoom + 1];
        this.clusters = new Cluster[maxZoom + 1][];
        this.maxZoomClusters = new ArrayList<>();
        this.advocatorsTree = new KDTree<>(K);
        this.pointIdSeq = 0;
        this.advocatorSeq = 0;
    }

    public iSuperCluster(int _minZoom, int _maxZoom) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.trees = new KDTree[maxZoom + 1];
        this.clusters = new Cluster[maxZoom + 1][];
        this.maxZoomClusters = new ArrayList<>();
        this.advocatorsTree = new KDTree<>(K);
        this.pointIdSeq = 0;
        this.advocatorSeq = 0;
    }

    public void load(double[][] points) {
        System.out.println("incremental SuperCluster loading " + points.length + " points ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.length;

        if (keepTiming) MyTimer.startTimer();
        // merge all points into the maxZoom level clusters
        for (int i = 0; i < points.length; i ++) {
            mergePoint(createPointCluster(points[i], this.pointIdSeq ++));
        }
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) {
            if (timing.containsKey("mergePoint")) {
                timing.put("mergePoint", timing.get("mergePoint") + MyTimer.durationSeconds());
            } else {
                timing.put("mergePoint", MyTimer.durationSeconds());
            }
        }

        if (keepTiming) MyTimer.startTimer();
        // then re-cluster all the levels above maxZoom level
        Cluster[] clusters = this.maxZoomClusters.toArray(new Cluster[this.maxZoomClusters.size()]);
        for (int i = 0; i < clusters.length; i ++) {
            clusters[i].zoom = Integer.MAX_VALUE;
        }
        this.trees[maxZoom] = new KDTree<>(K);
        this.trees[maxZoom].load(clusters);
        this.clusters[maxZoom] = clusters;

        // cluster the clusters on maxZoom level to form maxZoom-1 level, etc.;
        for (int z = maxZoom - 1; z >= minZoom; z --) {
            // create a new set of clusters for the zoom and index them with a KD-tree
            clusters = this._clusters(clusters, z);

            this.trees[z] = new KDTree<>(K);
            this.trees[z].load(clusters);
            this.clusters[z] = clusters;
        }
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) {
            if (timing.containsKey("recluster")) {
                timing.put("recluster", timing.get("recluster") + MyTimer.durationSeconds());
            } else {
                timing.put("recluster", MyTimer.durationSeconds());
            }
        }

        long end = System.nanoTime();
        System.out.println("incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.maxZoomClusters.size());
        if (keepTiming) printTiming();
    }

    private void mergePoint(Cluster c) {
        if (keepTiming) MyTimer.startTimer();
        double radius = getRadius(maxZoom);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) {
            if (timing.containsKey("mergePoint.getRadius")) {
                timing.put("mergePoint.getRadius", timing.get("mergePoint.getRadius") + MyTimer.durationSeconds());
            } else {
                timing.put("mergePoint.getRadius", MyTimer.durationSeconds());
            }
        }
        if (keepTiming) MyTimer.startTimer();
        // Find all earlier advocators c can merge into
        List<Advocator> advocators = advocatorsTree.within(c, radius);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) {
            if (timing.containsKey("mergePoint.rangeSearch")) {
                timing.put("mergePoint.rangeSearch", timing.get("mergePoint.rangeSearch") + MyTimer.durationSeconds());
            } else {
                timing.put("mergePoint.rangeSearch", MyTimer.durationSeconds());
            }
        }

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {
            if (keepTiming) MyTimer.startTimer();
            Advocator newAdvocator = new Advocator(K);
            newAdvocator.seq = advocatorSeq ++;
            newAdvocator.cluster = c;
            newAdvocator.setDimensionValue(0, c.getDimensionValue(0));
            newAdvocator.setDimensionValue(1, c.getDimensionValue(1));
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) {
                if (timing.containsKey("mergePoint.createAdvocator")) {
                    timing.put("mergePoint.createAdvocator", timing.get("mergePoint.createAdvocator") + MyTimer.durationSeconds());
                } else {
                    timing.put("mergePoint.createAdvocator", MyTimer.durationSeconds());
                }
            }
            if (keepTiming) MyTimer.startTimer();
            advocatorsTree.insert(newAdvocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) {
                if (timing.containsKey("mergePoint.insertTree")) {
                    timing.put("mergePoint.insertTree", timing.get("mergePoint.insertTree") + MyTimer.durationSeconds());
                } else {
                    timing.put("mergePoint.insertTree", MyTimer.durationSeconds());
                }
            }
            c.numPoints = 0;
            this.maxZoomClusters.add(c);
        }
        // if earlier advocators' groups could be merged into
        else {
            if (keepTiming) MyTimer.startTimer();
            // find the earliest advocator
            Advocator earliestAdvocator = null;
            for (Advocator advocator: advocators) {
                if (earliestAdvocator == null) {
                    earliestAdvocator = advocator;
                }
                else if (earliestAdvocator.seq > advocator.seq) {
                    earliestAdvocator = advocator;
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) {
                if (timing.containsKey("mergePoint.findEarliest")) {
                    timing.put("mergePoint.findEarliest", timing.get("mergePoint.findEarliest") + MyTimer.durationSeconds());
                } else {
                    timing.put("mergePoint.findEarliest", MyTimer.durationSeconds());
                }
            }

            if (keepTiming) MyTimer.startTimer();
            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;
            // the advocator's attached point first time becomes a cluster,
            // create a parent cluster instance attached to advocator,
            // and add both this advocator cluster and this new point to parent's children
            if (cluster.numPoints == 0) {
                // only if keepPoints switch turned on, store children as the raw data points
                if (keepPoints) {
                    // keep previous single point cluster as a new child point
                    Cluster point = cluster.clone();
                    double wx = (cluster.getDimensionValue(0) + c.getDimensionValue(0)) / 2.0;
                    double wy = (cluster.getDimensionValue(1) + c.getDimensionValue(1)) / 2.0;
                    cluster.setDimensionValue(0, wx);
                    cluster.setDimensionValue(1, wy);
                    cluster.numPoints = 2;
                    cluster.id = (cluster.id << 5) + (maxZoom + 1);
                    cluster.children.add(point);
                    cluster.children.add(c);
                    point.parentId = cluster.id;
                    c.parentId = cluster.id;
                }
                // just merge this point into cluster
                else {
                    double wx = (cluster.getDimensionValue(0) + c.getDimensionValue(0)) / 2.0;
                    double wy = (cluster.getDimensionValue(1) + c.getDimensionValue(1)) / 2.0;
                    cluster.setDimensionValue(0, wx);
                    cluster.setDimensionValue(1, wy);
                    cluster.numPoints = 2;
                    cluster.id = (cluster.id << 5) + (maxZoom + 1);
                }
            }
            // this advocator's attached point is already a cluster,
            // calculate the new weighted x and y
            else {
                double wx = cluster.getDimensionValue(0) * cluster.numPoints;
                double wy = cluster.getDimensionValue(1) * cluster.numPoints;
                wx += c.getDimensionValue(0);
                wy += c.getDimensionValue(1);
                cluster.numPoints = cluster.numPoints + 1;
                cluster.setDimensionValue(0, wx / cluster.numPoints);
                cluster.setDimensionValue(1, wy / cluster.numPoints);
                // only if keepPoints switch turned on, store children as the raw data points
                if (keepPoints) {
                    cluster.children.add(c);
                    c.expansionZoom = maxZoom + 1;
                    c.parentId = cluster.id;
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) {
                if (timing.containsKey("mergePoint.mergeCluster")) {
                    timing.put("mergePoint.mergeCluster", timing.get("mergePoint.mergeCluster") + MyTimer.durationSeconds());
                } else {
                    timing.put("mergePoint.mergeCluster", MyTimer.durationSeconds());
                }
            }
        }
    }

    public void printTiming() {
        System.out.println("Timing distribution:");
        System.out.println("[recluster] " + timing.get("recluster") + " seconds");
        System.out.println("[mergePoint] " + timing.get("mergePoint") + " seconds");
        System.out.println("    [get radius] " + timing.get("mergePoint.getRadius") + " seconds");
        System.out.println("    [range search] " + timing.get("mergePoint.rangeSearch") + " seconds");
        System.out.println("    [create advocator] " + timing.get("mergePoint.createAdvocator") + " seconds");
        System.out.println("    [insert tree] " + timing.get("mergePoint.insertTree") + " seconds");
        System.out.println("    [find earliest] " + timing.get("mergePoint.findEarliest") + " seconds");
        System.out.println("    [merge cluster] " + timing.get("mergePoint.mergeCluster") + " seconds");
    }
}