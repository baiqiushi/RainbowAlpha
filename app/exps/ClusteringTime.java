package exps;

import algorithms.SuperCluster;
import model.PointTuple;
import util.PostgreSQL;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClusteringTime {
    public static void main(String[] args) throws IOException {

        System.out.println("Please enter a limit: (press ENTER to finish, 0 means 5k ~ 2560k)");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        int limit = Integer.valueOf(bufferedReader.readLine());

        PostgreSQL  postgreSQL = new PostgreSQL();

        if (limit == 0) {
            int[] scales = {5000, 10000, 20000, 40000, 80000, 160000, 320000, 640000, 1280000, 2560000};

            double[] times = new double[scales.length];

            for (int k = 0; k < scales.length; k++) {

                System.out.println("========== Experiment for " + scales[k] + " clusters starts! ==========");

                // 1) Load original data from PostgreSQL
                PointTuple[] pointTuples = postgreSQL.queryPointTuplesForLimit(scales[k]);
                int length = pointTuples.length;
                for (int i = 0; i < length; i++) {
                    pointTuples[i].setId(i);
                }

                // 2) Compose the input clusters array
                double[][] points = new double[length][2];
                for (int i = 0; i < length; i++) {
                    points[i][0] = pointTuples[i].getX();
                    points[i][1] = pointTuples[i].getY();
                }

                // 3) Load the clusters into SuperCluster
                SuperCluster superCluster = new SuperCluster();
                long start = System.nanoTime();
                superCluster.load(points);
                long end = System.nanoTime();
                System.out.println("Loading " + scales[k] + " clusters into SuperCluster is done! ");
                System.out.println("Takes time: " + ((double) (end - start)) / 1000000000.0 + " seconds");
                times[k] = ((double) (end - start)) / 1000000000.0;
            }

            System.out.println("========== All experiments done! ==========");
            for (int i = 0; i < scales.length; i++) {
                if (i > 0) {
                    System.out.print(", ");
                }
                System.out.print(scales[i]);
            }
            System.out.println();
            for (int i = 0; i < times.length; i++) {
                if (i > 0) {
                    System.out.print(", ");
                }
                System.out.print(times[i]);
            }
        } else {
            System.out.println("========== Experiment for " + limit + " clusters starts! ==========");

            // 1) Load original data from PostgreSQL
            PointTuple[] pointTuples = postgreSQL.queryPointTuplesForLimit(limit);
            int length = pointTuples.length;
            for (int i = 0; i < length; i++) {
                pointTuples[i].setId(i);
            }

            // 2) Compose the input clusters array
            double[][] points = new double[length][2];
            for (int i = 0; i < length; i++) {
                points[i][0] = pointTuples[i].getX();
                points[i][1] = pointTuples[i].getY();
            }

            // 3) Load the clusters into SuperCluster
            SuperCluster superCluster = new SuperCluster();
            long start = System.nanoTime();
            superCluster.load(points);
            long end = System.nanoTime();
            System.out.println("Loading " + limit + " clusters into SuperCluster is done! ");
            System.out.println("Takes time: " + ((double) (end - start)) / 1000000000.0 + " seconds");
        }
    }
}
