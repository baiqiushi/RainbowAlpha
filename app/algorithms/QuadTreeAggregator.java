package algorithms;

import model.Point;
import model.Query;
import util.*;

import java.util.*;

import static util.Mercator.*;

public class QuadTreeAggregator implements IAlgorithm {

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
                pointsInRange.add(new Point(ncX, ncY));
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

        public int range(double ncX, double ncY, double nhalfWidth, double nhalfHeight,
                                 double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                                 double resScale, I2DIndexNodeHandler nodeHandler) {

            // Automatically abort if the range does not intersect this quad
            if (!intersectsBBox(ncX, ncY, nhalfWidth, nhalfHeight, rcX, rcY, rhalfWidth, rhalfHeight))
                return 0; // empty list

            // Terminate here, if there are no children
            if (this.northWest == null) {
                if (this.point != null) {
                    nodeHandler.handleNode(this.point.getX(), this.point.getY(), (short) 0);
                }
                return 1;
            }

            // Terminate here, if this node's boundary is already smaller than resScale
            if (Math.max(nhalfWidth, nhalfHeight) * 2 <= resScale) {
                nodeHandler.handleNode(ncX, ncY, (short) 0);
                return 1;
            }

            // Otherwise, add the points from the children
            double cX, cY;
            double halfWidth, halfHeight;
            halfWidth = nhalfWidth / 2;
            halfHeight = nhalfHeight / 2;
            // northwest
            cX = ncX - halfWidth;
            cY = ncY + halfHeight;

            int count = 0;

            count += this.northWest.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale, nodeHandler);

            // northeast
            cX = ncX + halfWidth;
            cY = ncY + halfHeight;
            count += this.northEast.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale, nodeHandler);

            // southwest
            cX = ncX - halfWidth;
            cY = ncY - halfHeight;
            count += this.southWest.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale, nodeHandler);

            // southeast
            cX = ncX + halfWidth;
            cY = ncY - halfHeight;
            count += this.southEast.range(cX, cY, halfWidth, halfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale, nodeHandler);

            return count;
        }
    }

    QuadTree quadTree;
    double quadTreeCX;
    double quadTreeCY;
    double quadTreeHalfWidth;
    double quadTreeHalfHeight;
    int totalNumberOfPoints = 0;
    int totalStoredNumberOfPoints = 0;
    static long nodesCount = 0; // count quad-tree nodes

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public QuadTreeAggregator(int resX, int resY) {
        this.quadTreeCX = (Constants.MAX_X + Constants.MIN_X) / 2;
        this.quadTreeCY = (Constants.MAX_Y + Constants.MIN_Y) / 2;
        this.quadTreeHalfWidth = (Constants.MAX_X - Constants.MIN_X) / 2;
        this.quadTreeHalfHeight = (Constants.MAX_Y - Constants.MIN_Y) / 2;
        this.quadTree = new QuadTree();

        double pixelWidth = (Constants.MAX_X - Constants.MIN_X) / resX;
        double pixelHeight = (Constants.MAX_Y - Constants.MIN_Y) / resY;
        highestResScale = Math.min(pixelWidth / Math.pow(2, Constants.MAX_ZOOM - 4), pixelHeight / Math.pow(2, Constants.MAX_ZOOM - 4));

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        MyMemory.printMemory();
    }

    public void load(List<Point> points) {
        System.out.println("[QuadTree Aggregator] loading " + points.size() + " points ... ...");

        MyTimer.startTimer();
        int count = 0;
        int skip = 0;
        this.totalNumberOfPoints += points.size();
        for (Point point: points) {
            if (this.quadTree.insert(this.quadTreeCX, this.quadTreeCY, this.quadTreeHalfWidth, this.quadTreeHalfHeight, lngLatToXY(point)))
                count ++;
            else
                skip ++;
        }
        this.totalStoredNumberOfPoints += count;
        MyTimer.stopTimer();
        double loadTime = MyTimer.durationSeconds();

        System.out.println("[QuadTree Aggregator] inserted " + count + " points and skipped " + skip + " points.");
        if (keepTiming) timing.put("total", timing.get("total") + loadTime);
        System.out.println("[QuadTree Aggregator] loading is done!");
        System.out.println("[QuadTree Aggregator] loading time: " + loadTime + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();

        //-DEBUG-//
        System.out.println("==== Until now ====");
        System.out.println("[QuadTree Aggregator] has processed " + this.totalNumberOfPoints + " points.");
        System.out.println("[QuadTree Aggregator] has stored " + this.totalStoredNumberOfPoints + " points.");
        System.out.println("[QuadTree Aggregator] has skipped " + skip + " points.");
        System.out.println("[QuadTree Aggregator] has generated " + nodesCount + " nodes.");
        //-DEBUG-//
    }

    @Override
    public void finishLoad() {

    }

    public byte[] answerQuery(Query query) {
        double lng0 = query.bbox[0];
        double lat0 = query.bbox[1];
        double lng1 = query.bbox[2];
        double lat1 = query.bbox[3];
        int zoom = query.zoom;
        int resX = query.resX;
        int resY = query.resY;

        /** message type is binary */
        if (Constants.MSG_TYPE == 0) {
            MyTimer.startTimer();
            System.out.println("[QuadTree Aggregator] is answering query Q = { range: [" + lng0 + ", " + lat0 + "] ~ [" +
                    lng1 + ", " + lat1 + "], resolution: [" + resX + " x " + resY + "], zoom: " + zoom + " } ...");

            double x0 = lngX(lng0);
            double y1 = latY(lat0);
            double x1 = lngX(lng1);
            double y0 = latY(lat1);
            double resScale = Math.min((x1 - x0) / resX, (y1 - y0) / resY);
            double rcX = (x0 + x1) / 2;
            double rcY = (y1 + y0) / 2;
            double rhalfWidth = (x1 - x0) / 2;
            double rhalfHeight = (y1 - y0) / 2;

            System.out.println("[QuadTree Aggregator] starting range search on QuadTree with: \n" +
                    "range = [(" + rcX + ", " + rcY + "), " + rhalfWidth + ", " + rhalfHeight + "] ; \n" +
                    "resScale = " + resScale + ";");

            MyTimer.startTimer();
            List<Point> points = this.quadTree.range(this.quadTreeCX, this.quadTreeCY, this.quadTreeHalfWidth, this.quadTreeHalfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale);
            MyTimer.stopTimer();
            double treeTime = MyTimer.durationSeconds();

            MyTimer.temporaryTimer.put("treeTime", treeTime);
            System.out.println("[QuadTree Aggregator] tree search got " + points.size() + " data points.");
            System.out.println("[QuadTree Aggregator] tree search time: " + treeTime + " seconds.");

            // build binary result message
            MyTimer.startTimer();
            BinaryMessageBuilder messageBuilder = new BinaryMessageBuilder();
            double lng, lat;
            int resultSize = 0;
            for (Point point : points) {
                lng = xLng(point.getX());
                lat = yLat(point.getY());
                messageBuilder.add(lng, lat);
                resultSize++;
            }
            MyTimer.stopTimer();
            double buildBinaryTime = MyTimer.durationSeconds();
            MyTimer.temporaryTimer.put("aggregateTime", buildBinaryTime);
            System.out.println("[QuadTree Aggregator] build binary result with  " + resultSize + " points.");
            System.out.println("[QuadTree Aggregator] build binary result time: " + buildBinaryTime + " seconds.");

            MyTimer.stopTimer();
            System.out.println("[QuadTree Aggregator] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
            return messageBuilder.getBuffer();
        }
        /** message type = bitmap (only support "snapping" aggregation)*/
        else if (Constants.MSG_TYPE == 1) {
            MyTimer.startTimer();
            System.out.println("[QuadTree Aggregator] is answering query Q = { range: [" + lng0 + ", " + lat0 + "] ~ [" +
                    lng1 + ", " + lat1 + "], resolution: [" + resX + " x " + resY + "], zoom: " + zoom + " } ...");

            double x0 = lngX(lng0);
            double y1 = latY(lat0);
            double x1 = lngX(lng1);
            double y0 = latY(lat1);
            double resScale = Math.min((x1 - x0) / resX, (y1 - y0) / resY);
            double rcX = (x0 + x1) / 2;
            double rcY = (y1 + y0) / 2;
            double rhalfWidth = (x1 - x0) / 2;
            double rhalfHeight = (y1 - y0) / 2;

            System.out.println("[QuadTree Aggregator] starting range search on QuadTree with: \n" +
                    "range = [(" + rcX + ", " + rcY + "), " + rhalfWidth + ", " + rhalfHeight + "] ; \n" +
                    "resScale = " + resScale + ";");

            MyTimer.startTimer();

            BitmapNodeHandler nodeHandler = new BitmapNodeHandler(resX, resY, lng0, lat0, lng1, lat1);
            int resultSize = this.quadTree.range(this.quadTreeCX, this.quadTreeCY, this.quadTreeHalfWidth, this.quadTreeHalfHeight,
                    rcX, rcY, rhalfWidth, rhalfHeight, resScale, nodeHandler);
            boolean[][] bitmap = nodeHandler.getBitmap();

            MyTimer.stopTimer();
            double treeTime = MyTimer.durationSeconds();

            MyTimer.temporaryTimer.put("treeTime", treeTime);
            System.out.println("[QuadTree Aggregator] tree search got " + resultSize + " data points, and directly aggregates into a bitmap.");
            System.out.println("[QuadTree Aggregator] tree search time: " + treeTime + " seconds.");

            // build bitmap message
            MyTimer.startTimer();
            BitmapMessageBuilder bitmapMessageBuilder = new BitmapMessageBuilder(resX, resY, lng0, lat0, lng1, lat1);
            bitmapMessageBuilder.write(bitmap);
            MyTimer.stopTimer();
            double bufferTime = MyTimer.durationSeconds();
            System.out.println("[QuadTree Aggregator] bitmap buffer time: " + bufferTime + " seconds.");

            MyTimer.stopTimer();
            System.out.println("[QuadTree Aggregator] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
            return bitmapMessageBuilder.getBuffer();
        }
        // should not run to this point
        System.err.println("[QuadTree Aggregator] didn't get a valid message type from configuration file, message.type = " + Constants.MSG_TYPE);
        return new byte[0];
    }

    @Override
    public boolean readFromFile(String fileName) {
        return false;
    }

    @Override
    public boolean writeToFile(String fileName) {
        return false;
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }
}
