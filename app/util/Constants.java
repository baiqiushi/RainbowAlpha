package util;

public class Constants {

    // Map
    public static int MIN_ZOOM = 0;
    public static int MAX_ZOOM = 18;

    // Data
    public static double MIN_LONGITUDE;
    public static double MIN_LATITUDE;
    public static double MAX_LONGITUDE;
    public static double MAX_LATITUDE;
    public static double MIN_X;
    public static double MIN_Y;
    public static double MAX_X;
    public static double MAX_Y;

    // Database
    public static String DB_URL;
    public static String DB_USERNAME;
    public static String DB_PASSWORD;
    public static String DB_TABLENAME;

    // Message
    public static int DOUBLE_BYTES = 8;
    public static int INT_BYTES = 4;
    public static int HEADER_SIZE = INT_BYTES + 3 * DOUBLE_BYTES;

    public static int RADIUS_IN_PIXELS = 1;

    public static int TILE_RESOLUTION = 1;
}
