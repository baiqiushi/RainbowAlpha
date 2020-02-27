package algorithms;

import model.Cluster;
import model.PointTuple;
import util.*;

import java.util.ArrayList;
import java.util.List;

public class SuperCluster {

    int minZoom; // min zoom to generate clusters on
    int maxZoom; // max zoom level to cluster the clusters on
    int radius = 20; // cluster radius in pixels
    int extent = 128;  // tile extent (radius is calculated relative to it)
    int totalNumberOfPoints = 0;
    String indexType; // KDTree / GridIndex
    I2DIndex<Cluster>[] indexes;
    Cluster[][] clusters;

    private double[] radiuses; // store computed radius of each zoom level

    private void initRadiuses() {
        this.radiuses = new double[maxZoom + 1];
        for (int i = 0; i < this.radiuses.length; i ++) {
            this.radiuses[i] = radius / (extent * Math.pow(2, i));
        }
    }

    public SuperCluster() {
        this(Constants.DEFAULT_MIN_ZOOM, Constants.DEFAULT_MAX_ZOOM, Constants.DEFAULT_INDEX_TYPE);
    }

    public SuperCluster(int _minZoom, int _maxZoom, String _indexType) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;

        this.indexType = _indexType;
        this.indexes = IndexCreator.createIndexArray(this.indexType, maxZoom + 2);

        this.clusters = new Cluster[maxZoom + 2][];
        this.initRadiuses();
    }

    private Cluster[] createPointClusters(double[][] points) {
        Cluster[] clusters = new Cluster[points.length];
        for (int i = 0; i < points.length; i ++) {
            clusters[i] = createPointCluster(points[i][0], points[i][1], i);
        }
        return clusters;
    }

    private Cluster[] createPointClusters(List<PointTuple> points) {
        Cluster[] clusters = new Cluster[points.size()];
        for (int i = 0; i < points.size(); i ++) {
            clusters[i] = createPointCluster(points.get(i).getX(), points.get(i).getY(), i);
        }
        return clusters;
    }

    /**
     * build hierarchy of the super cluster
     *
     * @param clusters - point clusters created from raw clusters
     */
    private void buildHierarchy(Cluster[] clusters) {
        // index input clusters into a KD-tree
        this.indexes[maxZoom + 1] = IndexCreator.createIndex(this.indexType, getRadius(maxZoom));
        this.indexes[maxZoom + 1].load(clusters);
        this.clusters[maxZoom + 1] = clusters;

        // cluster clusters on max zoom, then cluster the results on previous zoom, etc.;
        // results in a cluster hierarchy across zoom levels
        for (int z = maxZoom; z >= minZoom; z --) {
            // create a new set of clusters for the zoom and index them with a KD-tree
            clusters = this._clusters(clusters, z);
            this.indexes[z] = IndexCreator.createIndex(this.indexType, getRadius(z - 1 >= 0? z - 1: 0));
            this.indexes[z].load(clusters);
            this.clusters[z] = clusters;
        }

        // abandon the tree and array storing the raw data, to be fair with iSuperCluster
        this.indexes[maxZoom + 1] = null;
        this.clusters[maxZoom + 1] = null;
    }

    public void load(List<PointTuple> points) {
        System.out.println("SuperCluster loading " + points.size() + " clusters ... ...");
        long start = System.nanoTime();
        this.totalNumberOfPoints = points.size();
        // generate a cluster object for each point
        Cluster[] clusters = createPointClusters(points);

        // build hierarchy of clusters based on the clusters for raw clusters
        buildHierarchy(clusters);

        long end = System.nanoTime();
        System.out.println("SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.clusters[maxZoom].length);
    }

    /**
     * @param points, an array of coordinates,
     *                at point[i] must be x = clusters[i][0], y = clusters[i][1]
     */
    public void load(double[][] points) {
        System.out.println("SuperCluster loading " + points.length + " clusters ... ...");
        long start = System.nanoTime();
        this.totalNumberOfPoints = points.length;
        // generate a cluster object for each point
        Cluster[] clusters = createPointClusters(points);

        // build hierarchy of clusters based on the clusters for raw clusters
        buildHierarchy(clusters);

        long end = System.nanoTime();
        System.out.println("SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.clusters[maxZoom].length);
    }

    protected Cluster[] _clusters(Cluster[] points, int zoom) {
        List<Cluster> clusters = new ArrayList<>();

        double r = getRadius(zoom);

        // loop through each point
        for (int i = 0; i < points.length; i ++) {
            Cluster p = points[i];

            // if we've already visited the point at this zoom level, skip it
            if (p.zoom <= zoom) {
                continue;
            }
            p.zoom = zoom;

            // find all nearby clusters
            I2DIndex<Cluster> tree = this.indexes[zoom + 1];
            List<Cluster> neighbors = tree.within(p, r);

            int numPoints = p.numPoints == 0? 1: p.numPoints;
            double wx = p.getX() * numPoints;
            double wy = p.getY() * numPoints;

            // encode both zoom and point index on which the cluster originated
            int id = (i << 5) + (zoom + 1);

            // store all children Ids in parent cluster
            List<Cluster> children = new ArrayList<>();

            for (Cluster neighbor : neighbors) {

                // filter out neighbors that are already processed or the point itself
                if (neighbor.zoom <= zoom) {
                    continue;
                }
                neighbor.zoom = zoom; // save the zoom (so it doesn't get processed twice)

                int numPoints2 = neighbor.numPoints == 0? 1: neighbor.numPoints;
                // accumulate coordinates for calculating weighted center
                wx += neighbor.getX() * numPoints2;
                wy += neighbor.getY() * numPoints2;

                numPoints += numPoints2;
                neighbor.parentId = id;

                // children is empty, this neighbor is an original point
                if (neighbor.children.isEmpty()) {
                    children.add(neighbor);
                }
                // children is not empty, this neighbor is already a cluster
                else {
                    children.addAll(neighbor.children);
                }
            }

            if (numPoints == 1) {
                clusters.add(p);
            } else {
                p.parentId = id;
                Cluster parent = createCluster(wx / numPoints, wy / numPoints, id, numPoints);

                // children is empty, this point is an original point
                if (p.children.isEmpty()) {
                    children.add(p);
                }
                // children is not empty, this point is already a cluster
                else {
                    children.addAll(p.children);
                }
                parent.children = children;

                if (parent.children.size() == 1) {
                    parent.expansionZoom = parent.children.get(0).expansionZoom;
                }
                else {
                    parent.expansionZoom = zoom  + 1;
                }

                clusters.add(parent);
            }
        }

        return clusters.toArray(new Cluster[clusters.size()]);
    }

    protected Cluster createPointCluster(double _x, double _y, int _id) {
        Cluster c = new Cluster(lngX(_x), latY(_y), _id);
        return c;
    }

    protected Cluster createCluster(double _x, double _y, int _id, int _numPoints) {
        Cluster c = new Cluster(_x, _y, _id);
        c.numPoints = _numPoints;
        return c;
    }

    protected Cluster createPointCluster(double _x, double _y) {
        Cluster c = new Cluster(lngX(_x), latY(_y), -1);
        return c;
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

        I2DIndex<Cluster> tree = this.indexes[this._limitZoom(zoom)];
        Cluster leftBottom = createPointCluster(minLng, maxLat);
        Cluster rightTop = createPointCluster(maxLng, minLat);
        List<Cluster> points = tree.range(leftBottom, rightTop);
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
     * @return
     */
    public Cluster[] getClusters(double x0, double y0, double x1, double y1, int zoom, boolean treeCut, String measure, double pixels, boolean bipartite) {
        return getClusters(x0, y0, x1, y1, zoom);
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
        return getClusters(x0, y0, x1, y1, zoom, treeCut, measure, pixels, bipartite);
    }

    /**
     * Get an array of all clusters for the given zoom level
     *
     * @param zoom
     * @return
     */
    public Cluster[] getClusters(int zoom) {
        if (zoom < minZoom || zoom  > maxZoom + 1) {
            return null;
        }
        Cluster[] clusters = new Cluster[this.clusters[zoom].length];
        for (int i = 0; i < this.clusters[zoom].length; i ++) {
            Cluster cluster = this.clusters[zoom][i].clone();
            cluster.setX(xLng(cluster.getX()));
            cluster.setY(yLat(cluster.getY()));
            clusters[i] = cluster;
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
            return null;
        }
        int[] labels = new int[totalNumberOfPoints];
        Cluster[] clusters = getClusters(zoom);
        //-DEBUG-//
//        System.out.println("[getClusteringLabels] clusters size = " + clusters.length);
//        for (int i = 0; i < clusters.length; i ++) {
//            System.out.print(clusters[i].id);
//            if (!clusters[i].children.isEmpty()) {
//                System.out.print("-->");
//                for (Cluster child: clusters[i].children) {
//                    System.out.print(child.id + ",");
//                }
//            }
//            System.out.println();
//        }
        //-DEBUG-//
        for (int i = 0; i < clusters.length; i ++) {
            Cluster cluster = clusters[i];
            if (cluster.numPoints == 0) {
                labels[cluster.getId()] = cluster.getId();
            }
            else {
                for (Cluster child : cluster.children) {
                    labels[child.getId()] = cluster.getId();
                }
            }
        }
        return labels;
    }

    /**
     * Return the distance between given clusters/clusters on given zoom level
     *
     * @param zoom
     * @param p1
     * @param p2
     * @return
     */
    public double getClusterDistance(int zoom, int p1, int p2) {
        if (zoom < minZoom || zoom  > maxZoom + 1) {
            return -1.0;
        }

        Cluster[] clusters = this.clusters[zoom];
        Cluster c1 = null, c2 = null;
        for (int i = 0; i < clusters.length; i ++) {
            if (clusters[i].getId() == p1) {
                c1 = clusters[i];
            }
            if (clusters[i].getId() == p2) {
                c2 = clusters[i];
            }
            if (c1 != null && c2 != null) {
                break;
            }
        }
        if (c1 == null || c2 == null) {
            return -1.0;
        }
        return c1.distanceTo(c2);
    }

    /**
     * Return radius for given zoom level
     *
     * @param zoom
     * @return
     */
    public double getRadius(int zoom) {
        return this.radiuses[zoom];
    }

    protected Cluster[] concat(Cluster[] a, Cluster[] b) {
        int length = a.length + b.length;
        Cluster[] result = new Cluster[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    protected int _limitZoom(int zoom) {
        //return Math.max(minZoom, Math.min(zoom, maxZoom + 1));
        // do not support query for the raw data level anymore
        return Math.max(minZoom, Math.min(zoom, maxZoom));
    }

    // longitude to spherical mercator in [0..1] range
    public static double lngX(double lng) {
        return lng / 360 + 0.5;
    }

    // latitude to spherical mercator in [0..1] range
    public static double latY(double lat) {
        double sin = Math.sin(lat * Math.PI / 180);
        double y = (0.5 - 0.25 * Math.log((1 + sin) / (1 - sin)) / Math.PI);
        return y < 0 ? 0 : y > 1 ? 1 : y;
    }

    // spherical mercator to longitude
    public static double xLng(double x) {
        return (x - 0.5) * 360;
    }

    // spherical mercator to latitude
    public static double yLat(double y) {
        double y2 = (180 - y * 360) * Math.PI / 180;
        return 360 * Math.atan(Math.exp(y2)) / Math.PI - 90;
    }
}
