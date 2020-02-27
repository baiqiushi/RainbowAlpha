package algorithms;

import model.Advocator;
import model.Cluster;
import model.PointTuple;
import util.*;

import java.util.*;

public class BiSuperCluster extends SuperCluster {
    // advocators at each level
    I2DIndex<Advocator>[] advocatorsIndexes;
    // clusters at each level
    List<Cluster>[] advocatorClusters;
    // clusters at each level as a tree
    I2DIndex<Cluster>[] clustersIndexes;
    // clusters at each level that are pending for insert into tree
    Queue<Cluster>[] pendingClusters;

    int pointIdSeq;
    int advocatorSeq;
    int totalShiftCount = 0;

    double miu = 0.5;

    String indexType; // KDTree / GridIndex

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public BiSuperCluster(int _minZoom, int _maxZoom, String _indexType, boolean _analysis) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.indexType = _indexType;
        this.advocatorsIndexes = IndexCreator.createIndexArray(indexType, maxZoom + 1);
        this.advocatorClusters = new List[maxZoom + 1];
        this.clustersIndexes = IndexCreator.createIndexArray(indexType, maxZoom + 1);
        this.pendingClusters = new Queue[maxZoom + 1];
        this.pointIdSeq = 0;
        this.advocatorSeq = 0;

        for (int z = 0; z <= maxZoom; z ++) {
            this.advocatorsIndexes[z] = IndexCreator.createIndex(indexType, getRadius(z));
            this.advocatorClusters[z] = new ArrayList<>();
            this.clustersIndexes[z] = IndexCreator.createIndex(indexType, getRadius(z - 1 >= 0? z - 1: 0));
            this.pendingClusters[z] = new LinkedList<>();
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
        }

        MyMemory.printMemory();
    }

    private void insertPointClusters(double[][] points) {
        for (int i = 0; i < points.length; i ++) {
            insert(createPointCluster(points[i][0], points[i][1], this.pointIdSeq ++), maxZoom);
        }
    }

    private void insertPointClusters(List<PointTuple> points) {
        for (int i = 0; i < points.size(); i ++) {
            insert(createPointCluster(points.get(i).getX(), points.get(i).getY(), this.pointIdSeq ++), maxZoom);
        }
    }

    private void shiftClusters() {
//        //-DEBUG-//
//        System.out.println("==== Before starting shift operations ====");
//        for (int z = maxZoom; z >= minZoom; z --) {
//            Cluster[] allClusters = getClusters(-180, -90, 180, 90, z);
//            int totalPointsCount = 0;
//            for (int i = 0; i < allClusters.length; i ++) {
//                totalPointsCount += allClusters[i].numPoints == 0 ? 1 : allClusters[i].numPoints;
//            }
//            if (totalPointsCount != this.totalNumberOfPoints) {
//                System.out.println("[Error] zoom level [" + z + "] has totally ====> " + totalPointsCount + " <==== clusters.");
//            }
//        }
//        //-DEBUG-//

        int shiftCount = 0;
        // shift clusters with significant relocation bottom-up
        for (int z = maxZoom; z > minZoom; z --) {

            if (keepTiming) MyTimer.startTimer();
            // update clustersTree for level z
            Cluster pending;
            while ((pending = this.pendingClusters[z].poll()) != null) {
                this.clustersIndexes[z].insert(pending);
                pending.dirty = false;
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            // shifting at level z affects clustering results of level (z-1)
            double r = getRadius(z - 1);

            // for current level, shift clusters one by one
            int shiftCountLevel = 0;
            for (Cluster cluster: this.advocatorClusters[z]) {
                Advocator advocator = cluster.advocator;
                if (cluster.distanceTo(advocator) > miu * r) {
                    shiftCountLevel ++;
                    shift(cluster, advocator, z);
                }
            }
            System.out.println("==== zoom level [" + z + "] shifted " + shiftCountLevel + " clusters.");
            shiftCount += shiftCountLevel;

//            //-DEBUG-//
//            System.out.println("==== After shifted clusters for level " + z + " ====");
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
//                            sb.append(child.getId() + ":[" + child.numPoints + "]->" + child.parent.getId() + ", ");
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
//                System.out.println("[Error] zoom level [" + (z - 1) + "] has totally ====> " + totalPointsCount + " <==== clusters, while correct = " + this.totalNumberOfPoints);
//            }
//            //-DEBUG-//
        }
        this.totalShiftCount += shiftCount;

        System.out.println("==== All shifts of this batch are done! ====");
        System.out.println("Max zoom level clusters # = " + this.advocatorClusters[maxZoom].size());
        System.out.println("This batch shift clusters: " + shiftCount);
        System.out.println("Total shift clusters: " + this.totalShiftCount);
    }

    public void load(double[][] points) {
        System.out.println("Batch incremental SuperCluster loading " + points.length + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.length;
        System.out.println("Total # of clusters should be " + totalNumberOfPoints + " now.");

        // insert clusters to max zoom level one by one
        insertPointClusters(points);

        // shift clusters that are necessary
        shiftClusters();

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("Batch incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();
    }

    public void load(List<PointTuple> points) {
        System.out.println("Batch incremental SuperCluster loading " + points.size() + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.size();
        System.out.println("Total # of clusters should be " + totalNumberOfPoints + " now.");

        // insert clusters to max zoom level one by one
        insertPointClusters(points);

        // shift clusters that are necessary
        shiftClusters();

        long end = System.nanoTime();
        if (keepTiming) timing.put("total", timing.get("total") + (double) (end - start) / 1000000000.0);
        System.out.println("Batch incremental SuperCluster loading is done!");
        System.out.println("Clustering time: " + (double) (end - start) / 1000000000.0 + " seconds.");
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

    private void insert(Cluster c, int zoom) {

        //-DEBUG-//
        //System.out.println("insert " + c.getId() + ":[" + c.numPoints + "] into level " + zoom + " ...");
        //-DEBUG-//

        double radius = getRadius(zoom);

        if (keepTiming) MyTimer.startTimer();
        // Find all earlier advocators c can merge into
        I2DIndex<Advocator> advocatorsIndex = this.advocatorsIndexes[zoom];
        List<Advocator> advocators = advocatorsIndex.within(c, radius);
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("insert-rangeSearch", timing.get("insert-rangeSearch") + MyTimer.durationSeconds());

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {
            Advocator newAdvocator = new Advocator(c.getX(), c.getY(), advocatorSeq ++);
            newAdvocator.cluster = c;
            c.advocator = newAdvocator;

            if (keepTiming) MyTimer.startTimer();
            advocatorsIndex.insert(newAdvocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

            this.advocatorClusters[zoom].add(c);

            if (keepTiming) MyTimer.startTimer();
            this.clustersIndexes[zoom].insert(c);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            // insert this cluster into lower level
            if (zoom >= 1) {
                Cluster parent = createCluster(c.getX(), c.getY(), c.getId(), c.numPoints);
                c.parentId = parent.getId();
                c.parent = parent;
                /** in BiSuperCluster Class, use Cluster.children to store direct descendants pointers */
                parent.children.add(c);
                parent.advocatorCluster = c;
                insert(parent, zoom - 1);
            }
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
                this.clustersIndexes[zoom].delete(cluster);
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

            // merge c's children into cluster's children too
            if (zoom < maxZoom) {
                cluster.children.addAll(c.children);
                for (Cluster child: c.children) {
                    child.parentId = cluster.getId();
                    child.parent = cluster;
                }
            }
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("mergeCalculation", timing.get("mergeCalculation") + MyTimer.durationSeconds());

            // keep cluster in pendingClusters
            if (!cluster.dirty) {
                this.pendingClusters[zoom].add(cluster);
                cluster.dirty = true;
            }

            // merge c into cluster.parent recursively
            merge(cluster.parent, c, zoom - 1);
        }
    }

    /**
     * Merge c2 into c1, and recursively merge c2 into c1.parent
     *
     * @param c1
     * @param c2
     * @param zoom
     */
    private void merge(Cluster c1, Cluster c2, int zoom) {
        if (c1 == null) return;

        //-DEBUG-//
        //System.out.println("Merge cluster " + c2.getId() + ":[" + c2.numPoints + "] into " + c1.getId() + ":[" + c1.numPoints + "] at level " + zoom + " ...");
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
        if (keepTiming) MyTimer.stopTimer();
        if (keepTiming) timing.put("mergeCalculation", timing.get("mergeCalculation") + MyTimer.durationSeconds());

        // keep c1 in pendingClusters
        if (!c1.dirty) {
            this.pendingClusters[zoom].add(c1);
            c1.dirty = true;
        }

        merge(c1.parent, c2, zoom - 1);
    }

    /**
     * Split c2 from c1, zoom is the level of c1,
     * and recursively split c2 from c1.parent
     *
     * @param c1
     * @param c2
     * @param zoom
     */
    private void split(Cluster c1, Cluster c2, int zoom) {
        if (c1 == null) return;

        //-DEBUG-//
        //System.out.println("Split cluster " + c2.getId() + ":[" + c2.numPoints + "] from " + c1.getId() + ":[" + c1.numPoints + "] at level " + zoom + " ...");
        //-DEBUG-//

        // if c2 is c1's only child, delete c1 directly
        if (c1.children.size() == 1 && c1.children.contains(c2)) {

            if (keepTiming) MyTimer.startTimer();
            this.advocatorsIndexes[zoom].delete(c1.advocator);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

            this.advocatorClusters[zoom].remove(c1);

            if (keepTiming) MyTimer.startTimer();
            this.clustersIndexes[zoom].delete(c1);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainClusterTree", timing.get("maintainClusterTree") + MyTimer.durationSeconds());

            c1.children.remove(c2);
            c2.parent = null;
            c2.parentId = -1;

            split(c1.parent, c1, zoom - 1);
        }
        // otherwise, subtract c2's weight from c1 and recurse
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
            // remove c2 from c1's children, if it is
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

            split(c1.parent, c2, zoom - 1);
        }
    }

    /**
     * Find list of clusters that should merge into c.parent
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

    private void shift(Cluster c, Advocator from, int zoom) {

        //-DEBUG-//
        //System.out.println("Shift cluster " + c.getId() + ":[" + c.numPoints + "] at level " + zoom + " ...");
        //-DEBUG-//

        if (isAdvocatorOfParent(c)) {
            // Find list of clusters that should merge into c.parent
            List<Cluster> toMerge = toMerge(c, zoom);
            // Find list of clusters that should split from c.parent
            List<Cluster> toSplit = toSplit(c, zoom);

            if (keepTiming) MyTimer.startTimer();
            // shift the advocator "from"'s location to the centroid of cluster c
            //TODO - use a index structure that support update location to handle the position shift
            this.advocatorsIndexes[zoom].delete(from);
            from.setX(c.getX());
            from.setY(c.getY());
            this.advocatorsIndexes[zoom].insert(from);
            if (keepTiming) MyTimer.stopTimer();
            if (keepTiming) timing.put("maintainAdvocatorTree", timing.get("maintainAdvocatorTree") + MyTimer.durationSeconds());

            for (Cluster m: toMerge) {
                split(m.parent, m, zoom - 1);
                c.parent.children.add(m);
                m.parent = c.parent;
                m.parentId = c.parentId;
                merge(c.parent, m, zoom - 1);
            }

            for (Cluster s: toSplit) {
                split(c.parent, s, zoom - 1);

                Cluster parent = createCluster(s.getX(), s.getY(), s.getId(), s.numPoints);
                s.parentId = parent.getId();
                s.parent = parent;
                parent.children.add(s);
                parent.advocatorCluster = s;

                insert(parent, zoom - 1);
            }
        }
        else {
            // shifting at level zoom affects clustering results of level (zoom-1)
            double radius = getRadius(zoom - 1);
            if (c.parent.advocator.distanceTo(c) > radius) {
                split(c.parent, c, zoom - 1);

                Cluster parent = createCluster(c.getX(), c.getY(), c.getId(), c.numPoints);
                c.parentId = parent.getId();
                c.parent = parent;
                parent.children.add(c);
                parent.advocatorCluster = c;

                insert(parent, zoom - 1);
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

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
        System.out.println("    [find earliest] " + timing.get("findEarliest") + " seconds");
        System.out.println("    [insert range search] " + timing.get("insert-rangeSearch") + " seconds");
        System.out.println("    [shift range search] " + timing.get("shift-rangeSearch") + " seconds");
        System.out.println("    [merge calculation] " + timing.get("mergeCalculation") + " seconds");
        System.out.println("    [split calculation] " + timing.get("splitCalculation") + " seconds");
        System.out.println("    [maintain advocator tree] " + timing.get("maintainAdvocatorTree") + " seconds");
        System.out.println("    [maintain cluster tree] " + timing.get("maintainClusterTree") + " seconds");
    }
}
