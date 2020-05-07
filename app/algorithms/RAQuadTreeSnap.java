package algorithms;

import javafx.util.Pair;
import model.Point;
import model.Query;
import util.*;
import java.util.*;

import static util.Mercator.*;

public class RAQuadTreeSnap implements IAlgorithm {

    public static double highestLevelNodeDimension;
    // resolution of each node (similar to a tile in map systems), e.g. 512
    public static int oneNodeResolution;

    public class QuadTree {
        // Store count of the sub-tree
        public int count;
        // Store count for each level in the sub-tree
        public int[] counts;
        public Point sample;

        // children
        public QuadTree northWest;
        public QuadTree northEast;
        public QuadTree southWest;
        public QuadTree southEast;

        public QuadTree() {
            this.count = 0;
            this.counts = new int[Constants.MAX_ZOOM + 1];
        }

        public boolean containsPoint(double cX, double cY, double halfDimension, Point point) {
            if (point.getX() >= (cX - halfDimension)
                    && point.getY() >= (cY - halfDimension)
                    && point.getX() < (cX + halfDimension)
                    && point.getY() < (cY + halfDimension)) {
                return true;
            }
            else {
                return false;
            }
        }

        public boolean intersectsBBox(double c1X, double c1Y, double halfDimension1,
                                      double c2X, double c2Y, double halfWidth2, double halfHeight2) {
            // bbox 1
            double left = c1X - halfDimension1;
            double right = c1X + halfDimension1;
            double bottom = c1Y + halfDimension1;
            double top = c1Y - halfDimension1;
            // bbox 2
            double minX = c2X - halfWidth2;
            double maxX = c2X + halfWidth2;
            double minY = c2Y - halfHeight2;
            double maxY = c2Y + halfHeight2;

            // right to the right
            if (minX > right) return false;
            // left to the left
            if (maxX < left) return false;
            // above the bottom
            if (minY > bottom) return false;
            // below the top
            if (maxY < top) return false;

            return true;
        }

        public boolean insert(double cX, double cY, double halfDimension, Point point, int level) {
            // Ignore objects that do not belong in this quad tree
            if (!containsPoint(cX, cY, halfDimension, point)) {
                return false;
            }
            // If this node is leaf and empty, put this point on this node
            if (this.sample == null && this.northWest == null) {
                this.sample = point;
                this.count = 1;
                return true;
            }
            // Else, add count into this node
            this.count ++;

            // if boundary is smaller than highestLevelNodeDimension, stop splitting, and make current node a leaf node.
            if (halfDimension * 2 / oneNodeResolution < highestLevelNodeDimension) {
                if (this.sample == null) this.sample = point;
                return true;
            }

            // Otherwise, subdivide
            if (this.northWest == null) {
                this.subdivide();
                if (this.sample != null) {
                    // insert current node's point into corresponding quadrant
                    this.insertNorthWest(cX, cY, halfDimension, this.sample, level + 1);
                    this.insertNorthEast(cX, cY, halfDimension, this.sample, level + 1);
                    this.insertSouthWest(cX, cY, halfDimension, this.sample, level + 1);
                    this.insertSouthEast(cX, cY, halfDimension, this.sample, level + 1);
                }
            }

            if (this.sample == null) this.sample = point;

            // insert new point into corresponding quadrant
            if (insertNorthWest(cX, cY, halfDimension, point, level + 1)) return true;
            if (insertNorthEast(cX, cY, halfDimension, point, level + 1)) return true;
            if (insertSouthWest(cX, cY, halfDimension, point, level + 1)) return true;
            if (insertSouthEast(cX, cY, halfDimension, point, level + 1)) return true;

            return false;
        }

        boolean insertNorthWest(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX - halfDimension;
            double cY = _cY - halfDimension;
            return this.northWest.insert(cX, cY, halfDimension, point, level);
        }

        boolean insertNorthEast(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX + halfDimension;
            double cY = _cY - halfDimension;
            return this.northEast.insert(cX, cY, halfDimension, point, level);
        }

        boolean insertSouthWest(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX - halfDimension;
            double cY = _cY + halfDimension;
            return this.southWest.insert(cX, cY, halfDimension, point, level);
        }

        boolean insertSouthEast(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX + halfDimension;
            double cY = _cY + halfDimension;
            return this.southEast.insert(cX, cY, halfDimension, point, level);
        }

        void subdivide() {
            this.northWest = new QuadTree();
            this.northEast = new QuadTree();
            this.southWest = new QuadTree();
            this.southEast = new QuadTree();
            nodesCount += 4;
        }

        /**
         * depth first search
         *
         * @param ncX
         * @param ncY
         * @param nhalfDimension
         * @param rcX
         * @param rcY
         * @param rhalfWidth
         * @param rhalfHeight
         * @param rPixelScale
         * @param level
         * @return
         */
        public List<Point> dfs(double ncX, double ncY, double nhalfDimension,
                               double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                               double rPixelScale, int level) {
            List<Point> pointsInRange = new ArrayList<>();

            // Automatically abort if the range does not intersect this quad
            if (!intersectsBBox(ncX, ncY, nhalfDimension, rcX, rcY, rhalfWidth, rhalfHeight))
                return pointsInRange; // empty list

            // Terminate here, if there are no children
            if (this.northWest == null) {
                numberOfNodesStoppedAtLevels[level] ++;
                if (this.sample != null) {
                    numberOfSamplesStoppedAtLevels[level] ++;
                    pointsInRange.add(this.sample);
                }
                return pointsInRange;
            }

            // Terminate here, if this node's pixel scale is already smaller than the range query's pixel scale
            if ((nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
                numberOfNodesStoppedAtLevels[level] ++;
                numberOfSamplesStoppedAtLevels[level] ++;
                // add this node's samples
                pointsInRange.add(this.sample);
                return pointsInRange;
            }

            // Otherwise, add the points from the children

            double cX, cY;
            double halfDimension;
            halfDimension = nhalfDimension / 2;
            // northwest
            cX = ncX - halfDimension;
            cY = ncY - halfDimension;
            pointsInRange.addAll(this.northWest.dfs(cX, cY, halfDimension,
                    rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, level + 1));

            // northeast
            cX = ncX + halfDimension;
            cY = ncY - halfDimension;
            pointsInRange.addAll(this.northEast.dfs(cX, cY, halfDimension,
                    rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, level + 1));

            // southwest
            cX = ncX - halfDimension;
            cY = ncY + halfDimension;
            pointsInRange.addAll(this.southWest.dfs(cX, cY, halfDimension,
                    rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, level + 1));

            // southeast
            cX = ncX + halfDimension;
            cY = ncY + halfDimension;
            pointsInRange.addAll(this.southEast.dfs(cX, cY, halfDimension,
                    rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, level + 1));

            return pointsInRange;
        }

        /**
         * breadth first search
         *
         * explore nodes with higher estimated profit first
         * - profit means how much more sample percentage can be added if we explore this node
         *
         * @param ncX
         * @param ncY
         * @param nhalfDimension
         * @param rcX
         * @param rcY
         * @param rhalfWidth
         * @param rhalfHeight
         * @param rPixelScale
         * @param level
         * @param samplePercentage - stop traversing when result size is roughly samplePercentage of Exact Visualization Sample
         * @return
         */
        public List<Point> bfs(double ncX, double ncY, double nhalfDimension,
                               double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                               double rPixelScale, int level, int samplePercentage) {

            class QEntry {
                int level;
                double ncX;
                double ncY;
                double nhalfDimension;
                QuadTree node;
                double estimatedProfit;

                QEntry(int _level, double _ncX, double _ncY, double _nhalfDimension, QuadTree _node, double _estimatedProfit) {
                    level = _level;
                    ncX = _ncX;
                    ncY = _ncY;
                    nhalfDimension = _nhalfDimension;
                    node = _node;
                    estimatedProfit = _estimatedProfit;
                }
            }

            List<Point> pointsInRange = new ArrayList<>();

            // explore larger estimatedProfit node first
            PriorityQueue<QEntry> queue = new PriorityQueue<>((o1, o2) -> (int) (o2.estimatedProfit * 10000 - o1.estimatedProfit * 10000));

            // target sample ratio
            double targetSampleRatio = (double) samplePercentage / 100.0;

            // add root node
            queue.add(new QEntry(level, ncX, ncY, nhalfDimension, this, estimateProfit(this.sample, this.counts)));

            while (queue.size() > 0) {

                QEntry currentEntry = queue.poll();
                int _level = currentEntry.level;
                double _ncX = currentEntry.ncX;
                double _ncY = currentEntry.ncY;
                double _nhalfDimension = currentEntry.nhalfDimension;
                QuadTree currentNode = currentEntry.node;
                double _estimatedProfit = currentEntry.estimatedProfit;

                // ignore this node if the range does not intersect with it
                if (!intersectsBBox(_ncX, _ncY, _nhalfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    continue;
                }

                // only do sample percentage driven early stop if the targetSampleRatio is not 1.0
                if (targetSampleRatio < 1.0) {
                    // if the (1 - largest estimated profit) is larger than target sample ratio,
                    // it means the smallest sample ratio of already explored node is larger than target sample ratio,
                    // stop exploring more nodes, just exhaust the queue and return all samples
                    if ((1.0 - _estimatedProfit) >= targetSampleRatio) {
                        numberOfNodesStoppedAtLevels[_level]++;
                        if (currentNode.sample != null) {
                            // to make sure the result size is roughly the target sample ratio,
                            // do a random flip coin with the probability of keeping this sample.
                            double localSampleRatio = targetSampleRatio / (1.0 - _estimatedProfit);
                            if (flipCoin(localSampleRatio)) {
                                numberOfSamplesStoppedAtLevels[_level]++;
                                pointsInRange.add(currentNode.sample);
                            }
                        }
                        continue;
                    }
                }

                // if there are no children of this node, add samples to result set
                if (currentNode.northWest == null) {
                    numberOfNodesStoppedAtLevels[_level] ++;
                    if (currentNode.sample != null) {
                        numberOfSamplesStoppedAtLevels[_level] ++;
                        pointsInRange.add(currentNode.sample);
                    }
                    continue;
                }

                // if this node's pixel scale is already smaller than the range query's pixel scale,
                // add samples to result set
                if ((_nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
                    numberOfNodesStoppedAtLevels[_level] ++;
                    if (currentNode.sample != null) {
                        numberOfSamplesStoppedAtLevels[_level] ++;
                        pointsInRange.add(currentNode.sample);
                    }
                    continue;
                }

                // Otherwise, add this node's children to the queue
                double cX, cY;
                double halfDimension = _nhalfDimension / 2;
                // northwest
                if (currentNode.northWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY - halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northWest,
                            estimateProfit(currentNode.northWest.sample, currentNode.northWest.counts)));
                }

                // northeast
                if (currentNode.northEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY - halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northEast,
                            estimateProfit(currentNode.northEast.sample, currentNode.northEast.counts)));
                }

                // southwest
                if (currentNode.southWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY + halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southWest,
                            estimateProfit(currentNode.southWest.sample, currentNode.southWest.counts)));
                }

                // southeast
                if (currentNode.southEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY + halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southEast,
                            estimateProfit(currentNode.southEast.sample, currentNode.southEast.counts)));
                }

            }

            return pointsInRange;
        }

        /**
         * Post-order traverse the Quadtree,
         * fill the counts array with count of samples for each level under current node
         */
        public int[] buildCounts(int level) {
            // corner case for leaf node
            if (this.northWest == null) {
                int count = this.sample == null? 0: 1;
                for (int i = level; i <= Constants.MAX_ZOOM; i ++) {
                    this.counts[i] = count;
                }
                return this.counts;
            }

            // compute counts for all four children first
            int[] nwCounts = this.northWest.buildCounts(level + 1);
            int[] neCounts = this.northEast.buildCounts(level + 1);
            int[] swCounts = this.southWest.buildCounts(level + 1);
            int[] seCounts = this.southEast.buildCounts(level + 1);

            // for all levels below current node, sum up counts from four children
            for (int i = level + 1; i <= Constants.MAX_ZOOM; i ++) {
                this.counts[i] = nwCounts[i] + neCounts[i] + swCounts[i] + seCounts[i];
            }
            this.counts[level] = this.sample == null? 0: 1;

            return this.counts;
        }

        public void print() {
            System.out.println("=================== RA-QuadTree ===================");
            Queue<Pair<Integer, QuadTree>> queue = new LinkedList<>();
            queue.add(new Pair<>(0, this));
            int currentLevel = -1;
            while (queue.size() > 0) {
                Pair<Integer, QuadTree> currentEntry = queue.poll();
                int level = currentEntry.getKey();
                QuadTree currentNode = currentEntry.getValue();
                if (level > currentLevel) {
                    System.out.println();
                    System.out.print("[" + level + "] ");
                    currentLevel = level;
                }
                System.out.print(currentNode.sample == null? "0": "1");
                System.out.print(", ");
                if (currentNode.northWest != null) {
                    queue.add(new Pair<>(level + 1, currentNode.northWest));
                }
                if (currentNode.northEast != null) {
                    queue.add(new Pair<>(level + 1, currentNode.northEast));
                }
                if (currentNode.southWest != null) {
                    queue.add(new Pair<>(level + 1, currentNode.southWest));
                }
                if (currentNode.southEast != null) {
                    queue.add(new Pair<>(level + 1, currentNode.southEast));
                }
            }
            System.out.println();
        }

        public void statistics() {
            System.out.println("=================== RA-QuadTree Statistics ===================");
            System.out.println("level,    # samples,    # nodes,    # samples/node,    min # samples,    max # samples");
            Queue<Pair<Integer, QuadTree>> queue = new LinkedList<>();
            queue.add(new Pair<>(0, this));
            int currentLevel = -1;
            int totalNumberOfSamples = 0;
            int totalNumberOfNodes = 0;
            int totalMinNumberOfSamples = Integer.MAX_VALUE;
            int totalMaxNumberOfSamples = 0;
            int numberOfSamples = 0;
            int numberOfNodes = 0;
            int minNumberOfSamples = Integer.MAX_VALUE;
            int maxNumberOfSamples = 0;
            while (queue.size() > 0) {
                Pair<Integer, QuadTree> currentEntry = queue.poll();
                int level = currentEntry.getKey();
                QuadTree currentNode = currentEntry.getValue();
                int currentNumberOfSamples = currentNode.sample == null? 0: 1;
                numberOfSamples += currentNumberOfSamples;
                numberOfNodes += 1;
                minNumberOfSamples = Math.min(currentNumberOfSamples, minNumberOfSamples);
                maxNumberOfSamples = Math.max(currentNumberOfSamples, maxNumberOfSamples);
                if (level > currentLevel) {
                    System.out.println(level + ",    " + numberOfSamples + ",    " + numberOfNodes + ",    " + (numberOfSamples/numberOfNodes) + ",    " + minNumberOfSamples + ",    " + maxNumberOfSamples);
                    currentLevel = level;
                    totalNumberOfSamples += numberOfSamples;
                    totalNumberOfNodes += numberOfNodes;
                    totalMinNumberOfSamples = Math.min(totalMinNumberOfSamples, minNumberOfSamples);
                    totalMaxNumberOfSamples = Math.max(totalMaxNumberOfSamples, maxNumberOfSamples);
                    numberOfSamples = 0;
                    numberOfNodes = 0;
                    minNumberOfSamples = Integer.MAX_VALUE;
                    maxNumberOfSamples = 0;
                }
                if (currentNode.northWest != null) {
                    queue.add(new Pair<>(level + 1, currentNode.northWest));
                }
                if (currentNode.northEast != null) {
                    queue.add(new Pair<>(level + 1, currentNode.northEast));
                }
                if (currentNode.southWest != null) {
                    queue.add(new Pair<>(level + 1, currentNode.southWest));
                }
                if (currentNode.southEast != null) {
                    queue.add(new Pair<>(level + 1, currentNode.southEast));
                }
            }
            System.out.println("-------------------------- Summary -------------------------");
            System.out.println("total # samples,    total # nodes,    total # samples/node,    total min # samples,    total max # samples");
            System.out.println(totalNumberOfSamples + ",    " + totalNumberOfNodes + ",    " + (totalNumberOfSamples/totalNumberOfNodes) + ",    " + totalMinNumberOfSamples + " ,   " + totalMaxNumberOfSamples);
        }

        public void histograms(int someLevel) {
            int[] histogramForSamplesOnIntermediateNodes = new int[101]; // 0 ~ 99, >=100
            int[] histogramForRawPointsOnLeafNodes = new int[101]; // 0 ~ 99, >=100
            int[] histogramForSamplesOnIntermediateNodesAtLevel = new int[101]; // 0 ~ 99, >=100
            int[] histogramForRawPointsOnIntermediateNodesAtLevel = new int[101]; // 0 ~ 999, >=1000

            Queue<Pair<Integer, QuadTree>> queue = new LinkedList<>();
            queue.add(new Pair<>(0, this));
            while (queue.size() > 0) {
                Pair<Integer, QuadTree> currentEntry = queue.poll();
                int level = currentEntry.getKey();
                QuadTree currentNode = currentEntry.getValue();
                int currentNumberOfSamples = currentNode.sample == null? 0: 1;
                if (currentNode.northWest != null) {
                    queue.add(new Pair<>(level + 1, currentNode.northWest));
                }
                if (currentNode.northEast != null) {
                    queue.add(new Pair<>(level + 1, currentNode.northEast));
                }
                if (currentNode.southWest != null) {
                    queue.add(new Pair<>(level + 1, currentNode.southWest));
                }
                if (currentNode.southEast != null) {
                    queue.add(new Pair<>(level + 1, currentNode.southEast));
                }
                if (currentNode.northWest != null && level > 10){
                    if (currentNumberOfSamples > 99) histogramForSamplesOnIntermediateNodes[100] += 1;
                    else histogramForSamplesOnIntermediateNodes[currentNumberOfSamples] += 1;
                    if (level == someLevel) {
                        if (currentNumberOfSamples > 99) histogramForSamplesOnIntermediateNodesAtLevel[100] += 1;
                        else histogramForSamplesOnIntermediateNodesAtLevel[currentNumberOfSamples] += 1;
                        if (currentNode.count > 990) histogramForRawPointsOnIntermediateNodesAtLevel[100] += 1;
                        else histogramForRawPointsOnIntermediateNodesAtLevel[currentNode.count/10] += 1;
                    }
                }
                else if (currentNode.northWest == null) {
                    if (currentNumberOfSamples > 99) histogramForRawPointsOnLeafNodes[100] += 1;
                    else histogramForRawPointsOnLeafNodes[currentNumberOfSamples] += 1;
                }
            }

            System.out.println("=================== GQuadTree Histogram for Samples on Intermediate Nodes ===================");
            System.out.println("# of samples on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println(i + ",    " + histogramForSamplesOnIntermediateNodes[i]);
            }
            System.out.println(">=100,    " + histogramForSamplesOnIntermediateNodes[100]);

            System.out.println("=================== GQuadTree Histogram for Raw Points on Leaf Nodes ===================");
            System.out.println("# of raw points on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println(i + ",    " + histogramForRawPointsOnLeafNodes[i]);
            }
            System.out.println(">=100,    " + histogramForRawPointsOnLeafNodes[100]);

            System.out.println("=================== GQuadTree Histogram for Samples on Intermediate Nodes at level " + someLevel + " ===================");
            System.out.println("# of samples on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println(i + ",    " + histogramForSamplesOnIntermediateNodesAtLevel[i]);
            }
            System.out.println(">=100,    " + histogramForSamplesOnIntermediateNodesAtLevel[100]);

            System.out.println("=================== GQuadTree Histogram for Raw Points on Intermediate Nodes at level " + someLevel + " ===================");
            System.out.println("# of raw points on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println((0 + i*10) + "~" + (9 + i*10) + ",    " + histogramForRawPointsOnIntermediateNodesAtLevel[i]);
            }
            System.out.println(">=1000,    " + histogramForRawPointsOnIntermediateNodesAtLevel[100]);
        }
    }

    QuadTree quadTree;
    int totalNumberOfPoints = 0;
    int totalStoredNumberOfPoints = 0;
    static long nodesCount = 0; // count quad-tree nodes

    /** For stats */
    static int[] numberOfNodesStoppedAtLevels; // for current query, count how many nodes stopped at a certain level
    static int[] numberOfSamplesStoppedAtLevels; // for current query, count how many samples stopped at a certain level

    /** For estimate profit */
    static int exactLevel; // for current query, the exact visualization appears at which level
    static int resX, resY; // for current query, the resolution
    static Random random = new Random();


    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public RAQuadTreeSnap() {
        this.quadTree = new QuadTree();

        oneNodeResolution = Constants.TILE_RESOLUTION;

        // zoom level 0 is fixed with dimension 1.0
        highestLevelNodeDimension = 1.0 / Math.pow(2, Constants.MAX_ZOOM);

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        /** For stats */
        numberOfNodesStoppedAtLevels = new int[Constants.MAX_ZOOM + 1];
        numberOfSamplesStoppedAtLevels = new int[Constants.MAX_ZOOM + 1];

        MyMemory.printMemory();
    }

    public void load(List<Point> points) {
        System.out.println("[RA-QuadTree Aggregator] loading " + points.size() + " points ... ...");

        MyTimer.startTimer();
        this.totalNumberOfPoints += points.size();
        int count = 0;
        int skip = 0;
        MyTimer.startTimer();
        for (Point point: points) {
            if (this.quadTree.insert(0.5, 0.5, 0.5, lngLatToXY(point), 0))
                count ++;
            else
                skip ++;
        }
        MyTimer.stopTimer();
        double insertTime = MyTimer.durationSeconds();
        this.totalStoredNumberOfPoints += count;
        System.out.println("[RA-QuadTree Aggregator] inserted " + count + " points and skipped " + skip + " points.");
        System.out.println("[RA-QuadTree Aggregator] insertion time: " + insertTime + " seconds.");

        // build counts array for each node in the QuadTree
        MyTimer.startTimer();
        int[] rootCounts = this.quadTree.buildCounts(0);
        MyTimer.stopTimer();
        double buildCountsTime = MyTimer.durationSeconds();
        System.out.println("[RA-QuadTree Aggregator] built counts array for each node is done!");
        System.out.println("[RA-QuadTree Aggregator] counts building time: " + buildCountsTime + " seconds.");
        //-DEBUG-//
        System.out.println("[RA-QuadTree Aggregator] root node counts array: ");
        for (int i = 0; i < Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + rootCounts[i]);
        }
        //-DEBUG-//

        MyTimer.stopTimer();
        double loadTime = MyTimer.durationSeconds();

        if (keepTiming) timing.put("total", timing.get("total") + loadTime);
        System.out.println("[RA-QuadTree Aggregator] loading is done!");
        System.out.println("[RA-QuadTree Aggregator] loading time: " + loadTime + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();

        //-DEBUG-//
        System.out.println("==== Until now ====");
        System.out.println("RA-QuadTree has processed " + this.totalNumberOfPoints + " points.");
        System.out.println("RA-QuadTree has stored " + this.totalStoredNumberOfPoints + " points.");
        System.out.println("RA-QuadTree has skipped " + skip + " points.");
        System.out.println("RA-QuadTree has generated " + nodesCount + " nodes.");
        this.quadTree.statistics();
        //this.quadTree.histograms(12);
        //-DEBUG-//
    }

    public static double estimateProfit(Point sample, int[] counts) {

        int exactSampleSize = Math.max(counts[exactLevel], 1);

        // in worst case how many more points are in exact samples
        int extraSampleSize = Math.max(exactSampleSize - (sample == null? 0: 1), 0);

        double estimatedProfit = (double) extraSampleSize / (double) exactSampleSize;

        return estimatedProfit;
    }

    public static boolean flipCoin(double probability) {
        return random.nextFloat() < probability;
    }

    public byte[] answerQuery(Query query) {
        double lng0 = query.bbox[0];
        double lat0 = query.bbox[1];
        double lng1 = query.bbox[2];
        double lat1 = query.bbox[3];
        int zoom = query.zoom;
        resX = query.resX;
        resY = query.resY;
        int samplePercentage = query.samplePercentage;

        MyTimer.startTimer();
        System.out.println("[RA-QuadTree Aggregator] is answering query Q = { " +
                "range: [" + lng0 + ", " + lat0 + "] ~ [" + lng1 + ", " + lat1 + "], " +
                "resolution: [" + resX + " x " + resY + "], " +
                "zoom: " + zoom + ", " +
                "samplePercentage: " + samplePercentage +
                " } ...");

        double iX0 = lngX(lng0);
        double iY0 = latY(lat0);
        double iX1 = lngX(lng1);
        double iY1 = latY(lat1);
        double pixelScale = 1.0 / 256 / Math.pow(2, zoom);
        double rcX = (iX0 + iX1) / 2;
        double rcY = (iY0 + iY1) / 2;
        double rhalfWidth = (iX1 - iX0) / 2;
        double rhalfHeight = (iY0 - iY1) / 2;

        System.out.println("[RA-QuadTree Aggregator] starting range search on QuadTree with: \n" +
                "bbox = [(" + iX0 + ", " + iY0 + "), (" + iX1 + ", " + iY1 + ")] ; \n" +
                "range = [(" + rcX + ", " + rcY + "), " + rhalfWidth + ", " + rhalfHeight + "] ; \n" +
                "pixelScale = " + pixelScale + ";");

        /** For stats*/
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfNodesStoppedAtLevels[i] = 0;
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfSamplesStoppedAtLevels[i] = 0;

        /** For estimate profit */
        // find the level where exact visualization samples are
        exactLevel = Math.min((int) Math.ceil(Math.log(1.0 / pixelScale / oneNodeResolution) / Math.log(2)), Constants.MAX_ZOOM);
        //-DEBUG-//
        System.out.println("exact level = " + exactLevel);
        //-DEBUG-//

        MyTimer.startTimer();
        List<Point> points;
        // if given samplePercentage parameter, use it to do bfs.
        if (samplePercentage > 0) {
            System.out.println("[RA-QuadTree Aggregator] is using Sample Percentage driven BFS.");
            points = this.quadTree.bfs(0.5, 0.5, 0.5,
                    rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0, samplePercentage);
        }
        // otherwise,
        else {
            System.out.println("[RA-QuadTree Aggregator] is using exact DFS.");
            points = this.quadTree.dfs(0.5, 0.5, 0.5,
                    rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0);
        }
        MyTimer.stopTimer();
        double treeTime = MyTimer.durationSeconds();

        MyTimer.temporaryTimer.put("treeTime", treeTime);
        System.out.println("[RA-QuadTree Aggregator] tree search got " + points.size() + " data points.");
        System.out.println("[RA-QuadTree Aggregator] tree search time: " + treeTime + " seconds.");

        // build binary result message
        MyTimer.startTimer();
        BinaryMessageBuilder messageBuilder = new BinaryMessageBuilder();
        double lng, lat;
        int resultSize = 0;
        for (Point point : points) {
            lng = xLng(point.getX());
            lat = yLat(point.getY());
            messageBuilder.add(lng, lat);
            resultSize++;
        }
        MyTimer.stopTimer();
        double buildBinaryTime = MyTimer.durationSeconds();
        MyTimer.temporaryTimer.put("aggregateTime", buildBinaryTime);

        System.out.println("[RA-QuadTree Aggregator] build binary result with  " + resultSize + " points.");
        System.out.println("[RA-QuadTree Aggregator] build binary result time: " + buildBinaryTime + " seconds.");

        MyTimer.stopTimer();
        System.out.println("[RA-QuadTree Aggregator] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
        System.out.println("[RA-QuadTree Aggregator] ---- # of nodes stopping at each level ----");
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + numberOfNodesStoppedAtLevels[i]);
        }
        System.out.println("[RA-QuadTree Aggregator] ---- # of samples stopping at each level ----");
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + numberOfSamplesStoppedAtLevels[i]);
        }
        return messageBuilder.getBuffer();
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }

    private void printRenderingGray(String name, byte[] _rendering, int _resolution) {
        int side = _resolution;
        System.out.println("========== " + name + "==========");
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int r = UnsignedByte.toInt(_rendering[i * side * 3 + j * 3 + 0]);
                int g = UnsignedByte.toInt(_rendering[i * side * 3 + j * 3 + 1]);
                int b = UnsignedByte.toInt(_rendering[i * side * 3 + j * 3 + 2]);
                // gray scaling formula = (0.3 * R) + (0.59 * G) + (0.11 * B)
                int gray = (int) ((0.3 * r) + (0.59 * g) + (0.11 * b));
                if (j > 0) System.out.print(" ");
                System.out.print(gray);
            }
            System.out.println();
        }
    }
}
