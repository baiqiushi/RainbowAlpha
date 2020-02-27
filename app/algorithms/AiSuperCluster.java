package algorithms;

import model.Advocator;
import model.Cluster;
import model.PointTuple;
import util.KDTree;
import util.MyLogger;
import util.MyMemory;
import util.MyTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiSuperCluster extends SuperCluster {

    //-Rand-index-//
    public boolean keepLabels = false;
    ArrayList<int[]> labels = new ArrayList<>();
    //-Rand-index-//

    KDTree<Advocator>[] advocatorsTrees;
    List<Cluster>[] advocatorClusters;
    int pointIdSeq;
    int advocatorSeq;

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing = new HashMap<>();
    //-Timing-//

    public AiSuperCluster(int _minZoom, int _maxZoom, boolean _analysis) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.advocatorsTrees = new KDTree[maxZoom + 1];
        this.advocatorClusters = new List[maxZoom + 1];
        this.pointIdSeq = 0;
        this.advocatorSeq = 0;
        this.keepLabels = _analysis;

        for (int i = 0; i <= maxZoom; i ++) {
            this.advocatorsTrees[i] = new KDTree<>();
            this.advocatorClusters[i] = new ArrayList<>();
        }

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
            timing.put("rangeSearch", 0.0);
            timing.put("insertTree", 0.0);
            timing.put("findEarliest", 0.0);
            timing.put("mergeCalculation", 0.0);
        }
    }

    public void load(List<PointTuple> points) {
        System.out.println("Approximate incremental SuperCluster loading " + points.size() + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.size();

        // insert clusters to max zoom level one by one
        for (int i = 0; i < points.size(); i ++) {
            if (keepLabels) {
                this.labels.add(new int[maxZoom + 1]);
            }
            insert(createPointCluster(points.get(i).getX(), points.get(i).getY(), this.pointIdSeq ++), maxZoom);
        }

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("Approximate incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.advocatorClusters[maxZoom].size());
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    public void load(double[][] points) {
        System.out.println("Approximate incremental SuperCluster loading " + points.length + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.length;

        // insert clusters to max zoom level one by one
        for (int i = 0; i < points.length; i ++) {
            if (keepLabels) {
                this.labels.add(new int[maxZoom + 1]);
            }
            insert(createPointCluster(points[i][0], points[i][1], this.pointIdSeq ++), maxZoom);
        }

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("Approximate incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.advocatorClusters[maxZoom].size());
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    private void insert(Cluster c, int zoom) {
        double radius = getRadius(zoom);

        if (keepTiming) MyTimer.startTimer();
        // Find all earlier advocators c can merge into
        KDTree<Advocator> advocatorsTree = this.advocatorsTrees[zoom];
        List<Advocator> advocators = advocatorsTree.within(c, radius);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("rangeSearch", timing.get("rangeSearch") + MyTimer.durationSeconds());

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {
            Advocator newAdvocator = new Advocator(c.getX(), c.getY(), advocatorSeq ++);
            newAdvocator.cluster = c;

            if (keepTiming) MyTimer.startTimer();
            advocatorsTree.insert(newAdvocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("insertTree", timing.get("insertTree") + MyTimer.durationSeconds());

            this.advocatorClusters[zoom].add(c);

            if (keepLabels) {
                this.labels.get(c.getId())[zoom] = c.getId();
            }

            // insert this cluster into lower level
            if (zoom >= 1) {
                Cluster parent = createCluster(c.getX(), c.getY(), c.getId(), 0);
                c.parentId = parent.getId();
                c.parent = parent;
                /** in AiSuperCluster Class, use Cluster.children to store direct descendants pointers */
                parent.children.add(c);
                insert(parent, zoom - 1);
            }
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
            if (keepTiming) timing.put("findEarliest", timing.get("findEarliest") + MyTimer.durationSeconds());

            if (keepTiming) MyTimer.startTimer();
            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;
            if (cluster.numPoints == 0) {
                cluster.numPoints = 1;
                cluster.setId((cluster.getId() << 5) + (maxZoom + 1));
            }
            double wx = cluster.getX() * cluster.numPoints;
            double wy = cluster.getY() * cluster.numPoints;
            wx += c.getX();
            wy += c.getY();
            cluster.numPoints = cluster.numPoints + 1;
            cluster.setX(wx / cluster.numPoints);
            cluster.setY(wy / cluster.numPoints);
            // merge c's children into cluster's children too
            if (zoom < maxZoom) {
                cluster.children.addAll(c.children);
                for (Cluster child: c.children) {
                    child.parentId = cluster.getId();
                    child.parent = cluster;
                }
            }

            // update all cluster's ascendants to merge this new c
            while (cluster.parent != null) {
                cluster = cluster.parent;
                merge(cluster, c);
            }

            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("mergeCalculation", timing.get("mergeCalculation") + MyTimer.durationSeconds());

            if (keepLabels) {
                this.labels.get(c.getId())[zoom] = cluster.getId();
            }
        }
    }

    private void merge(Cluster c1, Cluster c2) {
        if (c1.numPoints == 0) {
            c1.numPoints = 1;
            c1.setId((c1.getId() << 5) + (maxZoom + 1));
        }
        double wx = c1.getX() * c1.numPoints;
        double wy = c1.getY() * c1.numPoints;
        wx += c2.getX();
        wy += c2.getY();
        c1.numPoints = c1.numPoints + 1;
        c1.setX(wx / c1.numPoints);
        c1.setY(wy / c1.numPoints);
    }

    /**
     * Get an array of Clusters for given visible region and zoom level
     * note: search on advocators tree, but return the clusters attached to the advocators
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     * @param zoom
     * @return
     */
    public Cluster[] getClusters(double x0, double y0, double x1, double y1, int zoom) {
        double minLng = ((x0 + 180) % 360 + 360) % 360 - 180;
        double minLat = Math.max(-90, Math.min(90, y0));
        double maxLng = x1 == 180 ? 180 : ((x1 + 180) % 360 + 360) % 360 - 180;
        double maxLat = Math.max(-90, Math.min(90, y1));

        if (x1 - x0 >= 360) {
            minLng = -180;
            maxLng = 180;
        } else if (minLng > maxLng) {
            Cluster[] easternHem = this.getClusters(minLng, minLat, 180, maxLat, zoom);
            Cluster[] westernHem = this.getClusters(-180, minLat, maxLng, maxLat, zoom);
            return concat(easternHem, westernHem);
        }

        KDTree<Advocator> advocatorsTree = this.advocatorsTrees[this._limitZoom(zoom)];
        Cluster leftBottom = createPointCluster(minLng, maxLat);
        Cluster rightTop = createPointCluster(maxLng, minLat);
        List<Advocator> advocators = advocatorsTree.range(leftBottom, rightTop);
        Cluster[] clusters = new Cluster[advocators.size()];
        int i = 0;
        for (Advocator advocator: advocators) {
            Cluster cluster = advocator.cluster.clone();
            cluster.setX(xLng(cluster.getX()));
            cluster.setY(yLat(cluster.getY()));
            clusters[i] = cluster;
            i ++;
        }
        return clusters;
    }

    /**
     * Get the clustering labels of given zoom level for all clusters loaded,
     * in the same order as when clusters array was loaded.
     *
     * @param zoom
     * @return
     */
    public int[] getClusteringLabels(int zoom) {
        if (zoom < minZoom || zoom  > maxZoom + 1) {
            MyLogger.error(this.getClass(), "zoom level [" + zoom + "] exceeds the range [" + minZoom + "~" + maxZoom + "]");
            return null;
        }
        if (!keepLabels) {
            MyLogger.error(this.getClass(), "keepLabels turned off, cannot get clustering labels!");
            return null;
        }
        int[] labels = new int[totalNumberOfPoints];

        for (int i = 0; i < this.labels.size(); i ++) {
            labels[i] = this.labels.get(i)[zoom];
        }

        return labels;
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
        System.out.println("    [range search] " + timing.get("rangeSearch") + " seconds");
        System.out.println("    [insert tree] " + timing.get("insertTree") + " seconds");
        System.out.println("    [find earliest] " + timing.get("findEarliest") + " seconds");
        System.out.println("    [merge calculation] " + timing.get("mergeCalculation") + " seconds");
    }
}
