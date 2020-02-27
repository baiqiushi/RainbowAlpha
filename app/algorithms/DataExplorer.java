package algorithms;

import model.Cluster;
import model.PointTuple;
import util.I2DIndex;
import util.IndexCreator;
import util.MyMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataExplorer extends SuperCluster {
    String indexType; // KDTree / GridIndex

    I2DIndex index;
    List<Cluster> points;

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public DataExplorer(int _minZoom, int _maxZoom, String _indexType, boolean _analysis) {
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
        System.out.println("Data Explorer loading " + points.size() + " points ... ...");
        this.totalNumberOfPoints += points.size();
        long start = System.nanoTime();
        for (PointTuple point: points) {
            this.index.insert(createPointCluster(point.getX(), point.getY(), point.getId()));
        }
        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("Data Explorer loading is done!");
        System.out.println("Loading time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    /**
     * Get an array of Clusters for given visible region and zoom level
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

        Cluster leftBottom = createPointCluster(minLng, maxLat);
        Cluster rightTop = createPointCluster(maxLng, minLat);
        List<Cluster> points = this.index.range(leftBottom, rightTop);
        Cluster[] clusters = new Cluster[points.size()];
        int i = 0;
        for (Cluster point: points) {
            Cluster cluster = point.clone();
            cluster.setX(xLng(cluster.getX()));
            cluster.setY(yLat(cluster.getY()));
            clusters[i] = cluster;
            i ++;
        }
        return clusters;
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }
}
