package algorithms;

import model.Point;
import java.util.List;

public interface IAlgorithm {

    /**
     * load data into the algorithm incrementally
     *
     * @param points - Point instances with longitude and latitude
     */
    void load(List<Point> points);

    /**
     * answer a query
     *
     * @param lng0 - bounding box left bottom longitude
     * @param lat0 - bounding box left bottom latitude
     * @param lng1 - bounding box right top longitude
     * @param lat1 - bounding box right top latitude
     * @param zoom - zoom level
     * @param resX - resolution width
     * @param resY - resolution height
     * @return - byte[] binary format result message (including preserved HEADER_SIZE header)
     */
    byte[] answerQuery(double lng0, double lat0, double lng1, double lat1, int zoom, int resX, int resY);
}
