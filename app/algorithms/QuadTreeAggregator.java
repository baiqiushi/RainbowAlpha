package algorithms;

import model.Cluster;
import model.Point;
import model.PointTuple;
import util.Constants;
import util.MyMemory;

import java.util.*;

public class QuadTreeAggregator extends SuperCluster {

    public static double highestResScale;

    public class QuadTree {
        // Store count of the sub-tree
        public int count;
        public Point point;

        // children
        public QuadTree northWest;
        public QuadTree northEast;
        public QuadTree southWest;
        public QuadTree southEast;

        public QuadTree() {
            this.count = 0;
        }

        public boolean containsPoint(double cX, double cY, double halfWidth, double halfHeight, Point point) {
            if (point.getX() >= (cX - halfWidth)
                    && point.getY() >= (cY - halfHeight)
                    && point.getX() < (cX + halfWidth)
                    && point.getY() < (cY + halfHeight)) {
                return true;
            }
            else {
                return false;
            }
        }

        public boolean intersectsBBox(double c1X, double c1Y, double halfWidth1, double halfHeight1,
                                      double c2X, double c2Y, double halfWidth2, double halfHeight2) {
            // bbox 1
            double left = c1X - halfWidth1;
            double right = c1X + halfWidth1;
            double bottom = c1Y - halfHeight1;
            double top = c1Y + halfHeight1;
            // bbox 2
            double minX = c2X - halfWidth2;
            double maxX = c2X + halfWidth2;
            double minY = c2Y - halfHeight2;
            double maxY = c2Y + halfHeight2;

            // right to the right
            if (minX > right) return false;
            // left to the left
            if (maxX < left) return false;
            // above the top
            if (minY > top) return false;
            // below the bottom
            if (maxY < bottom) return false;

            return true;
        }

        public boolean insert(double cX, double cY, double halfWidth, double halfHeight, Point point) {
            // Ignore objects that do not belong in this quad tree
            if (!containsPoint(cX, cY, halfWidth, halfHeight, point)) {
                return false;
            }
            // If this node is leaf and empty, put this point on this node
            if (this.point == null && this.northWest == null) {
                this.point = point;
                this.count = 1;
                return true;
            }
            // Else, add count into this node
            this.count ++;

            // if boundary is smaller than highestResScale, drop this point
            if (Math.max(halfWidth, halfHeight) * 2 < highestResScale) {
                return false;
            }

            // Otherwise, subdivide
            if (this.northWest == null) {
                this.subdivide();
                // insert current node's point into corresponding quadrant
                this.insertNorthWest(cX, cY, halfWidth, halfHeight, this.point);
                this.insertNorthEast(cX, cY, halfWidth, halfHeight, this.point);
                this.insertSouthWest(cX, cY, halfWidth, halfHeight, this.point);
                this.insertSouthEast(cX, cY, halfWidth, halfHeight, this.point);
                this.point = null;
            }

            // insert new point into corresponding quadrant
            if (insertNorthWest(cX, cY, halfWidth, halfHeight, point)) return true;
            if (insertNorthEast(cX, cY, halfWidth, halfHeight, point)) return true;
            if (insertSouthWest(cX, cY, halfWidth, halfHeight, point)) return true;
            if (insertSouthEast(cX, cY, halfWidth, halfHeight, point)) return true;

            return false;
        }

        boolean insertNorthWest(double _cX, double _cY, double _halfWidth, double _halfHeight, Point point) {
            double halfWidth = _halfWidth / 2;
            double halfHeight = _halfHeight / 2;
            double cX = _cX - halfWidth;
            double cY = _cY + halfHeight;
            return this.northWest.insert(cX, cY, halfWidth, halfHeight, point);
        }

        boolean insertNorthEast(double _cX, double _cY, double _halfWidth, double _halfHeight, Point point) {
            double halfWidth = _halfWidth / 2;
            double halfHeight = _halfHeight / 2;
            double cX = _cX + halfWidth;
            double cY = _cY + halfHeight;
            return this.northEast.insert(cX, cY, halfWidth, halfHeight, point);
        }

        boolean insertSouthWest(double _cX, double _cY, double _halfWidth, double _halfHeight, Point point) {
            double halfWidth = _halfWidth / 2;
            double halfHeight = _halfHeight / 2;
            double cX = _cX - halfWidth;
            double cY = _cY - halfHeight;
            return this.southWest.insert(cX, cY, halfWidth, halfHeight, point);
        }

        boolean insertSouthEast(double _cX, double _cY, double _halfWidth, double _halfHeight, Point point) {
            double halfWidth = _halfWidth / 2;
            double halfHeight = _halfHeight / 2;
            double cX = _cX + halfWidth;
            double cY = _cY - halfHeight;
            return this.southEast.insert(cX, cY, halfWidth, halfHeight, point);
        }

        void subdivide() {
            this.northWest = new QuadTree();
            this.northEast = new QuadTree();
            this.southWest = new QuadTree();
            this.southEast = new QuadTree();
            nodesCount += 4;
        }

        public List<Point> range(double ncX, double ncY, double nhalfWidth, double nhalfHeight,
                                   double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                                   double resScale) {
            List<Point> pointsInRange = new ArrayList<>();

            // Automatically abort if the range does not intersect this quad
            if (!intersectsBBox(ncX, ncY, nhalfWidth, nhalfHeight, rcX, rcY, rhalfWidth, rhalfHeight))
                return pointsInRange; // empty list

            // Terminate here, if there are no children
            if (this.northWest == null) {
                if (this.point != null) {
                    pointsInRange.add(this.point);
                }
                return pointsInRange;
            }

            // Terminate here, if this node's boundary is already smaller than resScale
            if (Math.max(nhalfWidth, nhalfHeight) * 2 <= resScale) {
                // add this node center to result
                pointsInRange.add(new Point(ncX, ncY, -1));
                return pointsInRange;
            }

            // Otherwise, add the points from the children
            double cX, cY;
            double halfWidth, halfHeight;
            halfWidth = nhalfWidth / 2;
            halfHeight = nhalfHeight / 2;
            // northwest
            cX = ncX - halfWidth;
            cY = ncY + halfHeight;
            pointsInRange.addAll(this.northWest.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale));

            // northeast
            cX = ncX + halfWidth;
            cY = ncY + halfHeight;
            pointsInRange.addAll(this.northEast.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale));

            // southwest
            cX = ncX - halfWidth;
            cY = ncY - halfHeight;
            pointsInRange.addAll(this.southWest.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale));

            // southeast
            cX = ncX + halfWidth;
            cY = ncY - halfHeight;
            pointsInRange.addAll(this.southEast.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale));

            return pointsInRange;
        }
    }

    QuadTree quadTree;
    double quadTreeCX;
    double quadTreeCY;
    double quadTreeHalfWidth;
    double quadTreeHalfHeight;
    static long nodesCount = 0; // count quad-tree nodes

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public QuadTreeAggregator(int _minZoom, int _maxZoom, int resX, int resY) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;

        this.quadTreeCX = (Constants.MAX_X + Constants.MIN_X) / 2;
        this.quadTreeCY = (Constants.MAX_Y + Constants.MIN_Y) / 2;
        this.quadTreeHalfWidth = (Constants.MAX_X - Constants.MIN_X) / 2;
        this.quadTreeHalfHeight = (Constants.MAX_Y - Constants.MIN_Y) / 2;
        this.quadTree = new QuadTree();

        double pixelWidth = (Constants.MAX_X - Constants.MIN_X) / resX;
        double pixelHeight = (Constants.MAX_Y - Constants.MIN_Y) / resY;
        highestResScale = Math.min(pixelWidth / Math.pow(2, this.maxZoom - 4), pixelHeight / Math.pow(2, this.maxZoom - 4));

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        MyMemory.printMemory();
    }

    public void load(List<PointTuple> points) {
        System.out.println("QuadTree Aggregator loading " + points.size() + " points ... ...");
        long start = System.nanoTime();
        int count = 0;
        int skip = 0;
        for (PointTuple point: points) {
            if (this.quadTree.insert(this.quadTreeCX, this.quadTreeCY, this.quadTreeHalfWidth, this.quadTreeHalfHeight,
                    createPoint(point.getX(), point.getY(), point.getId())))
                count ++;
            else
                skip ++;
        }
        this.totalNumberOfPoints += count;
        long end = System.nanoTime();
        System.out.println("QuadTree Aggregator inserted " + count + " points and skipped " + skip + " points.");
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("QuadTree Aggregator loading is done!");
        System.out.println("Loading time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();

        //-DEBUG-//
        System.out.println("==== Until now ====");
        System.out.println("QuadTree has inserted " + this.totalNumberOfPoints + " points.");
        System.out.println("QuadTree has generated " + nodesCount + " nodes.");
        //-DEBUG-//
    }

    protected Point createPoint(double _x, double _y, int _id) {
        return new Point(lngX(_x), latY(_y), _id);
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
        System.out.println("[QuadTree Aggregator] getting clusters for given range [" + x0 + ", " + y0 + "] ~ [" +
                x1 + ", " + y1 + "] and resolution [" + resX + " x " + resY + "]...");

        long start = System.nanoTime();
        double iX0 = lngX(x0);
        double iY0 = latY(y0);
        double iX1 = lngX(x1);
        double iY1 = latY(y1);
        double resScale = Math.min((iX1 - iX0) / resX, (iY0 - iY1) / resY);
        double rcX = (iX0 + iX1) / 2;
        double rcY = (iY0 + iY1) / 2;
        double rhalfWidth = (iX1 - iX0) / 2;
        double rhalfHeight = (iY0 - iY1) / 2;

        if (treeCut) {
            resScale = resScale * pixels;
        }

        System.out.println("[QuadTree Aggregator] starting range search on QuadTree with: \n" +
                "range = [(" + rcX + ", " + rcY + "), " + rhalfWidth + ", " + rhalfHeight + "] ; \n" +
                "resScale = " + resScale + ";");

        List<Point> points = this.quadTree.range(this.quadTreeCX, this.quadTreeCY, this.quadTreeHalfWidth, this.quadTreeHalfHeight,
                rcX, rcY, rhalfWidth, rhalfHeight, resScale);
        List<Cluster> results = new ArrayList<>();
        for (Point point: points) {
            Cluster cluster = new Cluster(xLng(point.getX()), yLat(point.getY()), point.getId());
            results.add(cluster);
        }
        long end = System.nanoTime();
        double totalTime = (double) (end - start) / 1000000000.0;
        System.out.println("[QuadTree Aggregator] getClusters time: " + totalTime + " seconds.");
        return results.toArray(new Cluster[results.size()]);
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }
}
