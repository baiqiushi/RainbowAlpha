package algorithms;

import model.Cluster;
import model.Advocator;
import model.PointTuple;
import util.KDTree;
import util.MyMemory;
import util.MyTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class iSuperCluster extends SuperCluster {

    // switch of keeping data clusters or not,
    // turn it on when we need to return the labels for all data clusters
    public boolean keepPoints = false;

    List<Cluster> maxZoomClusters;
    KDTree<Advocator> advocatorsTree;
    int pointIdSeq;
    int advocatorSeq;

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing = new HashMap<>();
    //-Timing-//

    public iSuperCluster(int _minZoom, int _maxZoom, boolean _analysis) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.indexes = new KDTree[maxZoom + 1];
        this.clusters = new Cluster[maxZoom + 1][];
        this.maxZoomClusters = new ArrayList<>();
        this.advocatorsTree = new KDTree<>();
        this.pointIdSeq = 0;
        this.advocatorSeq = 0;
        this.keepPoints = _analysis;

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
            timing.put("recluster", 0.0);
            timing.put("mergePoint", 0.0);
            timing.put("mergePoint.rangeSearch", 0.0);
            timing.put("mergePoint.insertTree", 0.0);
            timing.put("mergePoint.findEarliest", 0.0);
            timing.put("mergePoint.mergeCalculation", 0.0);
        }
    }

    private void mergePointClusters(double[][] points) {
        for (int i = 0; i < points.length; i ++) {
            mergePoint(createPointCluster(points[i][0], points[i][1], this.pointIdSeq ++));
        }
    }

    private void mergePointClusters(List<PointTuple> points) {
        for (int i = 0; i < points.size(); i ++) {
            mergePoint(createPointCluster(points.get(i).getX(), points.get(i).getY(), this.pointIdSeq ++));
        }
    }

    private void recluster() {
        // then re-cluster all the levels above maxZoom level
        Cluster[] clusters = this.maxZoomClusters.toArray(new Cluster[this.maxZoomClusters.size()]);
        for (int i = 0; i < clusters.length; i ++) {
            clusters[i].zoom = Integer.MAX_VALUE;
        }
        this.indexes[maxZoom] = new KDTree<>();
        this.indexes[maxZoom].load(clusters);
        this.clusters[maxZoom] = clusters;

        // cluster the clusters on maxZoom level to form maxZoom-1 level, etc.;
        for (int z = maxZoom - 1; z >= minZoom; z --) {
            // create a new set of clusters for the zoom and index them with a KD-tree
            clusters = this._clusters(clusters, z);

            this.indexes[z] = new KDTree<>();
            this.indexes[z].load(clusters);
            this.clusters[z] = clusters;
        }

    }

    public void load(double[][] points) {
        System.out.println("incremental SuperCluster loading " + points.length + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.length;

        if (keepTiming) MyTimer.startTimer();
        // merge all clusters into the maxZoom level clusters
        mergePointClusters(points);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("mergePoint", timing.get("mergePoint") + MyTimer.durationSeconds());

        if (keepTiming) MyTimer.startTimer();
        recluster();
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("recluster", timing.get("recluster") + MyTimer.durationSeconds());

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.maxZoomClusters.size());
        if (keepTiming) printTiming();

        MyMemory.printMemory();
    }

    public void load(List<PointTuple> points) {
        System.out.println("incremental SuperCluster loading " + points.size() + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.size();

        if (keepTiming) MyTimer.startTimer();
        // merge all clusters into the maxZoom level clusters
        mergePointClusters(points);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("mergePoint", timing.get("mergePoint") + MyTimer.durationSeconds());

        if (keepTiming) MyTimer.startTimer();
        recluster();
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("recluster", timing.get("recluster") + MyTimer.durationSeconds());

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.maxZoomClusters.size());
        if (keepTiming) printTiming();

        MyMemory.printMemory();
    }

    private void mergePoint(Cluster c) {
        double radius = getRadius(maxZoom);

        if (keepTiming) MyTimer.startTimer();
        // Find all earlier advocators c can merge into
        List<Advocator> advocators = advocatorsTree.within(c, radius);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("mergePoint.rangeSearch", timing.get("mergePoint.rangeSearch") + MyTimer.durationSeconds());

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {
            Advocator newAdvocator = new Advocator(c.getX(), c.getY(), advocatorSeq ++);
            newAdvocator.cluster = c;

            if (keepTiming) MyTimer.startTimer();
            advocatorsTree.insert(newAdvocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("mergePoint.insertTree", timing.get("mergePoint.insertTree") + MyTimer.durationSeconds());

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
                else if (earliestAdvocator.getId() > advocator.getId()) {
                    earliestAdvocator = advocator;
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("mergePoint.findEarliest", timing.get("mergePoint.findEarliest") + MyTimer.durationSeconds());

            if (keepTiming) MyTimer.startTimer();
            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;
            // the advocator's attached point first time becomes a cluster,
            // create a parent cluster instance attached to advocator,
            // and add both this advocator cluster and this new point to parent's children
            if (cluster.numPoints == 0) {
                // only if keepPoints switch turned on, store children as the raw data clusters
                if (keepPoints) {
                    // keep previous single point cluster as a new child point
                    Cluster point = cluster.clone();
                    double wx = (cluster.getX() + c.getX()) / 2.0;
                    double wy = (cluster.getY() + c.getY()) / 2.0;
                    cluster.setX(wx);
                    cluster.setY(wy);
                    cluster.numPoints = 2;
                    cluster.setId((cluster.getId() << 5) + (maxZoom + 1));
                    cluster.children.add(point);
                    cluster.children.add(c);
                    point.parentId = cluster.getId();
                    c.parentId = cluster.getId();
                }
                // just merge this point into cluster
                else {
                    double wx = (cluster.getX() + c.getX()) / 2.0;
                    double wy = (cluster.getY() + c.getY()) / 2.0;
                    cluster.setX(wx);
                    cluster.setY(wy);
                    cluster.numPoints = 2;
                    cluster.setId((cluster.getId() << 5) + (maxZoom + 1));
                }
            }
            // this advocator's attached point is already a cluster,
            // calculate the new weighted x and y
            else {
                double wx = cluster.getX() * cluster.numPoints;
                double wy = cluster.getY() * cluster.numPoints;
                wx += c.getX();
                wy += c.getY();
                cluster.numPoints = cluster.numPoints + 1;
                cluster.setX( wx / cluster.numPoints);
                cluster.setY(wy / cluster.numPoints);
                // only if keepPoints switch turned on, store children as the raw data clusters
                if (keepPoints) {
                    cluster.children.add(c);
                    c.expansionZoom = maxZoom + 1;
                    c.parentId = cluster.getId();
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("mergePoint.mergeCalculation", timing.get("mergePoint.mergeCalculation") + MyTimer.durationSeconds());
        }
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
        System.out.println("    [recluster] " + timing.get("recluster") + " seconds");
        System.out.println("    [mergePoint] " + timing.get("mergePoint") + " seconds");
        System.out.println("        [range search] " + timing.get("mergePoint.rangeSearch") + " seconds");
        System.out.println("        [insert tree] " + timing.get("mergePoint.insertTree") + " seconds");
        System.out.println("        [find earliest] " + timing.get("mergePoint.findEarliest") + " seconds");
        System.out.println("        [merge calculation] " + timing.get("mergePoint.mergeCalculation") + " seconds");
    }
}
