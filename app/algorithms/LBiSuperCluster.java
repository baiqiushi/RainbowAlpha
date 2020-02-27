package algorithms;

import model.Advocator;
import model.Cluster;
import model.ClusterDelta;
import model.PointTuple;
import util.*;

import java.util.*;

/**
 * Level Batch incremental SuperCluster
 *
 * Level Batch incremental SuperCluster framework,
 * handle inserts, updates and deletes level by level with a flagged priority Queue
 *
 * logic is the same as BiSuperCluster,
 *   but since the handling order could be different BiSuperCluster,
 *   clustering result could be different.
 */
public class LBiSuperCluster extends SuperCluster {
    // advocators at each level
    I2DIndex<Advocator>[] advocatorsIndexes;
    // clusters at each level
    List<Cluster>[] advocatorClusters;
    // clusters at each level as a tree
    I2DIndex<Cluster>[] clustersIndexes;

    // clusters at each level that are pending for insert into tree
    Queue<Cluster>[] pendingClusters;

    // clusters at each level that are registered for handling insert, update and delete operations
    Set<Cluster>[] insertedClusters;
    Set<Cluster>[] updatedClusters;
    Set<Cluster>[] deletedClusters;

    int pointIdSeq;
    int pointSeqSeq;
    int totalInsertCount = 0;
    int totalUpdateCount = 0;
    int totalShiftCount = 0;
    int totalDeleteCount= 0;

    double miu = 0.5;

    String indexType; // KDTree / GridIndex

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public LBiSuperCluster(int _minZoom, int _maxZoom, String _indexType, boolean _analysis) {

        super(_minZoom, _maxZoom, _indexType);

        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.indexType = _indexType;
        this.advocatorsIndexes = IndexCreator.createIndexArray(indexType, maxZoom + 1);
        this.advocatorClusters = new List[maxZoom + 1];
        this.clustersIndexes = IndexCreator.createIndexArray(indexType, maxZoom + 1);
        this.pendingClusters = new Queue[maxZoom + 1];
        this.insertedClusters = new HashSet[maxZoom + 1];
        this.updatedClusters = new HashSet[maxZoom + 1];
        this.deletedClusters = new HashSet[maxZoom + 1];
        this.pointIdSeq = 0;
        this.pointSeqSeq = 0;

        for (int z = 0; z <= maxZoom; z ++) {
            this.advocatorsIndexes[z] = IndexCreator.createIndex(indexType, getRadius(z));
            this.advocatorClusters[z] = new ArrayList<>();
            this.clustersIndexes[z] = IndexCreator.createIndex(indexType, getRadius(z - 1 >= 0? z - 1: 0));
            this.pendingClusters[z] = new LinkedList<>();
            this.insertedClusters[z] = new HashSet<>();
            this.updatedClusters[z] = new HashSet<>();
            this.deletedClusters[z] = new HashSet<>();
        }

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
            timing.put("findEarliest", 0.0);
            timing.put("insert-rangeSearch", 0.0);
            timing.put("shift-rangeSearch", 0.0);
            timing.put("mergeCalculation", 0.0);
            timing.put("splitCalculation", 0.0);
            timing.put("maintainAdvocatorTree", 0.0);
            timing.put("maintainClusterTree", 0.0);
            timing.put("maintainSets", 0.0);
        }

        MyMemory.printMemory();
    }

    private void insertPointClusters(double[][] points) {
        for (int i = 0; i < points.length; i ++) {
            insert(createPointCluster(points[i][0], points[i][1], this.pointIdSeq ++, this.pointSeqSeq ++));
        }
    }

    private void insertPointClusters(List<PointTuple> points) {
        for (int i = 0; i < points.size(); i ++) {
            insert(createPointCluster(points.get(i).getX(), points.get(i).getY(), this.pointIdSeq ++, this.pointSeqSeq ++));
        }
    }

    private void buildHierarchy() {
        int insertCount = 0;
        int updateCount = 0;
        int shiftCount = 0;
        int deleteCount = 0;
        // handle insert, update and delete of clusters level by level bottom-up
        for (int z = maxZoom; z > minZoom; z --) {

            if (keepTiming) MyTimer.startTimer();
            // update clustersTree for level z
            Cluster pending;
            while ((pending = this.pendingClusters[z].poll()) != null) {
                this.clustersIndexes[z].insert(pending);
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            // shifting at level z affects clustering results of level (z-1)
            double r = getRadius(z - 1);

            int insertCountLevel = 0;
            int updateCountLevel = 0;
            int shiftCountLevel = 0;
            int deleteCountLevel = 0;
            // handle all changes at z level to form clusters at (z-1) level
            // (1) handle all updated clusters
            for (Cluster updated: this.updatedClusters[z]) {
                updateCountLevel ++;
                onUpdate(updated, z);
            }
            // (2) handle all deleted clusters
            for (Cluster deleted: this.deletedClusters[z]) {
                onDelete(deleted, z);
                deleteCountLevel ++;
            }
            this.deletedClusters[z].clear();
            // (3) shift all updated & exceeding threshold clusters
            for (Cluster updated: this.updatedClusters[z]) {
                if (updated.distanceTo(updated.advocator) > miu * r) {
                    shift(updated, updated.advocator, z);
                    shiftCountLevel ++;
                }
            }
            this.updatedClusters[z].clear();
            // (3) handle all inserted clusters
            for (Cluster inserted: this.insertedClusters[z]) {
                onInsert(inserted, z);
                insertCountLevel ++;
            }
            this.insertedClusters[z].clear();

            System.out.println("zoom level [" + z + "] inserted " + insertCountLevel + " clusters.");
            System.out.println("zoom level [" + z + "] updated " + updateCountLevel + " clusters.");
            System.out.println("zoom level [" + z + "]   -- shifted " + shiftCountLevel + " clusters.");
            System.out.println("zoom level [" + z + "] delete " + deleteCountLevel + " clusters.");

            insertCount += insertCountLevel;
            updateCount += updateCountLevel;
            shiftCount += shiftCountLevel;
            deleteCount += deleteCountLevel;

            //-DEBUG-//
//            Cluster[] allClusters = getClusters(-180, -90, 180, 90, z-1);
//            int totalPointsCount = 0;
//            for (int i = 0; i < allClusters.length; i ++) {
//                totalPointsCount += allClusters[i].numPoints==0?1:allClusters[i].numPoints;
//                // check whether cluster's numPoints = sum of all its children's numPoints
//                if (allClusters[i].children != null) {
//                    int sumChildren = 0;
//                    for (Cluster child: allClusters[i].children) {
//                        sumChildren += child.numPoints==0?1:child.numPoints;
//                    }
//                    if ((allClusters[i].numPoints==0?1:allClusters[i].numPoints) != sumChildren) {
//                        System.out.println("[Error] Cluster " + allClusters[i].getId() + ":[" + allClusters[i].numPoints + "] is not consistent with its children!");
//                        StringBuilder sb = new StringBuilder();
//                        for (Cluster child: allClusters[i].children) {
//                            sb.append(child.getId() + ":[" + child.numPoints + "], ");
//                        }
//                        System.out.println("Its children: " + sb.toString());
//                    }
//                }
//                // check whether cluster's coordinate is NaN
//                if (Double.isNaN(allClusters[i].getX()) || Double.isNaN(allClusters[i].getY())) {
//                    System.out.println("[Error] Cluster " + allClusters[i].getId() + ":[" + allClusters[i].numPoints + "]: " + allClusters[i]);
//                }
//            }
//            if (totalPointsCount != this.totalNumberOfPoints) {
//                System.out.println("[Error] zoom level [" + (z - 1) + "] has totally ====> " + totalPointsCount + " <==== clusters. Correct should be " + this.totalNumberOfPoints);
//            }
            //-DEBUG-//
        }

        this.totalInsertCount += insertCount;
        this.totalUpdateCount += updateCount;
        this.totalShiftCount += shiftCount;
        this.totalDeleteCount += deleteCount;

        System.out.println("---------------------------------------------");
        System.out.println("This batch insert clusters: " + insertCount);
        System.out.println("This batch update clusters: " + updateCount);
        System.out.println("This batch   -- shift clusters: " + shiftCount);
        System.out.println("This batch delete clusters: " + deleteCount);
        System.out.println("---------------------------------------------");
        System.out.println("This batch handled clusters: " + (insertCount + updateCount + deleteCount));
        System.out.println("---------------------------------------------");
    }

    public void load(double[][] points) {
        System.out.println("Level Batch incremental SuperCluster loading " + points.length + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.length;
        System.out.println("Total # of clusters should be " + totalNumberOfPoints + " now.");

        // insert clusters to max zoom level one by one
        insertPointClusters(points);

        // build hierarchy
        buildHierarchy();

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("[LBiSC] Level Batch incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.advocatorClusters[maxZoom].size());
        System.out.println("---------------------------------------------");
        System.out.println("Total insert clusters: " + this.totalInsertCount);
        System.out.println("Total update clusters: " + this.totalUpdateCount);
        System.out.println("Total   -- shift clusters: " + this.totalShiftCount);
        System.out.println("Total delete clusters: " + this.totalDeleteCount);
        System.out.println("---------------------------------------------");
        System.out.println("Total handled clusters: " + (this.totalInsertCount + this.totalUpdateCount + this.totalDeleteCount));
        System.out.println("---------------------------------------------");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    public void load(List<PointTuple> points) {
        System.out.println("Level Batch incremental SuperCluster loading " + points.size() + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.size();
        System.out.println("Total # of clusters should be " + totalNumberOfPoints + " now.");

        // insert clusters to max zoom level one by one
        insertPointClusters(points);

        // build hierarchy
        buildHierarchy();

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("[LBiSC] Strict Batch incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        System.out.println("Max zoom level clusters # = " + this.advocatorClusters[maxZoom].size());
        System.out.println("---------------------------------------------");
        System.out.println("Total insert clusters: " + this.totalInsertCount);
        System.out.println("Total update clusters: " + this.totalUpdateCount);
        System.out.println("Total   -- shift clusters: " + this.totalShiftCount);
        System.out.println("Total delete clusters: " + this.totalDeleteCount);
        System.out.println("---------------------------------------------");
        System.out.println("Total handled clusters: " + (this.totalInsertCount + this.totalUpdateCount + this.totalDeleteCount));
        System.out.println("---------------------------------------------");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    /**
     * Get an array of Clusters for given visible region and zoom level
     * note: search on advocators tree, but return the clusters attached to the advocators
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     * @param zoom
     * @return
     */
    public Cluster[] getClusters(double x0, double y0, double x1, double y1, int zoom) {
        double minLng = ((x0 + 180) % 360 + 360) % 360 - 180;
        double minLat = Math.max(-90, Math.min(90, y0));
        double maxLng = x1 == 180 ? 180 : ((x1 + 180) % 360 + 360) % 360 - 180;
        double maxLat = Math.max(-90, Math.min(90, y1));

        if (x1 - x0 >= 360) {
            minLng = -180;
            maxLng = 180;
        } else if (minLng > maxLng) {
            Cluster[] easternHem = this.getClusters(minLng, minLat, 180, maxLat, zoom);
            Cluster[] westernHem = this.getClusters(-180, minLat, maxLng, maxLat, zoom);
            return concat(easternHem, westernHem);
        }

        I2DIndex<Advocator> advocatorsIndex = this.advocatorsIndexes[this._limitZoom(zoom)];
        Cluster leftBottom = createPointCluster(minLng, maxLat);
        Cluster rightTop = createPointCluster(maxLng, minLat);
        List<Advocator> advocators = advocatorsIndex.range(leftBottom, rightTop);
        Cluster[] clusters = new Cluster[advocators.size()];
        int i = 0;
        for (Advocator advocator: advocators) {
            Cluster cluster = advocator.cluster.clone();
            cluster.setX(xLng(cluster.getX()));
            cluster.setY(yLat(cluster.getY()));
            clusters[i] = cluster;
            i ++;
        }
        //-DEBUG-//
//        System.out.println("[getClusters] advocatorsTree.size = " + advocatorsTree.size());
//        System.out.println("[getClusters] result.size = " + clusters.length);
//        System.out.println("[getClusters] this level advocatorClusters.size = " + this.advocatorClusters[this._limitZoom(zoom)].size());
//        System.out.println("[getClusters] result: ===============>");
//        for (Cluster result: clusters) {
//            System.out.println(result);
//        }
        //-DEBUG-//
        return clusters;
    }

    /**
     * check if c's children are differentiable for view at zoom level
     *    because current zoom level's radius is rendered by 20 pixels (diameter = 40px)
     *        if c's children's average pairwise distance > 1/5 * radius,
     *            it means the children can be drawn separately by at least 4 pixels,
     *                then they are differentiable
     * @param c
     * @param measure - the measure to compare with differentiable distance, "avg" / "max" / "min"
     * @param differentiableDistance - real distance that is differentiable given by the user
     * @return
     */
    private boolean isDifferentiable(Cluster c, String measure, double differentiableDistance) {
        //-DEBUG-//
        //System.out.println("[Debug]     checking if c " + printCluster(c) + "'s children are differentiable?");
        //-DEBUG-//
        // if c has no children, return false
        if (c.children == null || c.children.isEmpty()) {
            //System.out.println("[Debug]     No children ~~NO~~");
            return false;
        }
        // if c has only one children, return true
        if (c.children.size() == 1) {
            //System.out.println("[Debug]     Single child YES!");
            return true;
        }

        // check if average pairwise distance between all children of c is larger than differentiableDistance
        double avgDistance = 0.0;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0.0;
        for (int i = 0; i < c.children.size() - 1; i ++) {
            for (int j = 1; j < c.children.size(); j ++) {
                avgDistance += c.children.get(i).distanceTo(c.children.get(j));
                minDistance = Math.min(minDistance, c.children.get(i).distanceTo(c.children.get(j)));
                maxDistance = Math.max(maxDistance, c.children.get(i).distanceTo(c.children.get(j)));
            }
        }
        c.diameter = maxDistance / 2.0;
        avgDistance = avgDistance * 2 / (c.children.size() * (c.children.size() -  1));

        //-DEBUG-//
        //System.out.println("[Debug]         - average distance of children = " + avgDistance);
        //System.out.println("[Debug]         - minimum distance of children = " + minDistance);
        //System.out.println("[Debug]         - radius for children level = " + radius);
        //-DEBUG-//
        double metric;
        switch (measure.toLowerCase()) {
            case "min":
                metric = minDistance;
                break;
            case "max":
                metric = maxDistance;
                break;
            default:
                metric = avgDistance;
        }
        if (metric >= differentiableDistance) {
            //-DEBUG-//
            //System.out.println("[Debug]     distance >= radius: Differentiable? YES!");
            //-DEBUG-//
            return true;
        }
        else {
            //-DEBUG-//
            //System.out.println("[Debug]     distance < radius: Differentiable? ~~NO~~");
            //-DEBUG-//
            return false;
        }
//        if (c.numPoints < 100) return true;
//        else return false;
    }

    private double getDifferentiableDistance(double pixels, int zoom) {
        // current view zoom level's radius
        double radius = getRadius(zoom);
        // radius of view zoom level is usually represented by 12 pixels
        double distancePerPixel = radius / 20;
        // make sure given pixels distance between [1~12]
        //pixels = pixels > 12? 12: Math.max(pixels, 1);
        // real distance of given # pixels of differentiable distance
        return distancePerPixel * pixels;
    }

    /**
     * Get an array of Clusters for given visible region and zoom level,
     *     then run tree-cut algorithm to choose a better subset of clusters to return
     * note: search on advocators tree, but return the clusters attached to the advocators
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     * @param zoom
     * @return
     */
    public Cluster[] getClusters(double x0, double y0, double x1, double y1, int zoom, boolean treeCut, String measure, double pixels, boolean bipartite) {
        Cluster[] clusters = getClusters(x0, y0, x1, y1, zoom);
        List<Cluster> betterClusters = new ArrayList<>();
        // run tree-cut algorithm
        if (treeCut) {
            MyTimer.startTimer();

            // real distance of given # pixels of differentiable distance
            double differentiableDistance = getDifferentiableDistance(pixels, zoom);

            //-DEBUG-//
            System.out.println("[Debug] [LBiSuperCluster --> getClusters] is running Tree-Cut algorithm ...");
            System.out.println("[Debug] [LBiSuperCluster --> getClusters] differentiable distance = " + differentiableDistance);
            //-DEBUG-//

            // bipartite the clusters into two groups first
            if (bipartite) {
                //-DEBUG-//
                System.out.println("[Debug] [LBiSuperCluster --> getClusters] will bipartite the frontier clusters first.");
                //-DEBUG-//

                // keep bipartite and expand until clusters is empty
                while (clusters.length > 3) {
                    // sort the clusters by numPoints in descending order
                    Arrays.sort(clusters, (o1, o2) -> (o2.numPoints - o1.numPoints));

                    //-DEBUG-//
                    System.out.println("==== Before bipartite ====");
                    for (Cluster cluster : clusters) System.out.print(cluster.numPoints + ", ");
                    System.out.println();
                    //-DEBUG-//

                    // if all clusters are clusters, just expand them all
                    if (clusters[0].numPoints == 0) break;

                    // bipartite the clusters with leftSum >= rightSum
                    long leftSum = 0;
                    long rightSum = 0;
                    // calculate rightSum first
                    for (Cluster cluster : clusters) rightSum += cluster.numPoints == 0 ? 1 : cluster.numPoints;
                    int index = 0;
                    // find the bipartite index
                    for (int i = 0; i < clusters.length - 1; i++) {
                        leftSum += clusters[i].numPoints == 0 ? 1 : clusters[i].numPoints;
                        rightSum -= clusters[i].numPoints == 0 ? 1 : clusters[i].numPoints;
                        if (leftSum >= rightSum) {
                            index = i;
                            break;
                        }
                    }
                    //-DEBUG-//
                    System.out.println("[Debug] [LBiSuperCluster --> getClusters] Total # of frontier clusters = " + clusters.length);
                    System.out.println("[Debug] [LBiSuperCluster --> getClusters] Bipartite the clusters at index [" + index + "] with leftSum = " + leftSum + " & rightSum = " + rightSum);
                    System.out.println("[Debug] [LBiSuperCluster --> getClusters] Left = " + printCluster(clusters[index]));
                    System.out.println("[Debug] [LBiSuperCluster --> getClusters] Right = " + printCluster(clusters[index + 1]));
                    System.out.println("[Debug] [LBiSuperCluster --> getClusters] Will expand # of frontier clusters = " + (clusters.length - index));
                    //-DEBUG-//
                    // put all clusters before maxGap into result
                    betterClusters.addAll(Arrays.asList(Arrays.copyOfRange(clusters, 0, index + 1)));

                    // expand all clusters after maxGap to their children if it isDifferentiable
                    List<Cluster> nextRound = new ArrayList<>();
                    for (int i = index + 1; i < clusters.length; i++) {
                        if (isDifferentiable(clusters[i], measure, differentiableDistance)) {
                            for (Cluster child : clusters[i].children) {
                                Cluster cluster = child.clone();
                                cluster.setX(xLng(cluster.getX()));
                                cluster.setY(yLat(cluster.getY()));
                                nextRound.add(cluster);
                            }
                        } else {
                            betterClusters.add(clusters[i]);
                        }
                    }
                    clusters = nextRound.toArray(new Cluster[nextRound.size()]);
                }
            }

            // a queue for breadth-first-search
            Queue<Cluster> frontier = new LinkedList<>();

            frontier.addAll(Arrays.asList(clusters));

            // check frontier one-by-one,
            //     if it's not differentiable, add it to results;
            //     else, add all its children back to queue;
            while (!frontier.isEmpty()) {
                Cluster c = frontier.poll();
                if (isDifferentiable(c, measure, differentiableDistance)) {
                    for (Cluster child: c.children) {
                        Cluster cluster = child.clone();
                        cluster.setX(xLng(cluster.getX()));
                        cluster.setY(yLat(cluster.getY()));
                        frontier.add(cluster);
                    }
                }
                else {
                    betterClusters.add(c);
                }
            }
            clusters = betterClusters.toArray(new Cluster[betterClusters.size()]);
            MyTimer.stopTimer();
            double treeCutTime = MyTimer.durationSeconds();
            MyTimer.temporaryTimer.put("treeCut", treeCutTime);
            System.out.println("[LBiSuperCluster] tree-cut takes " + treeCutTime + " seconds.");
        }

        return clusters;
    }

    /**
     * Return the distance between given clusters/clusters on given zoom level
     *
     * @param zoom
     * @param p1
     * @param p2
     * @return
     */
    public double getClusterDistance(int zoom, int p1, int p2) {
        if (zoom < minZoom || zoom  > maxZoom + 1) {
            return -1.0;
        }

        List<Cluster> clusters = this.advocatorClusters[zoom];
        Cluster c1 = null, c2 = null;
        for (int i = 0; i < clusters.size(); i ++) {
            if (clusters.get(i).getId() == p1) {
                c1 = clusters.get(i);
            }
            if (clusters.get(i).getId() == p2) {
                c2 = clusters.get(i);
            }
            if (c1 != null && c2 != null) {
                break;
            }
        }
        if (c1 == null || c2 == null) {
            return -1.0;
        }
        return c1.distanceTo(c2);
    }

    protected Cluster createPointCluster(double _x, double _y, int _id, int _seq) {
        Cluster c = new Cluster(lngX(_x), latY(_y), _id);
        c.zoom = maxZoom;
        c.seq = _seq;
        return c;
    }

    /**
     * insert point cluster c into maxZoom level
     *   Note: c is always one point itself
     */
    private void insert(Cluster c) {
        double radius = getRadius(maxZoom);

        if (keepTiming) MyTimer.startTimer();
        // Find all earlier advocators c can merge into
        List<Advocator> advocators = this.advocatorsIndexes[maxZoom].within(c, radius);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("insert-rangeSearch", timing.get("insert-rangeSearch") + MyTimer.durationSeconds());

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {
            Advocator newAdvocator = new Advocator(c.getX(), c.getY(), c.seq);
            newAdvocator.cluster = c;
            c.advocator = newAdvocator;

            if (keepTiming) MyTimer.startTimer();
            this.advocatorsIndexes[maxZoom].insert(newAdvocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

            this.advocatorClusters[maxZoom].add(c);

            if (keepTiming) MyTimer.startTimer();
            this.clustersIndexes[maxZoom].insert(c);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            if (keepTiming) MyTimer.startTimer();
            // register as newly inserted
            this.insertedClusters[maxZoom].add(c);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
        }
        // if earlier advocators' groups could be merged into
        else {
            if (keepTiming) MyTimer.startTimer();
            // find the earliest advocator
            Advocator earliestAdvocator = null;
            for (Advocator advocator: advocators) {
                if (earliestAdvocator == null) {
                    earliestAdvocator = advocator;
                }
                else if (earliestAdvocator.getId() > advocator.getId()) {
                    earliestAdvocator = advocator;
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("findEarliest", timing.get("findEarliest") + MyTimer.durationSeconds());

            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;

            if (keepTiming) MyTimer.startTimer();
            // delete cluster from clustersTree
            if (!cluster.dirty) {
                this.clustersIndexes[maxZoom].delete(cluster);
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            if (keepTiming) MyTimer.startTimer();
            // encode id if it's first time to be a cluster
            if (cluster.numPoints == 0) {
                encodeId(cluster, maxZoom);
            }

            // merge c's coordinate into cluster's centroid calculation
            merge(cluster, c.getX(), c.getY(), c.numPoints);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("mergeCalculation", timing.get("mergeCalculation") + MyTimer.durationSeconds());

            // keep cluster in pendingClusters
            if (!cluster.dirty) {
                this.pendingClusters[maxZoom].add(cluster);
                cluster.dirty = true;
            }

            if (keepTiming) MyTimer.startTimer();
            // only register update handler if this cluster is not newly inserted
            if (!this.insertedClusters[maxZoom].contains(cluster)) {
                // if this cluster is already registered as updated
                if (this.updatedClusters[maxZoom].contains(cluster)) {
                    // update the updateDelta to include this newly merged point
                    merge(cluster.updateDelta, c.getX(), c.getY(), c.numPoints);
                } else {
                    // whenever create a ClusterDelta, correct the numPoints
                    cluster.updateDelta = new ClusterDelta(c.getX(), c.getY(), 1);
                    this.updatedClusters[maxZoom].add(cluster);
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
        }
    }

    /**
     * Merge c2 into c1
     *   Note: c1 is at zoom level, one level above c2
     *
     * @param c1
     * @param c2
     * @param zoom
     */
    private void merge(Cluster c1, Cluster c2, int zoom) {
        if (c1 == null) return;

        //-DEBUG-//
        //System.out.println("Merge " + c2.getId() + ":[" + c2.numPoints + "] into cluster " + c1.getId() + ":[" + c1.numPoints + "] at level " + zoom + " ...");
        //-DEBUG-//

        if (keepTiming) MyTimer.startTimer();
        // delete c1 from clustersTree
        if (!c1.dirty) {
            this.clustersIndexes[zoom].delete(c1);
        }
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

        if (keepTiming) MyTimer.startTimer();
        // encode id if it's first time to be a cluster
        if (c1.numPoints == 0) {
            encodeId(c1, zoom);
        }

        // merge c2's coordinate into c1's centroid calculation
        merge(c1, c2.getX(), c2.getY(), c2.numPoints);

        // add c2 as c1's child
        c1.children.add(c2);
        c2.parent = c1;
        c2.parentId = c1.getId();
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("mergeCalculation", timing.get("mergeCalculation") + MyTimer.durationSeconds());

        // keep c1 in pendingClusters
        if (!c1.dirty) {
            this.pendingClusters[zoom].add(c1);
            c1.dirty = true;
        }

        if (keepTiming) MyTimer.startTimer();
        // only register update handler if c1 is not newly inserted
        if (!this.insertedClusters[zoom].contains(c1)) {
            // if this c1 is already registered as updated
            if (this.updatedClusters[zoom].contains(c1)) {
                // update c1's updateDelta to merge cluster c2
                merge(c1.updateDelta, c2.getX(), c2.getY(), c2.numPoints);
            } else {
                // whenever create a ClusterDelta, correct the numPoints
                c1.updateDelta = new ClusterDelta(c2.getX(), c2.getY(), c2.numPoints == 0 ? 1 : c2.numPoints);
                this.updatedClusters[zoom].add(c1);
            }
        }
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
    }

    /**
     * Split c2 from c1
     *   Note: c1 is at zoom level, one level above c2
     *
     * @param c1
     * @param c2
     * @param zoom
     */
    private void split(Cluster c1, Cluster c2, int zoom) {
        if (c1 == null) return;

        //-DEBUG-//
        //System.out.println("Split " + c2.getId() + ":[" + c2.numPoints + "] from cluster " + c1.getId() + ":[" + c1.numPoints + "] at level " + zoom + " ...");
        //-DEBUG-//

        // if c2 is c1's only child, delete c1 directly
        if (c1.children.size() == 1) {
            if (keepTiming) MyTimer.startTimer();
            this.deletedClusters[zoom].add(c1);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
        }
        // otherwise, subtract c2's weight from c1
        else {

            if (keepTiming) MyTimer.startTimer();
            // delete c1 from clustersTree
            if (!c1.dirty) {
                this.clustersIndexes[zoom].delete(c1);
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            if (keepTiming) MyTimer.startTimer();
            // split c2's coordinate from c1's centroid calculation
            split(c1, c2.getX(), c2.getY(), c2.numPoints);

            // remove c2 from c1's children
            c1.children.remove(c2);
            c2.parent = null;
            c2.parentId = -1;

            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("splitCalculation", timing.get("splitCalculation") + MyTimer.durationSeconds());

            // keep c1 in pendingClusters
            if (!c1.dirty) {
                this.pendingClusters[zoom].add(c1);
                c1.dirty = true;
            }

            if (keepTiming) MyTimer.startTimer();
            // only register update handler if c1 is not newly inserted
            if (!this.insertedClusters[zoom].contains(c1)) {
                // if this c1 is already registered as updated
                if (this.updatedClusters[zoom].contains(c1)) {
                    // update c1's updateDelta to split cluster c2
                    split(c1.updateDelta, c2.getX(), c2.getY(), c2.numPoints);
                }
                else {
                    // whenever create a ClusterDelta, correct the numPoints
                    c1.updateDelta = new ClusterDelta(c2.getX(), c2.getY(), c2.numPoints == 0 ? -1 : -c2.numPoints);
                    this.updatedClusters[zoom].add(c1);
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
        }
    }

    /**
     * Find list of clusters that should merge into c.parent
     *   Note: c is at zoom level
     *
     * @param c
     * @param zoom
     * @return
     */
    private List<Cluster> toMerge(Cluster c, int zoom) {
        // shifting at level zoom affects clustering results of level (zoom-1)
        double r = getRadius(zoom - 1);

        if (keepTiming) MyTimer.startTimer();
        List<Cluster> clusters = this.clustersIndexes[zoom].within(c, r);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("shift-rangeSearch", timing.get("shift-rangeSearch") + MyTimer.durationSeconds());

        clusters.removeAll(c.parent.children);
        // remove those:
        //    (1) already has a parent with smaller id of advocator than c.parent
        //    (2) that are advocators themselves
        for (Iterator<Cluster> iter = clusters.iterator(); iter.hasNext(); ) {
            Cluster curr = iter.next();
            if (isAdvocatorOfParent(curr)) {
                iter.remove();
                continue;
            }
            if (curr.parent != null) {
                Cluster currParent = curr.parent;
                if (currParent.advocator != null) {
                    if (currParent.advocator.getId() <= c.parent.advocator.getId()) {
                        iter.remove();
                    }
                }
            }
        }
        return clusters;
    }

    /**
     * Find list of clusters that should split from c.parent
     *   Note: c is at zoom level
     *
     * @param c
     * @param zoom
     * @return
     */
    private List<Cluster> toSplit(Cluster c, int zoom) {
        // shifting at level zoom affects clustering results of level (zoom-1)
        double r = getRadius(zoom - 1);

        List<Cluster> children = new ArrayList<>(c.parent.children);

        // remove those who are within r to c
        children.removeIf(child -> child.distanceTo(c) <= r);
        return children;
    }

    /**
     * Check whether cluster c is an advocator of its parent
     *
     * @param c
     * @return
     */
    private boolean isAdvocatorOfParent(Cluster c) {
        if (c.parent != null && c == c.parent.advocatorCluster)
            return true;
        return false;
    }

    /**
     * apply delta to cluster c
     *
     * @param c
     * @param delta
     * @param zoom
     */
    private void applyDelta(Cluster c, ClusterDelta delta, int zoom) {
        if (c == null) return;

        //-DEBUG-//
        //System.out.println("Apply delta [" + delta.numPoints + "](" + delta.x + "," + delta.y + ") into cluster " + c.getId() + ":[" + c.numPoints + "](" + c.getX() + "," + c.getY() + ") at level " + zoom + " ...");
        //-DEBUG-//

        if (keepTiming) MyTimer.startTimer();
        // delete c from clustersTree
        if (!c.dirty) {
            this.clustersIndexes[zoom].delete(c);
        }
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

        if (keepTiming) MyTimer.startTimer();
        // encode id if it's first time to be a cluster
        if (c.numPoints == 0) {
            encodeId(c, zoom);
        }

        // apply delta to c's centroid calculation
        update(c, delta);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("mergeCalculation", timing.get("mergeCalculation") + MyTimer.durationSeconds());

        // keep c in pendingClusters
        if (!c.dirty) {
            this.pendingClusters[zoom].add(c);
            c.dirty = true;
        }

        if (keepTiming) MyTimer.startTimer();
        // only register update handler if c is not newly inserted
        if (!this.insertedClusters[zoom].contains(c)) {
            // if this c is already registered as updated
            if (this.updatedClusters[zoom].contains(c)) {
                // combine delta into c's updateDelta
                combine(c.updateDelta, delta);
            }
            else {
                c.updateDelta = delta;
                this.updatedClusters[zoom].add(c);
            }
        }

        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
    }

    private void onUpdate(Cluster c, int zoom) {

        //-DEBUG-//
        //System.out.println("Updating cluster " + c.getId() + ":[" + c.numPoints + "], delta=" + c.updateDelta.numPoints + " at level " + zoom + " ...");
        //-DEBUG-//

        // propagate c's delta to c's parent
        applyDelta(c.parent, c.updateDelta, zoom - 1);
        c.updateDelta = null;
    }

    /**
     * onInsert handler for cluster c into zoom level
     *   Note: information of c itself is already up to date,
     *         grouping relationships at zoom level is also up to date,
     *         onInsert handler just wants to update the grouping relationships at zoom-1 level,
     *         right now, cluster c has no parent at zoom-1 level
     *
     * @param c
     * @param zoom
     */
    private void onInsert(Cluster c, int zoom) {
        //-DEBUG-//
        //System.out.println("reInserting " + c.getId() + ": [" + c.numPoints + "]...");
        //-DEBUG-//

        if (zoom == minZoom) return;
        double radius = getRadius(zoom - 1);

        if (keepTiming) MyTimer.startTimer();
        // Find all earlier advocators c can merge into
        List<Advocator> advocators = this.advocatorsIndexes[zoom - 1].within(c, radius);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("insert-rangeSearch", timing.get("insert-rangeSearch") + MyTimer.durationSeconds());

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {

            // create a parent cluster of c, and add it to zoom-1 level
            Cluster parent = createCluster(c.getX(), c.getY(), c.getId(), c.numPoints);
            parent.zoom = zoom - 1;
            parent.seq = c.seq;
            c.parentId = parent.getId();
            c.parent = parent;
            /** in SBiSuperCluster Class, use Cluster.children to store direct descendants pointers */
            parent.children.add(c);
            parent.advocatorCluster = c;

            Advocator newAdvocator = new Advocator(parent.getX(), parent.getY(), parent.seq);
            newAdvocator.cluster = parent;
            parent.advocator = newAdvocator;

            if (keepTiming) MyTimer.startTimer();
            this.advocatorsIndexes[zoom - 1].insert(newAdvocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

            this.advocatorClusters[zoom - 1].add(parent);

            if (keepTiming) MyTimer.startTimer();
            this.clustersIndexes[zoom - 1].insert(parent);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            if (keepTiming) MyTimer.startTimer();
            // register as newly inserted
            this.insertedClusters[zoom - 1].add(parent);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
        }
        // if earlier advocators' groups could be merged into
        else {

            if (keepTiming) MyTimer.startTimer();
            // find the earliest advocator
            Advocator earliestAdvocator = null;
            for (Advocator advocator: advocators) {
                if (earliestAdvocator == null) {
                    earliestAdvocator = advocator;
                }
                else if (earliestAdvocator.getId() > advocator.getId()) {
                    earliestAdvocator = advocator;
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("findEarliest", timing.get("findEarliest") + MyTimer.durationSeconds());

            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;

            merge(cluster, c, zoom - 1);
        }
    }

    /**
     * onDelete handler for cluster c from zoom level
     *   Note: information of c itself is already up to date,
     *         grouping relationships at zoom level is also up to date,
     *         delete just wants to update the grouping relationships at zoom-1 level
     *
     * @param c
     * @param zoom
     */
    private void onDelete(Cluster c, int zoom) {
        if (keepTiming) MyTimer.startTimer();
        this.advocatorsIndexes[zoom].delete(c.advocator);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

        this.advocatorClusters[zoom].remove(c);

        if (keepTiming) MyTimer.startTimer();
        this.clustersIndexes[zoom].delete(c);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

        // this cluster c has already updated its parent's delta by onUpdate handler,
        // now should remove it from updated sets to prevent it from shift handler.
        this.updatedClusters[zoom].remove(c);

        split(c.parent, c, zoom - 1);
    }

    /**
     * shift cluster c from its advocator "from"'s location to its current centroid location at zoom level
     *   Note: information of c itself is already up to date,
     *         grouping relationships at zoom level is also up to date,
     *         shift just wants to update the grouping relationships at zoom-1 level
     *
     * @param c
     * @param from
     * @param zoom
     */
    private void shift(Cluster c, Advocator from, int zoom) {
        if (zoom == minZoom) return;

        if (isAdvocatorOfParent(c)) {
            // Find list of clusters that should merge into c.parent
            List<Cluster> toMerge = toMerge(c, zoom);
            // Find list of clusters that should split from c.parent
            List<Cluster> toSplit = toSplit(c, zoom);

            if (keepTiming) MyTimer.startTimer();
            // shift the advocator "from"'s location to the centroid of cluster c
            this.advocatorsIndexes[zoom].delete(from);
            from.setX(c.getX());
            from.setY(c.getY());
            this.advocatorsIndexes[zoom].insert(from);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

            for (Cluster m: toMerge) {
                split(m.parent, m, zoom - 1);
                merge(c.parent, m, zoom - 1);

                if (keepTiming) MyTimer.startTimer();
                // remove cluster m from registered insert sets
                this.insertedClusters[zoom].remove(m);
                if (keepTiming) MyTimer.stopTimer();
                if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
            }

            for (Cluster s: toSplit) {
                split(c.parent, s, zoom - 1);

                if (keepTiming) MyTimer.startTimer();
                this.insertedClusters[zoom].add(s);
                if (keepTiming) MyTimer.stopTimer();
                if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
            }
        }
        else {
            // shifting at level zoom affects clustering results of level (zoom-1)
            double radius = getRadius(zoom - 1);
            // TODO - some times c.parent is null because of earlier shifted clusters absorbed updated clusters
            if (c.parent != null && c.parent.advocator.distanceTo(c) > radius) {
                split(c.parent, c, zoom - 1);

                if (keepTiming) MyTimer.startTimer();
                this.insertedClusters[zoom].add(c);
                if (keepTiming) MyTimer.stopTimer();
                if (keepTiming) timing.put("maintainSets", timing.get("maintainSets") + MyTimer.durationSeconds());
            }
        }
    }

    /**
     * Encode cluster c's Id by including zoom level information
     *
     * @param c
     * @param zoom
     */
    private void encodeId(Cluster c, int zoom) {
        c.setId((c.getId() << 5) + (zoom + 1));
    }

    /**
     * Merge given delta (dx, dy, dNumPoints) into cluster c
     *
     * @param c
     * @param dx
     * @param dy
     * @param dNumPoints
     */
    private void merge(Cluster c, double dx, double dy, int dNumPoints) {
        // Correct numPoints for delta
        dNumPoints = dNumPoints == 0? 1: dNumPoints;

        update(c, dx, dy, dNumPoints);
    }

    /**
     * Split given delta (dx, dy, dNumPoints) from cluster c
     *
     * @param c
     * @param dx
     * @param dy
     * @param dNumPoints
     */
    private void split(Cluster c, double dx, double dy, int dNumPoints) {
        // Correct numPoints for delta and make it negative
        dNumPoints = dNumPoints == 0? -1: -dNumPoints;

        update(c, dx, dy, dNumPoints);
    }

    /**
     * Update cluster c by given delta (dx, dy, dNumPoints)
     *
     * @param c
     * @param dx
     * @param dy
     * @param dNumPoints - should already be corrected
     */
    private void update(Cluster c, double dx, double dy, int dNumPoints) {

        // Correct numPoints for cluster c
        int numPoints = c.numPoints == 0? 1: c.numPoints;

        // apply (dx, dy, dNumPoints) into cluster c
        double wx = c.getX() * numPoints + dx * (dNumPoints == 0? 1: dNumPoints);
        double wy = c.getY() * numPoints + dy * (dNumPoints == 0? 1: dNumPoints);
        // TODO - for update cluster with delta, it should not be possible the numPoints be 0.
        numPoints = numPoints + dNumPoints;

        c.setX(wx / numPoints);
        c.setY(wy / numPoints);
        c.numPoints = numPoints;
    }

    /**
     * Update cluster c by given delta
     *
     * @param c
     * @param delta - should already be corrected
     */
    private void update(Cluster c, ClusterDelta delta) {
        update(c, delta.x, delta.y, delta.numPoints);
    }

    /**
     * Merge given delta (dx, dy, dNumPoints) into delta
     *
     * @param delta - should already be corrected
     * @param dx - positive
     * @param dy - positive
     * @param dNumPoints - 0 or positive
     */
    private void merge(ClusterDelta delta, double dx, double dy, int dNumPoints) {
        // Correct numPoints for delta
        dNumPoints = dNumPoints == 0? 1: dNumPoints;

        update(delta, dx, dy, dNumPoints);
    }

    /**
     * Split given delta (dx, dy, dNumPoints) from delta
     *
     * @param delta - should already be corrected
     * @param dx - positive
     * @param dy - positive
     * @param dNumPoints - 0 or positive
     */
    private void split(ClusterDelta delta, double dx, double dy, int dNumPoints) {
        // Correct numPoints for delta
        dNumPoints = dNumPoints == 0? -1: -dNumPoints;

        update(delta, dx, dy, dNumPoints);
    }

    /**
     * Update delta by apply extra delta (dx, dy, dNumPoints)
     *
     * @param delta - should already be corrected
     * @param dx
     * @param dy
     * @param dNumPoints - should already be corrected
     */
    private void update(ClusterDelta delta, double dx, double dy, int dNumPoints) {
        // apply (dx, dy, dNumPoints) into delta
        double wx = delta.x * (delta.numPoints == 0? 1: delta.numPoints) + dx * dNumPoints;
        double wy = delta.y * (delta.numPoints == 0? 1: delta.numPoints) + dy * dNumPoints;
        delta.numPoints = delta.numPoints + dNumPoints;
        delta.x = wx / (delta.numPoints == 0? 1: delta.numPoints);
        delta.y = wy / (delta.numPoints == 0? 1: delta.numPoints);
    }

    /**
     * Combine delta1 and delta2 into delta1
     *
     * @param delta1 - should already be corrected
     * @param delta2 - should already be corrected
     */
    private void combine(ClusterDelta delta1, ClusterDelta delta2) {
        double x = delta1.x * (delta1.numPoints == 0? 1: delta1.numPoints) + delta2.x * (delta2.numPoints == 0? 1: delta2.numPoints);
        double y = delta1.y * (delta1.numPoints == 0? 1: delta1.numPoints) + delta2.y * (delta2.numPoints == 0? 1: delta2.numPoints);
        int numPoints = delta1.numPoints + delta2.numPoints;
        delta1.x = x / (numPoints == 0? 1: numPoints);
        delta1.y = y / (numPoints == 0? 1: numPoints);
        delta1.numPoints = numPoints;
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
        System.out.println("    [find earliest] " + timing.get("findEarliest") + " seconds");
        System.out.println("    [insert range search] " + timing.get("insert-rangeSearch") + " seconds");
        System.out.println("    [shift range search] " + timing.get("shift-rangeSearch") + " seconds");
        System.out.println("    [merge calculation] " + timing.get("mergeCalculation") + " seconds");
        System.out.println("    [split calculation] " + timing.get("splitCalculation") + " seconds");
        System.out.println("    [maintain advocator tree] " + timing.get("maintainAdvocatorTree") + " seconds");
        System.out.println("    [maintain cluster tree] " + timing.get("maintainClusterTree") + " seconds");
        System.out.println("    [maintain event sets] " + timing.get("maintainSets") + " seconds");
    }

    private String printCluster(Cluster c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getId() + ":[" + c.numPoints + "]");
        return sb.toString();
    }
}
