package util;

import algorithms.SuperCluster;
import model.Point;

public class GridIndexTest {
    public static void main(String[] args) {
        I2DIndex<Point> gridIndex = new GridIndex<>(0, 0, 100, 100, 2);

        // put a point at every vertex of side-1 squares
        for (int i = 0; i <= 99; i ++) {
            for (int j = 0; j <= 99; j ++) {
                Point p = new Point(i, j, j*100+i);
                gridIndex.insert(p);
            }
        }

        // gridIndex.print();

        // circle with center (3,3) and radius 1.5,
        // should return 9 clusters: (2,2), (2,3), (2,4), (3,2), (3,3), (3,4), (4,2), (4,3), (4,4)
        System.out.println("[test 1] clusters within center=(3,3), raius=1.5: ");
        System.out.println(gridIndex.within(new Point(3, 3, 0), 1.5));

        // circle with center (0,0) and radius 3.5
        // should return 13 clusters: (0,0), (0,1), (0,2), (0,3), (1,0), (1,1), (1,2), (1,3), (2,0), (2,1), (2,2), (3,0), (3,1)
        System.out.println("[test 2] clusters within center=(0,0), raius=3.5: ");
        System.out.println(gridIndex.within(new Point(0, 0, 0), 3.5));

        // range with (1.5, 1.5), (4.5, 4.5)
        // should return 9 clusters: (2,2), (2,3), (2,4), (3,2), (3,3), (3,4), (4,2), (4,3), (4,4)
        System.out.println("[test 3] clusters of range (1.5, 1.5) -> (4.5, 4.5): ");
        System.out.println(gridIndex.range(new Point(1.5, 1.5, 0), new Point(4.5, 4.5, 0)));

        // range with (-1, -2), (1.5, 2.8)
        // should return 6 clusters: (0,0), (0,1), (0,2), (1,0), (1,1), (1,2)
        System.out.println("[test 4] clusters of range (-1, -2) -> (1.5, 2.8): ");
        System.out.println(gridIndex.range(new Point(-1, -1, 0), new Point(1.5, 2.8, 0)));


        System.out.println("lng = -180, x = " + SuperCluster.lngX(-180.0));
        System.out.println("lng = 0, x = " + SuperCluster.lngX(0.0));
        System.out.println("lng = 180, x = " + SuperCluster.lngX(180.0));

        System.out.println("lat = 90, y = " + SuperCluster.latY(90.0));
        System.out.println("lat = 0, y = " + SuperCluster.latY(0.0));
        System.out.println("lat = -90, y = " + SuperCluster.latY(-90.0));
    }
}
