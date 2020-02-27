package algorithms;

import model.Advocator;
import model.Cluster;
import model.ClusterDelta;
import model.PointTuple;
import util.Flags;
import util.I2DIndex;
import util.IndexCreator;
import util.MyMemory;

import java.util.*;

/**
 * Strict Batch incremental SuperCluster
 *
 * Use Level Batch incremental SuperCluster framework,
 * consider more complex situations to make the clustering result
 * consistent with the original SuperCluster algorithm if input order is the same.
 *
 * TODO - onInsert to rob other existing clusters unfinished
 *      - bugs to be fixed for those duplicate flagged clusters in the Queue
 */
public class SBiSuperCluster extends SuperCluster {
    // advocators at each level
    I2DIndex<Advocator>[] advocatorsIndexes;
    // clusters at each level
    List<Cluster>[] advocatorClusters;
    // clusters at each level as a tree
    I2DIndex<Cluster>[] clustersIndexes;

    // clusters at each level that are pending for insert into tree
    Queue<Cluster>[] pendingClusters;

    // clusters at each level that are flagged for handling insert, update and delete operations
    PriorityQueue<Cluster>[] flaggedClusters;

    int pointIdSeq;
    int pointSeqSeq;
    int totalInsertCount = 0;
    int totalUpdateCount = 0;
    int totalShiftCount = 0;
    int totalDeleteCount= 0;

    double miu = 0.0;

    String indexType; // KDTree / GridIndex

    public SBiSuperCluster(int _minZoom, int _maxZoom, String _indexType, boolean _analysis) {
        this.minZoom = _minZoom;
        this.maxZoom = _maxZoom;
        this.indexType = _indexType;
        this.advocatorsIndexes = IndexCreator.createIndexArray(indexType, maxZoom + 1);
        this.advocatorClusters = new List[maxZoom + 1];
        this.clustersIndexes = IndexCreator.createIndexArray(indexType, maxZoom + 1);
        this.pendingClusters = new Queue[maxZoom + 1];
        this.flaggedClusters = new PriorityQueue[maxZoom + 1];
        this.pointIdSeq = 0;
        this.pointSeqSeq = 0;

        for (int z = 0; z <= maxZoom; z ++) {
            this.advocatorsIndexes[z] = IndexCreator.createIndex(indexType, getRadius(z));
            this.advocatorClusters[z] = new ArrayList<>();
            this.clustersIndexes[z] = IndexCreator.createIndex(indexType, getRadius(z - 1 >= 0? z - 1: 0));
            this.pendingClusters[z] = new LinkedList<>();
            // make sure a cluster with smaller seq will be returned first
            this.flaggedClusters[z] = new PriorityQueue<>(new Comparator<Cluster>() {
                @Override
                public int compare(Cluster o1, Cluster o2) {
                    return o1.seq - o2.seq;
                }
            });
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

            // update clustersTree for level z
            Cluster pending;
            while ((pending = this.pendingClusters[z].poll()) != null) {
                this.clustersIndexes[z].insert(pending);
            }

            // shifting at level z affects clustering results of level (z-1)
            double r = getRadius(z - 1);

            //-DEBUG-//
//            System.out.println("-------------------------------------------");
//            System.out.println("Priority Queue of level [" + z + "]:");
//            for (Cluster fc: this.flaggedClusters[z]) {
//                System.out.println(fc.flag.name() + ": " + fc.seq + ": " + fc.getId() + ": [" + fc.numPoints + "]" + (fc.flag==Flags.UPDATED? " update delta="+fc.updateDelta.numPoints: ""));
//            }
//            System.out.println("-------------------------------------------");
            //-DEBUG-//

            int insertCountLevel = 0;
            int updateCountLevel = 0;
            int shiftCountLevel = 0;
            int deleteCountLevel = 0;
            // handle all changes at z level to form clusters at (z-1) level
            Cluster flaggedCluster;
            while ((flaggedCluster = this.flaggedClusters[z].poll()) != null) {

                if (flaggedCluster.flag == Flags.INSERTED) {
                    onInsert(flaggedCluster, z);
                    insertCountLevel ++;
                }

                if (flaggedCluster.flag == Flags.UPDATED) {
                    updateCountLevel ++;
                    onUpdate(flaggedCluster, z);

                    if (flaggedCluster.distanceTo(flaggedCluster.advocator) > miu * r) {
                        shift(flaggedCluster, flaggedCluster.advocator, z);
                        shiftCountLevel ++;
                    }
                }

                if (flaggedCluster.flag == Flags.DELETED) {
                    onDelete(flaggedCluster, z);
                    deleteCountLevel ++;
                }
            }

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
        System.out.println("Strict Batch incremental SuperCluster loading " + points.length + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.length;
        System.out.println("Total # of clusters should be " + totalNumberOfPoints + " now.");

        // insert clusters to max zoom level one by one
        insertPointClusters(points);

        // build hierarchy
        buildHierarchy();

        long end = System.nanoTime();
        System.out.println("[SBiSC] Strict Batch incremental SuperCluster loading is done!");
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

        MyMemory.printMemory();
    }

    public void load(List<PointTuple> points) {
        System.out.println("Strict Batch incremental SuperCluster loading " + points.size() + " clusters ... ...");
        long start = System.nanoTime();

        this.totalNumberOfPoints += points.size();
        System.out.println("Total # of clusters should be " + totalNumberOfPoints + " now.");

        // insert clusters to max zoom level one by one
        insertPointClusters(points);

        // build hierarchy
        buildHierarchy();

        long end = System.nanoTime();
        System.out.println("[SBiSC] Strict Batch incremental SuperCluster loading is done!");
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

        MyMemory.printMemory();
    }

    protected Cluster createPointCluster(double _x, double _y, int _id, int _seq) {
        Cluster c = new Cluster(lngX(_x), latY(_y), _id);
        c.seq = _seq;
        return c;
    }

    /**
     * insert point cluster c into maxZoom level
     *   Note: c is always one point itself
     */
    private void insert(Cluster c) {
        double radius = getRadius(maxZoom);

        // Find all earlier advocators c can merge into
        List<Advocator> advocators = this.advocatorsIndexes[maxZoom].within(c, radius);

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {
            Advocator newAdvocator = new Advocator(c.getX(), c.getY(), c.seq);
            newAdvocator.cluster = c;
            c.advocator = newAdvocator;

            this.advocatorsIndexes[maxZoom].insert(newAdvocator);

            this.advocatorClusters[maxZoom].add(c);

            this.clustersIndexes[maxZoom].insert(c);

            c.flag = Flags.INSERTED;
            if (!this.flaggedClusters[maxZoom].contains(c)) // TODO - this case should not happen at all
                this.flaggedClusters[maxZoom].add(c);
        }
        // if earlier advocators' groups could be merged into
        else {
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

            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;

            // delete cluster from clustersTree
            if (!cluster.dirty) {
                this.clustersIndexes[maxZoom].delete(cluster);
            }

            // encode id if it's first time to be a cluster
            if (cluster.numPoints == 0) {
                encodeId(cluster, maxZoom);
            }

            // merge c's coordinate into cluster's centroid calculation
            merge(cluster, c.getX(), c.getY(), c.numPoints);

            // keep cluster in pendingClusters
            if (!cluster.dirty) {
                this.pendingClusters[maxZoom].add(cluster);
                cluster.dirty = true;
            }

            // if this cluster is already flagged by earlier operations
            if (this.flaggedClusters[maxZoom].contains(cluster)) {
                // and if cluster has merged some other clusters
                if (cluster.flag == Flags.UPDATED) {
                    // update the updateDelta to include this newly merged point
                    merge(cluster.updateDelta, c.getX(), c.getY(), c.numPoints);
                }
            }
            else {
                cluster.flag = Flags.UPDATED;
                // whenever create a ClusterDelta, correct the numPoints
                cluster.updateDelta = new ClusterDelta(c.getX(), c.getY(), 1);
                this.flaggedClusters[maxZoom].add(cluster);
            }
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

        // delete c1 from clustersTree
        if (!c1.dirty) {
            this.clustersIndexes[zoom].delete(c1);
        }

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

        // keep c1 in pendingClusters
        if (!c1.dirty) {
            this.pendingClusters[zoom].add(c1);
            c1.dirty = true;
        }

        // if this c1 is already flagged by earlier operations
        if (this.flaggedClusters[zoom].contains(c1)) {
            // if c1 has been flagged as UPDATE
            if (c1.flag == Flags.UPDATED) {
                // update c1's updateDelta to merge cluster c2
                merge(c1.updateDelta, c2.getX(), c2.getY(), c2.numPoints);
            }
            // TODO - if c1 has been flagged as INSERT/DELETE, should do nothing?
        }
        else {
            c1.flag = Flags.UPDATED;
            // whenever create a ClusterDelta, correct the numPoints
            c1.updateDelta = new ClusterDelta(c2.getX(), c2.getY(), c2.numPoints == 0? 1: c2.numPoints);
            this.flaggedClusters[zoom].add(c1);
        }
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
            // TODO - verify this case
            c1.flag = Flags.DELETED;
            if (!this.flaggedClusters[zoom].contains(c1))
                this.flaggedClusters[zoom].add(c1);
        }
        // otherwise, subtract c2's weight from c1
        else {

            // delete c1 from clustersTree
            if (!c1.dirty) {
                this.clustersIndexes[zoom].delete(c1);
            }

            // split c2's coordinate from c1's centroid calculation
            split(c1, c2.getX(), c2.getY(), c2.numPoints);

            // remove c2 from c1's children
            c1.children.remove(c2);
            c2.parent = null;
            c2.parentId = -1;

            // keep c1 in pendingClusters
            if (!c1.dirty) {
                this.pendingClusters[zoom].add(c1);
                c1.dirty = true;
            }

            // if this c1 is already flagged by earlier operations
            if (this.flaggedClusters[zoom].contains(c1)) {
                // if c1 has been flagged as UPDATE
                if (c1.flag == Flags.UPDATED) {
                    // update c1's updateDelta to split cluster c2
                    split(c1.updateDelta, c2.getX(), c2.getY(), c2.numPoints);
                }
                // TODO - if c1 has been flagged as INSERT/DELETE, should do nothing?
            }
            else {
                c1.flag = Flags.UPDATED;
                // whenever create a ClusterDelta, correct the numPoints
                c1.updateDelta = new ClusterDelta(c2.getX(), c2.getY(), c2.numPoints == 0? -1: -c2.numPoints);
                this.flaggedClusters[zoom].add(c1);
            }
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

        List<Cluster> clusters = this.clustersIndexes[zoom].within(c, r);

        clusters.removeAll(c.parent.children);
        // remove those already has a parent with smaller id of advocator than c.parent
        for (Iterator<Cluster> iter = clusters.iterator(); iter.hasNext(); ) {
            Cluster curr = iter.next();
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

        List<Cluster> clusters = this.clustersIndexes[zoom].within(c, r);

        List<Cluster> children = new ArrayList<>(c.parent.children);

        children.removeAll(clusters);
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

        // delete c from clustersTree
        if (!c.dirty) {
            this.clustersIndexes[zoom].delete(c);
        }

        // encode id if it's first time to be a cluster
        if (c.numPoints == 0) {
            encodeId(c, zoom);
        }

        // apply delta to c's centroid calculation
        update(c, delta);

        // keep c in pendingClusters
        if (!c.dirty) {
            this.pendingClusters[zoom].add(c);
            c.dirty = true;
        }

        //-DEBUG-//
        //System.out.println("Updated cluster is " + c.getId() + ":[" + c.numPoints + "](" + c.getX() + "," + c.getY() + ")");
        //-DEBUG-//

        // if this c is already flagged by earlier operations
        if (this.flaggedClusters[zoom].contains(c)) {
            // if c has been flagged as UPDATE
            if (c.flag == Flags.UPDATED) {
                combine(c.updateDelta, delta);
            }
            // TODO - if c has been flagged as INSERT/DELETE, should do nothing?
        }
        else {
            c.flag = Flags.UPDATED;
            c.updateDelta = delta;
            this.flaggedClusters[zoom].add(c);
        }
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

        // Find all earlier advocators c can merge into
        List<Advocator> advocators = this.advocatorsIndexes[zoom - 1].within(c, radius);

        // if no group could be merged into, become a new Advocator itself
        if (advocators.isEmpty()) {

            // create a parent cluster of c, and add it to zoom-1 level
            Cluster parent = createCluster(c.getX(), c.getY(), c.getId(), c.numPoints);
            parent.seq = c.seq;
            c.parentId = parent.getId();
            c.parent = parent;
            /** in SBiSuperCluster Class, use Cluster.children to store direct descendants pointers */
            parent.children.add(c);
            parent.advocatorCluster = c;

            Advocator newAdvocator = new Advocator(parent.getX(), parent.getY(), parent.seq);
            newAdvocator.cluster = parent;
            parent.advocator = newAdvocator;

            this.advocatorsIndexes[zoom - 1].insert(newAdvocator);

            this.advocatorClusters[zoom - 1].add(parent);

            this.clustersIndexes[zoom - 1].insert(parent);

//            // Find list of clusters that should merge into c.parent
//            List<Cluster> toMerge = toMerge(c, zoom);
//
//            //-DEBUG-//
//            if (!toMerge.isEmpty()) {
//                StringBuilder sb = new StringBuilder();
//                for (Cluster tm: toMerge) {
//                    sb.append(tm.getId() + ":[" + tm.numPoints + "],");
//                }
//                System.out.println("toMerge :" + sb.toString());
//            }
//            //-DEBUG-//
//
//            for (Cluster m: toMerge) {
//                if (isAdvocatorOfParent(m)) {
//                    // break m.parent cluster
//                    // flag all children (except m) of m.parent as “inserted”
//                    m.parent.children.remove(m);
//                    for (Cluster sibling: m.parent.children) {
//
//                        if (flaggedClusters[zoom].contains(sibling)) {
//                            // TODO - handle existing cases
//                        }
//                        else {
//                            sibling.flag = Flags.INSERTED;
//                            this.flaggedClusters[zoom].add(sibling);
//                        }
//                    }
//
//                    if (this.flaggedClusters[zoom - 1].contains(m.parent)) {
//                        // TODO - handle existing cases
//                    }
//                    else {
//                        m.parent.flag = Flags.DELETED;
//                        this.flaggedClusters[zoom - 1].add(m.parent);
//                    }
//                    merge(c.parent, m, zoom - 1);
//                }
//                else {
//                    split(m.parent, m, zoom - 1);
//                    merge(c.parent, m, zoom - 1);
//                }
//                // remove cluster m from queue of clusters to be processed
//                if (this.flaggedClusters[zoom].contains(m))
//                    this.flaggedClusters[zoom].remove(m);
//            }


            if (this.flaggedClusters[zoom - 1].contains(parent)) {
                // TODO - handle existing cases
                //parent.flag = Flags.INSERTED;
            }
            else {
                parent.flag = Flags.INSERTED;
                this.flaggedClusters[zoom - 1].add(parent);
            }
        }
        // if earlier advocators' groups could be merged into
        else {
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

            // merge into earliest advocator's group
            Cluster cluster = earliestAdvocator.cluster;

            merge(cluster, c, zoom - 1);
        }

        c.flag = Flags.NONE;
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
        this.advocatorsIndexes[zoom].delete(c.advocator);
        this.advocatorClusters[zoom].remove(c);
        this.clustersIndexes[zoom].delete(c);
        c.flag = Flags.NONE;

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
        // shifting at level zoom affects clustering results of level (zoom-1)
        double radius = getRadius(zoom - 1);

        if (isAdvocatorOfParent(c)) {
            // Find all earlier advocators c can merge into
            List<Advocator> advocators = this.advocatorsIndexes[zoom - 1].within(c, radius);

            // if no group could be merged into, merge other clusters or split children clusters
            if (advocators.isEmpty()) {
                List<Cluster> toMerge = toMerge(c, zoom);
                List<Cluster> toSplit = toSplit(c, zoom);
                // shift the advocator "from"'s location to the centroid of cluster c
                this.advocatorsIndexes[zoom].delete(from);
                from.setX(c.getX());
                from.setY(c.getY());
                this.advocatorsIndexes[zoom].insert(from);

                for (Cluster m: toMerge) {
                    if (isAdvocatorOfParent(m)) {
                        // break m.parent cluster
                        // flag all children (except m) of m.parent as “inserted”
                        m.parent.children.remove(m);
                        for (Cluster sibling: m.parent.children) {

                            if (this.flaggedClusters[zoom].contains(sibling)) {
                                // TODO - handle existing cases
                            }
                            else {
                                sibling.flag = Flags.INSERTED;
                                this.flaggedClusters[zoom].add(sibling);
                            }
                        }

                        if (this.flaggedClusters[zoom - 1].contains(m.parent)) {
                            // TODO - handle existing cases
                        }
                        else {
                            m.parent.flag = Flags.DELETED;
                            this.flaggedClusters[zoom - 1].add(m.parent);
                        }
                        merge(c.parent, m, zoom - 1);
                    }
                    else {
                        split(m.parent, m, zoom - 1);
                        merge(c.parent, m, zoom - 1);
                    }
                    // remove cluster m from queue of clusters to be processed
                    if (this.flaggedClusters[zoom].contains(m))
                        this.flaggedClusters[zoom].remove(m);
                }

                for (Cluster s: toSplit) {
                    split(c.parent, s, zoom - 1);

                    if (this.flaggedClusters[zoom].contains(s)) {
                        // TODO - handle existing cases
                    }
                    else {
                        s.flag = Flags.INSERTED;
                        this.flaggedClusters[zoom].add(s);
                    }
                }
            }

            c.flag = Flags.NONE;
        }
        else {
            if (c.parent.advocator.distanceTo(c) > radius) {
                split(c.parent, c, zoom - 1);

                if (this.flaggedClusters[zoom].contains(c)) {
                    // TODO - handle existing cases
                }
                else {
                    c.flag = Flags.INSERTED;
                    this.flaggedClusters[zoom].add(c);
                }
            }
            else {
                c.flag = Flags.NONE;
            }
        }
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
}
