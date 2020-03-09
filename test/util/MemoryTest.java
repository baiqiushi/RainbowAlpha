package util;

import static util.Mercator.*;

public class MemoryTest {
    public static void main(String[] args) {
        MyMemory.printMemory();

        // double bytes
        System.out.println(Double.BYTES);
        // int bytes
        System.out.println(Integer.BYTES);

        System.out.println("lngX(-180) = " + lngX(-180));
        System.out.println("lngX(180) = "  + lngX(180));
        System.out.println("latY(-90) = " + latY(-90));
        System.out.println("latY(90) = " + latY(90));
    }
}
