package util;

import algorithms.SuperCluster;
import model.Point;

public class MemoryTest {
    public static void main(String[] args) {
        I2DIndex<Point>[] gridIndexes = new I2DIndex[36];
        for (int i = 0; i < gridIndexes.length; i ++) {
            gridIndexes[i] = new GridIndex<>(2.384185791015625E-6);
        }

        MyMemory.printMemory();

        // double bytes
        System.out.println(Double.BYTES);
        // int bytes
        System.out.println(Integer.BYTES);

        System.out.println("lngX(-180) = " + SuperCluster.lngX(-180));
        System.out.println("lngX(180) = "  + SuperCluster.lngX(180));
        System.out.println("latY(-90) = " + SuperCluster.latY(-90));
        System.out.println("latY(90) = " + SuperCluster.latY(90));
    }
}
