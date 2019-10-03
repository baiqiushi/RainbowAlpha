package util;

import model.PointTuple;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PostgreSQL {

    public Connection conn = null;

    public String url = "jdbc:postgresql://localhost/twitter";
    public String username = "postgres";
    public String password = "postgres";

    public boolean connectDB() {
        try {
            conn = DriverManager.getConnection(url, username, password);
            System.out.println("Connected to the PostgreSQL server successfully.");
            return true;
        } catch (SQLException e) {
            System.err.println("Connecting to the PostgreSQL server failed. Exceptions:");
            System.err.println(e.getMessage());
            return false;
        }
    }

    public void disconnectDB() {
        try {
            conn.close();
            System.out.println("Disconnected from the PostgreSQL server successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PointTuple[] queryPointTuplesForKeyword(String keyword) {

        if (this.conn == null) {
            if(!this.connectDB()) {
                return null;
            }
        }

        System.out.println("Querying PostgreSQL with keyword: [" + keyword + "] ... ...");
        List<PointTuple> result = new ArrayList<PointTuple>();
        String sql = "SELECT create_at, x, y FROM tweets WHERE to_tsvector('english', text)@@to_tsquery('english', ?)";
        long start = System.nanoTime();
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, keyword);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                Timestamp create_at = rs.getTimestamp(1);
                Double x = rs.getDouble(2);
                Double y = rs.getDouble(3);
                PointTuple pt = new PointTuple(2);
                pt.timestamp = create_at;
                pt.setDimensionValue(0, x);
                pt.setDimensionValue(1, y);
                result.add(pt);
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        long end = System.nanoTime();
        System.out.println("Querying PostgreSQL with keyword: [" + keyword + "] is done! ");
        System.out.println("Takes time: " + TimeUnit.SECONDS.convert(end - start, TimeUnit.NANOSECONDS) + " seconds");
        System.out.println("Result size: " + result.size());
        return result.toArray(new PointTuple[result.size()]);
    }
}
