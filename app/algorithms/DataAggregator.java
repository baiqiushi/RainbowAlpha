package algorithms;

import model.Cluster;
import model.PointTuple;
import util.I2DIndex;
import util.IndexCreator;
import util.MyMemory;
import util.MyTimer;

import java.util.*;

public class DataAggregator extends SuperCluster {
    String indexType; // KDTree / GridIndex

    I2DIndex index;
    List<Cluster> points;

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public DataAggregator(int _minZoom, int _maxZoom, String _indexType, boolean _analysis) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.indexType = _indexType;
        this.index = IndexCreator.createIndex(indexType, 100);
        this.points = new ArrayList<>();

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        MyMemory.printMemory();
    }

    public void load(List<PointTuple> points) {
        System.out.println("Data Aggregator loading " + points.size() + " points ... ...");
        this.totalNumberOfPoints += points.size();
        long start = System.nanoTime();
        for (PointTuple point: points) {
            this.index.insert(createPointCluster(point.getX(), point.getY(), point.getId()));
        }
        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("Data Aggregator loading is done!");
        System.out.println("Loading time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    /**
     * Get list of Clusters for given visible region and zoom level
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     * @return
     */
    private List<Cluster> getClusters(double x0, double y0, double x1, double y1) {
        double minLng = ((x0 + 180) % 360 + 360) % 360 - 180;
        double minLat = Math.max(-90, Math.min(90, y0));
        double maxLng = x1 == 180 ? 180 : ((x1 + 180) % 360 + 360) % 360 - 180;
        double maxLat = Math.max(-90, Math.min(90, y1));

        if (x1 - x0 >= 360) {
            minLng = -180;
            maxLng = 180;
        } else if (minLng > maxLng) {
            List<Cluster> easternHem = this.getClusters(minLng, minLat, 180, maxLat);
            List<Cluster> westernHem = this.getClusters(-180, minLat, maxLng, maxLat);
            return concat(easternHem, westernHem);
        }

        Cluster leftBottom = createPointCluster(minLng, maxLat);
        Cluster rightTop = createPointCluster(maxLng, minLat);
        List<Cluster> points = this.index.range(leftBottom, rightTop);
        return points;
    }

    private List<Cluster> concat(List<Cluster> a, List<Cluster> b) {
        a.addAll(b);
        return a;
    }

    /**
     * Get an array of Clusters for given visible region and zoom level,
     *     then run tree-cut algorithm to choose a better subset of clusters to return
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     * @param zoom
     * @param treeCut
     * @param measure
     * @param pixels
     * @param bipartite
     * @param resX
     * @param resY
     * @return
     */
    public Cluster[] getClusters(double x0, double y0, double x1, double y1, int zoom, boolean treeCut, String measure, double pixels, boolean bipartite, int resX, int resY) {
        System.out.println("[Data Aggregator] getting clusters for given range [" + x0 + ", " + y0 + "] ~ [" +
                x1 + ", " + y1 + "] and resolution [" + resX + " x " + resY + "]...");
        // get all data points
        List<Cluster> allPoints = getClusters(x0, y0, x1, y1);
        System.out.println("[Data Aggregator] got " + allPoints.size() + " raw data points. Now aggregating ...");
        long start = System.nanoTime();
        // aggregate into a small set of aggregated points based on resolution (resX, resY)
        List<Cluster> aggPoints = new ArrayList<>();
        boolean[][] bitmap = new boolean[resX][resY];
        double iX0 = lngX(x0);
        double iY0 = latY(y0);
        double iX1 = lngX(x1);
        double iY1 = latY(y1);
        double deltaX = iX1 - iX0;
        double deltaY = iY1 - iY0;
        for (Cluster point: allPoints) {
            // find pixel index of this point based on resolution resX * resY
            int i = (int) Math.floor((point.getX() - iX0) * resX / deltaX);
            int j = (int) Math.floor((point.getY() - iY0) * resY / deltaY);
            // only add it into result when <i, j> is not in set
            if (!bitmap[i][j]) {
                bitmap[i][j] = true;
                Cluster cluster = point.clone();
                cluster.setX(xLng(cluster.getX()));
                cluster.setY(yLat(cluster.getY()));
                aggPoints.add(cluster);
            }
        }
        long end = System.nanoTime();
        double aggregateTime = (double) (end - start) / 1000000000.0;
        MyTimer.temporaryTimer.put("aggregate", aggregateTime);
        System.out.println("[Data Aggregator] after aggregation, reduced to " + aggPoints.size() + " points.");
        System.out.println("[Data Aggregator] aggregation time: " + aggregateTime + " seconds.");
        return aggPoints.toArray(new Cluster[aggPoints.size()]);
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }
}
