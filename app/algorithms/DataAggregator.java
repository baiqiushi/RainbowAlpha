package algorithms;

import model.Point;
import model.Query;
import util.*;
import util.render.DeckGLRendererV2;

import java.util.*;

import static util.Mercator.*;

public class DataAggregator implements IAlgorithm {
    String aggregator;

    I2DIndex index;
    int totalNumberOfPoints = 0;

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public DataAggregator(String aggregator) {
        this.aggregator = aggregator;
        if (Constants.MSG_TYPE == 0) {
            this.index = new KDTree<>();
        }
        else if (Constants.MSG_TYPE == 1) {
            this.index = new OptKDTree<>();
        }

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        MyMemory.printMemory();
    }

    public void load(List<Point> points) {
        this.totalNumberOfPoints += points.size();
        System.out.println("[Data Aggregator] loading " + points.size() + " points ... ...");

        MyTimer.startTimer();
        for (Point point: points) {
            this.index.insert(lngLatToXY(point));
        }
        MyTimer.stopTimer();
        double loadTime = MyTimer.durationSeconds();

        if (keepTiming) timing.put("total", timing.get("total") + loadTime);
        System.out.println("[Data Aggregator] loading is done!");
        System.out.println("[Data Aggregator] loading time: " + loadTime + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    @Override
    public void finishLoad() {

    }

    /**
     * Get list of points references for given visible region
     *
     * @param lng0
     * @param lat0
     * @param lng1
     * @param lat1
     * @return
     */
    private List<Point> getPoints(double lng0, double lat0, double lng1, double lat1) {
        double minLng = ((lng0 + 180) % 360 + 360) % 360 - 180;
        double minLat = Math.max(-90, Math.min(90, lat0));
        double maxLng = lng1 == 180 ? 180 : ((lng1 + 180) % 360 + 360) % 360 - 180;
        double maxLat = Math.max(-90, Math.min(90, lat1));

        if (lng1 - lng0 >= 360) {
            minLng = -180;
            maxLng = 180;
        } else if (minLng > maxLng) {
            List<Point> easternHem = this.getPoints(minLng, minLat, 180, maxLat);
            List<Point> westernHem = this.getPoints(-180, minLat, maxLng, maxLat);
            return concat(easternHem, westernHem);
        }

        Point leftBottom = new Point(lngX(minLng), latY(maxLat));
        Point rightTop = new Point(lngX(maxLng), latY(minLat));
        List<Point> points = this.index.range(leftBottom, rightTop);
        return points;
    }

    private List<Point> concat(List<Point> a, List<Point> b) {
        a.addAll(b);
        return a;
    }

    /**
     * Traverse points for given visible region with given nodeHandler
     *
     * @param lng0
     * @param lat0
     * @param lng1
     * @param lat1
     * @param nodeHandler
     * @return resultSize
     */
    private int getPoints(double lng0, double lat0, double lng1, double lat1, I2DIndexNodeHandler nodeHandler) {
        double minLng = ((lng0 + 180) % 360 + 360) % 360 - 180;
        double minLat = Math.max(-90, Math.min(90, lat0));
        double maxLng = lng1 == 180 ? 180 : ((lng1 + 180) % 360 + 360) % 360 - 180;
        double maxLat = Math.max(-90, Math.min(90, lat1));

        if (lng1 - lng0 >= 360) {
            minLng = -180;
            maxLng = 180;
        } else if (minLng > maxLng) {
            int resultSize = 0;
            resultSize += this.getPoints(minLng, minLat, 180, maxLat, nodeHandler);
            resultSize += this.getPoints(-180, minLat, maxLng, maxLat, nodeHandler);
            return resultSize;
        }

        Point leftBottom = new Point(lngX(minLng), latY(maxLat));
        Point rightTop = new Point(lngX(maxLng), latY(minLat));
        return this.index.range(leftBottom, rightTop, nodeHandler);
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
            System.out.println("[Data Aggregator] is answering query Q = { range: [" + lng0 + ", " + lat0 + "] ~ [" +
                    lng1 + ", " + lat1 + "], resolution: [" + resX + " x " + resY + "], zoom: " + zoom + " } ...");

            // get all data points
            MyTimer.startTimer();
            List<Point> allPoints = getPoints(lng0, lat0, lng1, lat1);
            MyTimer.stopTimer();
            double treeTime = MyTimer.durationSeconds();
            MyTimer.temporaryTimer.put("treeTime", treeTime);
            System.out.println("[Data Aggregator] tree search got " + allPoints.size() + " raw data points.");
            System.out.println("[Data Aggregator] tree search time: " + treeTime + " seconds.");

            // do aggregation and build binary result message
            MyTimer.startTimer();
            BinaryMessageBuilder messageBuilder = new BinaryMessageBuilder();
            double lng, lat;
            int resultSize = 0;
            // deck-gl aggregator uses DeckGLRendererV2 to aggregate points into a small subset
            if (this.aggregator.equalsIgnoreCase("deck-gl")) {
                // 1) create an DeckGLRendererV2
                DeckGLRendererV2 deckgl = new DeckGLRendererV2(Constants.RADIUS_IN_PIXELS, 1.0);
                // 2) create a rendering with given resolution
                byte[] image = deckgl.createRendering(resX, resY);
                // 3) traverse all points to reduce those do not change the rendering effect
                for (Point point : allPoints) {
                    lng = xLng(point.getX());
                    lat = yLat(point.getY());
                    if (deckgl.render(image, resX, resY, lng, lat)) {
                        messageBuilder.add(lng, lat);
                        resultSize++;
                    }
                }
            }
            // other aggregators use "snapping" aggregation
            else {
                // aggregate into a small set of aggregated points based on resolution (resX, resY)
                boolean[][] bitmap = new boolean[resX][resY];
                double x0 = lngX(lng0);
                double y1 = latY(lat0);
                double x1 = lngX(lng1);
                double y0 = latY(lat1);
                double deltaX = x1 - x0;
                double deltaY = y1 - y0;
                for (Point point : allPoints) {
                    // find pixel index of this point based on resolution resX * resY
                    int i = (int) Math.floor((point.getX() - x0) * resX / deltaX);
                    int j = (int) Math.floor((point.getY() - y0) * resY / deltaY);
                    // only add it into result when <i, j> is not in set
                    if (!bitmap[i][j]) {
                        bitmap[i][j] = true;
                        lng = xLng(point.getX());
                        lat = yLat(point.getY());
                        messageBuilder.add(lng, lat);
                        resultSize++;
                    }
                }
            }
            MyTimer.stopTimer();
            double aggregateTime = MyTimer.durationSeconds();
            MyTimer.temporaryTimer.put("aggregateTime", aggregateTime);
            System.out.println("[Data Aggregator] after aggregation, reduced to " + resultSize + " points.");
            System.out.println("[Data Aggregator] aggregation time: " + aggregateTime + " seconds.");

            MyTimer.stopTimer();
            System.out.println("[Data Aggregator] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
            return messageBuilder.getBuffer();
        }
        /** message type = bitmap (only support "snapping" aggregation)*/
        else if (Constants.MSG_TYPE == 1) {
            MyTimer.startTimer();
            System.out.println("[Data Aggregator] is answering query Q = { range: [" + lng0 + ", " + lat0 + "] ~ [" +
                    lng1 + ", " + lat1 + "], resolution: [" + resX + " x " + resY + "], zoom: " + zoom + " } ...");

            MyTimer.startTimer();

            BitmapNodeHandler nodeHandler = new BitmapNodeHandler(resX, resY, lng0, lat0, lng1, lat1);
            int resultSize = getPoints(lng0, lat0, lng1, lat1, nodeHandler);
            boolean[][] bitmap = nodeHandler.getBitmap();

            MyTimer.stopTimer();
            double treeTime = MyTimer.durationSeconds();
            MyTimer.temporaryTimer.put("treeTime", treeTime);
            System.out.println("[Data Aggregator] tree search got " + resultSize + " raw data points, and directly aggregates into a bitmap");
            System.out.println("[Data Aggregator] tree search time: " + treeTime + " seconds.");

            // build bitmap message
            MyTimer.startTimer();
            BitmapMessageBuilder bitmapMessageBuilder = new BitmapMessageBuilder(resX, resY, lng0, lat0, lng1, lat1);
            bitmapMessageBuilder.write(bitmap);
            MyTimer.stopTimer();
            double bufferTime = MyTimer.durationSeconds();
            System.out.println("[Data Aggregator] bitmap buffer time: " + bufferTime + " seconds.");

            MyTimer.stopTimer();
            System.out.println("[Data Aggregator] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
            return bitmapMessageBuilder.getBuffer();
        }
        // should not run to this point
        System.err.println("[Data Aggregator] didn't get a valid message type from configuration file, message.type = " + Constants.MSG_TYPE);
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
