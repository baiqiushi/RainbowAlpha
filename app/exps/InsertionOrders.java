package exps;

import algorithms.SuperCluster;
import model.PointTuple;
import smile.clustering.KMeans;
import util.PostgreSQL;
import util.RandIndex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public class InsertionOrders {

    public static final int minZoom = 4;
    public static final int maxZoom = 8;

    public static void printPointTuples(List<PointTuple> pointTuples, int limit) {
        System.out.println("==================== Point Tuples (" + pointTuples.size() + ") ====================");
        for (int i = 0; i < Math.min(pointTuples.size(), limit); i ++) {
            System.out.println(pointTuples.get(i));
        }
        System.out.println("... ...");
    }

    public static void main(String[] args) throws IOException {

        System.out.println("Please enter a keyword: (press ENTER to finish)");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String keyword = bufferedReader.readLine();

        // 0) Load original data from PostgreSQL
        PostgreSQL postgreSQL = new PostgreSQL();
        List<PointTuple> pointTuples = postgreSQL.queryPointTuplesForKeyword(keyword);
        int length = pointTuples.size();
        for (int i = 0; i < length; i ++) {
            pointTuples.get(i).setId(i);
        }

        printPointTuples(pointTuples, 10);

        // 1) Generate data of different orders
        // A - original order (database order)
        double[][] aPoints = new double[length][2];
        for (int i = 0; i < length; i ++) {
            aPoints[i][0] = pointTuples.get(i).getX();
            aPoints[i][1] = pointTuples.get(i).getY();
        }
        // B - reversed order (reverse A)
        double[][] bPoints = new double[length][2];
        for (int i = 0; i < length; i ++) {
            bPoints[length - 1 - i][0] = pointTuples.get(i).getX();
            bPoints[length - 1 - i][1] = pointTuples.get(i).getY();
        }
        // C - spatial order (left-bottom to right-top)
        List<PointTuple> pointTuplesList = pointTuples;
        Collections.sort(pointTuples, PointTuple.getSpatialComparator());
        PointTuple[] cPointTuples = pointTuplesList.toArray(new PointTuple[pointTuplesList.size()]);
        double[][] cPoints = new double[length][2];
        for (int i = 0; i < cPointTuples.length; i ++) {
            cPoints[i][0] = cPointTuples[i].getX();
            cPoints[i][1] = cPointTuples[i].getY();
        }
        // D - reversed spatial order (right-top to left-bottom)
        Collections.sort(pointTuplesList, PointTuple.getReverseSpatialComparator());
        PointTuple[] dPointTuples = pointTuplesList.toArray(new PointTuple[pointTuplesList.size()]);
        double[][] dPoints = new double[length][2];
        for (int i = 0; i < dPointTuples.length; i ++) {
            dPoints[i][0] = dPointTuples[i].getX();
            dPoints[i][1] = dPointTuples[i].getY();
        }

        // 2-1) Run SuperCluster for all different orders
        SuperCluster aSuperCluster =  new SuperCluster(minZoom, maxZoom, "KDTree");
        aSuperCluster.load(aPoints);
        SuperCluster bSuperCluster = new SuperCluster(minZoom, maxZoom, "KDTree");
        bSuperCluster.load(bPoints);
        SuperCluster cSuperCluster = new SuperCluster(minZoom, maxZoom, "KDTree");
        cSuperCluster.load(cPoints);
        SuperCluster dSuperCluster = new SuperCluster(minZoom, maxZoom, "KDTree");
        dSuperCluster.load(dPoints);

        // 2-2) Run K-Means for all different orders, k decided by given zoom level's aSuperCluster


        // - DEBUG - //
        // Output zoom level 2 clusters for all clustering results
//        int exampleZoom = 2;
//        Cluster[] clusters = aSuperCluster.getClusters(exampleZoom);
//        System.out.println("================= " + exampleZoom + "th zoom level of A clusters (" + clusters.length + ") =================");
//        for (int i = 0; i < clusters.length; i ++) {
//            System.out.println(clusters[i]);
//        }
//        clusters = bSuperCluster.getClusters(exampleZoom);
//        System.out.println("================= " + exampleZoom + "th zoom level of B clusters (" + clusters.length + ") =================");
//        for (int i = 0; i < clusters.length; i ++) {
//            System.out.println(clusters[i]);
//        }
//        clusters = cSuperCluster.getClusters(exampleZoom);
//        System.out.println("================= " + exampleZoom + "th zoom level of C clusters (" + clusters.length + ") =================");
//        for (int i = 0; i < clusters.length; i ++) {
//            System.out.println(clusters[i]);
//        }
//        clusters = dSuperCluster.getClusters(exampleZoom);
//        System.out.println("================= " + exampleZoom + "th zoom level of D clusters (" + clusters.length + ") =================");
//        for (int i = 0; i < clusters.length; i ++) {
//            System.out.println(clusters[i]);
//        }
        // - DEBUG - //

        // 3-1) Compute the rand index of A-B, C-D for SuperCluster
        System.out.println("==================== RandIndex/AdjustedRandIndex of different orders ====================");
        System.out.println("==    A - original order");
        System.out.println("==    B - reversed order");
        System.out.println("==    C - spatial (left-bottom to right-top) order");
        System.out.println("==    D - reversed spatial (right-top to left-bottom) order");
        System.out.println("=========================================================================================");
        System.out.println(keyword + ",    " + length);
        System.out.println("==================== [SuperCluster] ====================");
        System.out.println("zoom,    k,    ri(A-B),    ri(C-D),    ari(A-B),    ari(C-D)");
        for (int z = minZoom; z <= maxZoom; z ++) {
            int k = aSuperCluster.getClusters(z).length;

            // labels for A
            int[] aLabels = aSuperCluster.getClusteringLabels(z);

            // labels for B
            int[] bLabels = bSuperCluster.getClusteringLabels(z);
            for (int i = 0; i < bLabels.length / 2; i ++) {
                int temp = bLabels[i];
                bLabels[i] = bLabels[bLabels.length - 1 - i];
                bLabels[bLabels.length - 1 - i] = temp;
            }

            // labels for C
            int[] cLabelsStage = cSuperCluster.getClusteringLabels(z);
            int[] cLabels = new int[cLabelsStage.length];
            for (int i = 0; i < cLabelsStage.length; i ++) {
                PointTuple labeledPointTuple = cPointTuples[i];
                cLabels[labeledPointTuple.getId()] = cLabelsStage[i];
            }

            // labels for D
            int[] dLabelsStage = dSuperCluster.getClusteringLabels(z);
            int[] dLabels = new int[dLabelsStage.length];
            for (int i = 0; i < dLabelsStage.length; i ++) {
                PointTuple labeledPointTuple = dPointTuples[i];
                dLabels[labeledPointTuple.getId()] = dLabelsStage[i];
            }

            System.out.println(z +
                    ",    " + k +
                    ",    " + RandIndex.randIndex(aLabels, bLabels) +
                    ",    " + RandIndex.randIndex(cLabels, dLabels) +
                    ",    " + RandIndex.adjustedRandIndex(aLabels, bLabels) +
                    ",    " + RandIndex.adjustedRandIndex(cLabels, dLabels));
        }

        // 3-2) Compute the rand index of A-B, C-D for KMeans
        System.out.println("==================== [KMeans] ====================");
        System.out.println("zoom,    k,    ri(A-B),    ri(C-D),    ari(A-B),    ari(C-D)");
        for (int z = minZoom; z <= maxZoom; z ++) {
            int k = aSuperCluster.getClusters(z).length;

            // labels for A
            KMeans aKMeans = new KMeans(aPoints, k);
            int[] aLabels = aKMeans.getClusterLabel();

            // labels for B
            KMeans bKMeans = new KMeans(bPoints, k);
            int[] bLabels = bKMeans.getClusterLabel();
            for (int i = 0; i < bLabels.length / 2; i ++) {
                int temp = bLabels[i];
                bLabels[i] = bLabels[bLabels.length - 1 - i];
                bLabels[bLabels.length - 1 - i] = temp;
            }

            // labels for C
            KMeans cKMeans = new KMeans(cPoints, k);
            int[] cLabelsStage = cKMeans.getClusterLabel();
            int[] cLabels = new int[cLabelsStage.length];
            for (int i = 0; i < cLabelsStage.length; i ++) {
                PointTuple labeledPointTuple = cPointTuples[i];
                cLabels[labeledPointTuple.getId()] = cLabelsStage[i];
            }

            // labels for D
            KMeans dKMeans = new KMeans(dPoints, k);
            int[] dLabelsStage = dKMeans.getClusterLabel();
            int[] dLabels = new int[dLabelsStage.length];
            for (int i = 0; i < dLabelsStage.length; i ++) {
                PointTuple labeledPointTuple = dPointTuples[i];
                dLabels[labeledPointTuple.getId()] = dLabelsStage[i];
            }

            System.out.println(z +
                    ",    " + k +
                    ",    " + RandIndex.randIndex(aLabels, bLabels) +
                    ",    " + RandIndex.randIndex(cLabels, dLabels) +
                    ",    " + RandIndex.adjustedRandIndex(aLabels, bLabels) +
                    ",    " + RandIndex.adjustedRandIndex(cLabels, dLabels));
        }


        postgreSQL.disconnectDB();
    }
}
