package algorithms;

import javafx.util.Pair;
import model.Point;
import model.Query;
import util.*;
import util.render.*;

import java.util.*;

import static util.Mercator.*;

public class RAQuadTree implements IAlgorithm {

    public static double highestLevelNodeDimension;
    // resolution of each node (similar to a tile in map systems), e.g. 512
    public static int oneNodeResolution;

    public static IRenderer renderer;

    public static IErrorMetric errorMetric;

    public class QuadTree {
        // Store count of the sub-tree
        public int count;
        // Store count for each level in the sub-tree
        public int[] counts;
        public byte[] rendering;
        public List<Point> samples;

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

        public boolean insert(double cX, double cY, double halfDimension, Point point, IRenderer aggregator, int level) {
            // Ignore objects that do not belong in this quad tree
            if (!containsPoint(cX, cY, halfDimension, point)) {
                return false;
            }
            // If this node is leaf and empty, put this point on this node
            if (this.samples == null && this.northWest == null) {
                this.samples = new ArrayList<>();
                this.samples.add(point);
                this.rendering = aggregator.createRendering(oneNodeResolution, true);
                aggregator.render(this.rendering, cX, cY, halfDimension, oneNodeResolution, true, point);
                this.count = 1;
                return true;
            }
            // Else, add count into this node
            this.count ++;

            // if boundary is smaller than highestLevelNodeDimension, stop splitting, and make current node a leaf node.
            if (halfDimension * 2 / oneNodeResolution < highestLevelNodeDimension) {
                this.samples.add(point);
                return true;
            }

            // Otherwise, subdivide
            if (this.northWest == null) {
                this.subdivide();
                // insert current node's point into corresponding quadrant
                this.insertNorthWest(cX, cY, halfDimension, this.samples.get(0), aggregator, level + 1);
                this.insertNorthEast(cX, cY, halfDimension, this.samples.get(0), aggregator, level + 1);
                this.insertSouthWest(cX, cY, halfDimension, this.samples.get(0), aggregator, level + 1);
                this.insertSouthEast(cX, cY, halfDimension, this.samples.get(0), aggregator, level + 1);
            }

            // update the rendering of this node
            boolean isDifferent = aggregator.render(this.rendering, cX, cY, halfDimension, oneNodeResolution, true, point);
            // if new rendering is different, store this point within samples
            // (only start storing samples from level 10)
            if (isDifferent) this.samples.add(point);

            // insert new point into corresponding quadrant
            if (insertNorthWest(cX, cY, halfDimension, point, aggregator, level + 1)) return true;
            if (insertNorthEast(cX, cY, halfDimension, point, aggregator, level + 1)) return true;
            if (insertSouthWest(cX, cY, halfDimension, point, aggregator, level + 1)) return true;
            if (insertSouthEast(cX, cY, halfDimension, point, aggregator, level + 1)) return true;

            return false;
        }

        boolean insertNorthWest(double _cX, double _cY, double _halfDimension, Point point, IRenderer aggregator, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX - halfDimension;
            double cY = _cY - halfDimension;
            return this.northWest.insert(cX, cY, halfDimension, point, aggregator, level);
        }

        boolean insertNorthEast(double _cX, double _cY, double _halfDimension, Point point, IRenderer aggregator, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX + halfDimension;
            double cY = _cY - halfDimension;
            return this.northEast.insert(cX, cY, halfDimension, point, aggregator, level);
        }

        boolean insertSouthWest(double _cX, double _cY, double _halfDimension, Point point, IRenderer aggregator, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX - halfDimension;
            double cY = _cY + halfDimension;
            return this.southWest.insert(cX, cY, halfDimension, point, aggregator, level);
        }

        boolean insertSouthEast(double _cX, double _cY, double _halfDimension, Point point, IRenderer aggregator, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX + halfDimension;
            double cY = _cY + halfDimension;
            return this.southEast.insert(cX, cY, halfDimension, point, aggregator, level);
        }

        void subdivide() {
            this.northWest = new QuadTree();
            this.northEast = new QuadTree();
            this.southWest = new QuadTree();
            this.southEast = new QuadTree();
            nodesCount += 4;
        }

        /**
         * depth first search for perfect visualization
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
                if (this.samples != null) {
                    numberOfSamplesStoppedAtLevels[level] += this.samples.size();
                    pointsInRange.addAll(this.samples);
                }
                return pointsInRange;
            }

            // Terminate here, if this node's pixel scale is already smaller than the range query's pixel scale
            if ((nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
                numberOfNodesStoppedAtLevels[level] ++;
                numberOfSamplesStoppedAtLevels[level] += this.samples.size();
                // add this node's samples
                pointsInRange.addAll(this.samples);
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


        class QEntry {
            int level;
            double ncX;
            double ncY;
            double nhalfDimension;
            QuadTree node;
            double benefit; // the benefit value if the take the best move
            short bestMove; // the best move #, 1 = enlarge result size; 2 = descend to children
            int sampleSize; // sample size selected on this node
            int enlargedSampleSize; // for move #1, the enlarged sample size
            int sampleSizeNW; // for move #2, sample size on child northwest
            int sampleSizeNE; // for move #2, sample size on child northeast
            int sampleSizeSW; // for move #2, sample size on child southwest
            int sampleSizeSE; // for move #2, sample size on child southeast

            int perfectSampleSize; // perfect level sample size
            int perfectSampleSizeNW; // perfect level sample size for NW child
            int perfectSampleSizeNE; // perfect level sample size for NW child
            int perfectSampleSizeSW; // perfect level sample size for NW child
            int perfectSampleSizeSE; // perfect level sample size for NW child

            QEntry(int _level, double _ncX, double _ncY, double _nhalfDimension, QuadTree _node) {
                level = _level;
                ncX = _ncX;
                ncY = _ncY;
                nhalfDimension = _nhalfDimension;
                node = _node;
                benefit = 0.0;
                bestMove = 1; // initial best move is to enlarge the result size
                sampleSize = 0; // initial result size on a node is 0
                enlargedSampleSize = 1; // initial enlarge result size to 1
                sampleSizeNW = 0;
                sampleSizeNE = 0;
                sampleSizeSW = 0;
                sampleSizeSE = 0;

                perfectSampleSize = -1;
                perfectSampleSizeNW = -1;
                perfectSampleSizeNE = -1;
                perfectSampleSizeSW = -1;
                perfectSampleSizeSE = -1;
            }
        }

        /**
         * breadth first search
         *
         * explore nodes with higher estimated benefit first
         * - benefit = gain of quality / cost of sample size
         *
         * @param _ncX
         * @param _ncY
         * @param _nhalfDimension
         * @param _rcX
         * @param _rcY
         * @param _rhalfWidth
         * @param _rhalfHeight
         * @param _rPixelScale
         * @param _targetSampleSize
         * @return
         */
        public List<Point> bfs(double _ncX, double _ncY, double _nhalfDimension,
                               double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight,
                               double _rPixelScale, int _targetSampleSize) {

            List<Point> result = new ArrayList<>();

            // explore larger estimatedProfit node first
            PriorityQueue<QEntry> queue = new PriorityQueue<>(new Comparator<QEntry>() {
                @Override
                public int compare(QEntry o1, QEntry o2) {
                    if (o2.benefit > o1.benefit)
                        return 1;
                    else if (o2.benefit < o1.benefit)
                        return -1;
                    else
                        return 0;
                }
            });

            QEntry rootEntry = new QEntry(0, _ncX, _ncY, _nhalfDimension, this);
            estimateBenefit(rootEntry, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale, _targetSampleSize);

            // add root node
            queue.add(rootEntry);
            int availableSampleSize = _targetSampleSize - rootEntry.sampleSize;

            while (queue.size() > 0) {

                // pick the largest benefit node
                QEntry entry = queue.poll();
                int level = entry.level;
                double ncX = entry.ncX;
                double ncY = entry.ncY;
                double nhalfDimension = entry.nhalfDimension;
                QuadTree node = entry.node;
                double benefit = entry.benefit;
                int sampleSize = entry.sampleSize;

                // if the largest estimated benefit is smaller than some threshold, entering collecting samples mode
                if (benefit <= 0.0 || availableSampleSize <= 0) {
                    //-DEBUG-//
//                    System.out.println("[queue] level = " + level);
//                    System.out.println("[queue] benefit = " + benefit);
//                    System.out.println("[queue] sample size = " + sampleSize);
                    //-DEBUG-//
                    numberOfNodesStoppedAtLevels[level] ++;
                    if (node.samples != null) {
                        if (sampleSize >= node.samples.size()) {
                            numberOfSamplesStoppedAtLevels[level] += node.samples.size();
                            result.addAll(node.samples);
                        }
                        else {
                            sampleSize = sampleSize == 0? 1: sampleSize;
                            numberOfSamplesStoppedAtLevels[level] += sampleSize;
                            result.addAll(node.samples.subList(0, sampleSize));
                        }
                    }
                    continue;
                }

                // Otherwise, take the best move suggested by this node
                switch (entry.bestMove) {
                    // move #1 - enlarge sample size
                    case 1:
                        availableSampleSize -= entry.enlargedSampleSize - entry.sampleSize;
                        entry.sampleSize = entry.enlargedSampleSize;
                        estimateBenefit(entry, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale, availableSampleSize);
                        queue.add(entry);
                        break;
                    // move #2 - descend to children
                    case 2:
                        availableSampleSize -= entry.sampleSizeNW + entry.sampleSizeNE + entry.sampleSizeSW + entry.sampleSizeSE - entry.sampleSize;
                        double cX, cY;
                        double halfDimension = nhalfDimension / 2;

                        // northwest
                        cX = ncX - halfDimension;
                        cY = ncY - halfDimension;
                        // ignore this node if the range does not intersect with it
                        if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                            QEntry entryNW = new QEntry(level + 1, cX, cY, halfDimension, node.northWest);
                            entryNW.perfectSampleSize = entry.perfectSampleSizeNW;
                            entryNW.sampleSize = entry.sampleSizeNW;
                            estimateBenefit(entryNW, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale, availableSampleSize);
                            queue.add(entryNW);
                        }

                        // northeast
                        cX = ncX + halfDimension;
                        cY = ncY - halfDimension;
                        // ignore this node if the range does not intersect with it
                        if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                            QEntry entryNE = new QEntry(level + 1, cX, cY, halfDimension, node.northEast);
                            entryNE.perfectSampleSize = entry.perfectSampleSizeNE;
                            entryNE.sampleSize = entry.sampleSizeNE;
                            estimateBenefit(entryNE, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale, availableSampleSize);
                            queue.add(entryNE);
                        }

                        // southwest
                        cX = ncX - halfDimension;
                        cY = ncY + halfDimension;
                        // ignore this node if the range does not intersect with it
                        if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                            QEntry entrySW = new QEntry(level + 1, cX, cY, halfDimension, node.southWest);
                            entrySW.perfectSampleSize = entry.perfectSampleSizeSW;
                            entrySW.sampleSize = entry.sampleSizeSW;
                            estimateBenefit(entrySW, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale, availableSampleSize);
                            queue.add(entrySW);
                        }

                        // southeast
                        cX = ncX + halfDimension;
                        cY = ncY + halfDimension;
                        // ignore this node if the range does not intersect with it
                        if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                            QEntry entrySE = new QEntry(level + 1, cX, cY, halfDimension, node.southEast);
                            entrySE.perfectSampleSize = entry.perfectSampleSizeSE;
                            entrySE.sampleSize = entry.sampleSizeSE;
                            estimateBenefit(entrySE, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale, availableSampleSize);
                            queue.add(entrySE);
                        }
                        break;
                }
            }

            //-DEBUG-//
            System.out.println("[availableSampleSize] = " + availableSampleSize);

            return result;
        }

        /**
         * breadth first search
         *
         * - find the perfect visualization level first
         * - then do stratified sampling with spatial grids as strata
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
         * @param sampleSize - return results only with this sample size
         * @return
         */
        public List<Point> sampleAfterTraverse(double ncX, double ncY, double nhalfDimension,
                                               double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                                               double rPixelScale, int level, int sampleSize) {

            class QEntry {
                int level;
                double ncX;
                double ncY;
                double nhalfDimension;
                QuadTree node;

                QEntry(int _level, double _ncX, double _ncY, double _nhalfDimension, QuadTree _node) {
                    level = _level;
                    ncX = _ncX;
                    ncY = _ncY;
                    nhalfDimension = _nhalfDimension;
                    node = _node;
                }
            }

            List<Point> result = new ArrayList<>();

            Queue<QEntry> traverseQueue = new LinkedList<>();
            Queue<QEntry> sampleQueue = new LinkedList<>();
            // add root node
            traverseQueue.add(new QEntry(level, ncX, ncY, nhalfDimension, this));
            // the total result size for a perfect visualization
            int perfectResultSize = 0;

            // traverse the tree, expand all nodes until to perfect visualization level
            // keep how many nodes left in the queue that need to be expanded
            int toBeExpandedNodes = 1; // only root now
            while (toBeExpandedNodes > 0) {

                QEntry currentEntry = traverseQueue.poll();
                int _level = currentEntry.level;
                double _ncX = currentEntry.ncX;
                double _ncY = currentEntry.ncY;
                double _nhalfDimension = currentEntry.nhalfDimension;
                QuadTree currentNode = currentEntry.node;

                // current node expanded
                toBeExpandedNodes --;

                // ignore this node if the range does not intersect with it
                if (!intersectsBBox(_ncX, _ncY, _nhalfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    continue;
                }

                // this node is already a leaf,
                // count all samples on this node as in the perfectResultSize
                if (currentNode.northWest == null) {
                    if (currentNode.samples != null) {
                        perfectResultSize += currentNode.samples.size();
                        sampleQueue.add(currentEntry);
                    }
                    continue;
                }

                // if this node's pixel scale is already smaller than the range query's pixel scale,
                // count all samples on this node as in the perfectResultSize
                if ((_nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
                    if (currentNode.samples != null) {
                        perfectResultSize += currentNode.samples.size();
                        sampleQueue.add(currentEntry);
                    }
                    continue;
                }

                // Otherwise, add this node's children to the queue
                double cX, cY;
                double halfDimension = _nhalfDimension / 2;
                double estimatedError;
                // northwest
                if (currentNode.northWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY - halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northWest));
                    toBeExpandedNodes ++;
                }

                // northeast
                if (currentNode.northEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY - halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northEast));
                    toBeExpandedNodes ++;
                }

                // southwest
                if (currentNode.southWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY + halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southWest));
                    toBeExpandedNodes ++;
                }

                // southeast
                if (currentNode.southEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY + halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southEast));
                    toBeExpandedNodes ++;
                }
            }

            //-DEBUG-//
            System.out.println("sampleQueue.size = " + sampleQueue.size());
            System.out.println("perfectResultSize = " + perfectResultSize);
            System.out.println("sampleSize = " + sampleSize);
            //-DEBUG-//

            // if required sample size is larger than perfect result size,
            // return all result
            if (sampleSize >= perfectResultSize) {
                while (sampleQueue.size() > 0) {
                    QEntry currentEntry = sampleQueue.poll();
                    int _level = currentEntry.level;
                    QuadTree currentNode = currentEntry.node;
                    numberOfNodesStoppedAtLevels[_level]++;
                    if (currentNode.samples != null) {
                        numberOfSamplesStoppedAtLevels[_level] += currentNode.samples.size();
                        result.addAll(currentNode.samples);
                    }
                }
            }
            else {
                // exhaust the perfect level nodes left in the queue,
                // do a stratified sampling with each node as a strata

                // if #nodes > sample size, do sampling on nodes
                if (sampleQueue.size() > sampleSize) {
                    // every node has the same probability (nodeSampleRatio) to have one sample or not.
                    double nodeSampleRatio = (double) sampleSize / (double) sampleQueue.size();
                    System.out.println("#nodes > sample size, sample on nodes, with nodeSampleRatio = " + nodeSampleRatio);
                    while (sampleQueue.size() > 0) {
                        QEntry currentEntry = sampleQueue.poll();
                        int _level = currentEntry.level;
                        QuadTree currentNode = currentEntry.node;
                        numberOfNodesStoppedAtLevels[_level]++;
                        if (currentNode.samples != null && currentNode.samples.size() > 0) {
                            // do a random flip coin with the probability (localSampleRatio).
                            if (flipCoin(nodeSampleRatio)) {
                                numberOfSamplesStoppedAtLevels[_level] ++;
                                result.add(currentNode.samples.get(0));
                            }
                        }
                    }
                }
                // do sampling on samples on each node
                else {
                    // extraSampleRatio is sample ratio on the the rest samples excluding a must have one point for each node
                    double extraSampleRatio = (double) (sampleSize - sampleQueue.size()) / (double) (perfectResultSize - sampleQueue.size());
                    int localSampleSize;
                    System.out.println("#nodes < sample size, sampling on each node, with extraSampleRatio = " + extraSampleRatio);
                    while (sampleQueue.size() > 0) {
                        QEntry currentEntry = sampleQueue.poll();
                        int _level = currentEntry.level;
                        QuadTree currentNode = currentEntry.node;
                        numberOfNodesStoppedAtLevels[_level]++;
                        if (currentNode.samples != null) {
                            // extra samples can be included + the must have one
                            localSampleSize = ((int) Math.round(extraSampleRatio * (currentNode.samples.size() - 1))) + 1;
                            numberOfSamplesStoppedAtLevels[_level] += localSampleSize;
                            result.addAll(currentNode.samples.subList(0, localSampleSize));
                        }
                    }
                }
            }

            return result;
        }

        /**
         * breadth first search
         *
         * - find the perfect visualization level first
         * - then do random sampling
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
         * @param sampleSize - return results only with this sample size
         * @return
         */
        public List<Point> randomSampleAfterTraverse(double ncX, double ncY, double nhalfDimension,
                                               double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                                               double rPixelScale, int level, int sampleSize) {

            class QEntry {
                int level;
                double ncX;
                double ncY;
                double nhalfDimension;
                QuadTree node;

                QEntry(int _level, double _ncX, double _ncY, double _nhalfDimension, QuadTree _node) {
                    level = _level;
                    ncX = _ncX;
                    ncY = _ncY;
                    nhalfDimension = _nhalfDimension;
                    node = _node;
                }
            }

            List<Point> result = new ArrayList<>();

            Queue<QEntry> traverseQueue = new LinkedList<>();
            Queue<QEntry> sampleQueue = new LinkedList<>();
            // add root node
            traverseQueue.add(new QEntry(level, ncX, ncY, nhalfDimension, this));
            // the total result size for a perfect visualization
            int perfectResultSize = 0;

            // traverse the tree, expand all nodes until to perfect visualization level
            // keep how many nodes left in the queue that need to be expanded
            int toBeExpandedNodes = 1; // only root now
            while (toBeExpandedNodes > 0) {

                QEntry currentEntry = traverseQueue.poll();
                int _level = currentEntry.level;
                double _ncX = currentEntry.ncX;
                double _ncY = currentEntry.ncY;
                double _nhalfDimension = currentEntry.nhalfDimension;
                QuadTree currentNode = currentEntry.node;

                // current node expanded
                toBeExpandedNodes --;

                // ignore this node if the range does not intersect with it
                if (!intersectsBBox(_ncX, _ncY, _nhalfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    continue;
                }

                // this node is already a leaf,
                // count all samples on this node as in the perfectResultSize
                if (currentNode.northWest == null) {
                    if (currentNode.samples != null) {
                        perfectResultSize += currentNode.samples.size();
                        sampleQueue.add(currentEntry);
                    }
                    continue;
                }

                // if this node's pixel scale is already smaller than the range query's pixel scale,
                // count all samples on this node as in the perfectResultSize
                if ((_nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
                    if (currentNode.samples != null) {
                        perfectResultSize += currentNode.samples.size();
                        sampleQueue.add(currentEntry);
                    }
                    continue;
                }

                // Otherwise, add this node's children to the queue
                double cX, cY;
                double halfDimension = _nhalfDimension / 2;
                double estimatedError;
                // northwest
                if (currentNode.northWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY - halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northWest));
                    toBeExpandedNodes ++;
                }

                // northeast
                if (currentNode.northEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY - halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northEast));
                    toBeExpandedNodes ++;
                }

                // southwest
                if (currentNode.southWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY + halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southWest));
                    toBeExpandedNodes ++;
                }

                // southeast
                if (currentNode.southEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY + halfDimension;
                    traverseQueue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southEast));
                    toBeExpandedNodes ++;
                }
            }

            //-DEBUG-//
            System.out.println("sampleQueue.size = " + sampleQueue.size());
            System.out.println("perfectResultSize = " + perfectResultSize);
            System.out.println("sampleSize = " + sampleSize);
            //-DEBUG-//

            // if required sample size is larger than perfect result size,
            // return all result
            if (sampleSize >= perfectResultSize) {
                while (sampleQueue.size() > 0) {
                    QEntry currentEntry = sampleQueue.poll();
                    int _level = currentEntry.level;
                    QuadTree currentNode = currentEntry.node;
                    numberOfNodesStoppedAtLevels[_level]++;
                    if (currentNode.samples != null) {
                        numberOfSamplesStoppedAtLevels[_level] += currentNode.samples.size();
                        result.addAll(currentNode.samples);
                    }
                }
            }
            else {
                // exhaust the perfect level nodes left in the queue,
                // do a random sampling
                double sampleRatio = (double) sampleSize / (double) perfectResultSize;
                while (sampleQueue.size() > 0) {
                    QEntry currentEntry = sampleQueue.poll();
                    int _level = currentEntry.level;
                    QuadTree currentNode = currentEntry.node;
                    numberOfNodesStoppedAtLevels[_level]++;
                    if (currentNode.samples != null) {
                        for (Point p: currentNode.samples) {
                            if (flipCoin(sampleRatio)) {
                                result.add(p);
                                numberOfSamplesStoppedAtLevels[_level] ++;
                            }
                        }
                    }
                }
            }

            return result;
        }

        /**
         * breadth first search with sampling in traversal
         *
         * explore nodes with higher estimated profit first
         * - profit means how much more sample percentage can be added if we explore this node
         * - stop once we have enough samples
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
        public List<Point> sampleInTraverse(double ncX, double ncY, double nhalfDimension,
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
            queue.add(new QEntry(level, ncX, ncY, nhalfDimension, this, estimateProfit(this.samples, this.counts)));

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
                        if (currentNode.samples != null) {
                            // to make sure the result size is roughly the target sample ratio,
                            // do a random sample on currentNode.samples.
                            double localSampleRatio = targetSampleRatio / (1.0 - _estimatedProfit);
                            int localSampleSize = (int) (currentNode.samples.size() * localSampleRatio);
                            localSampleSize = Math.max(localSampleSize, 1);
                            numberOfSamplesStoppedAtLevels[_level] += localSampleSize;
                            pointsInRange.addAll(currentNode.samples.subList(0, localSampleSize));
                        }
                        continue;
                    }
                }

                // if there are no children of this node, add samples to result set
                if (currentNode.northWest == null) {
                    numberOfNodesStoppedAtLevels[_level] ++;
                    if (currentNode.samples != null) {
                        numberOfSamplesStoppedAtLevels[_level] += currentNode.samples.size();
                        pointsInRange.addAll(currentNode.samples);
                    }
                    continue;
                }

                // if this node's pixel scale is already smaller than the range query's pixel scale,
                // add samples to result set
                if ((_nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
                    numberOfNodesStoppedAtLevels[_level] ++;
                    if (currentNode.samples != null) {
                        numberOfSamplesStoppedAtLevels[_level] += currentNode.samples.size();
                        pointsInRange.addAll(currentNode.samples);
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
                            estimateProfit(currentNode.northWest.samples, currentNode.northWest.counts)));
                }

                // northeast
                if (currentNode.northEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY - halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northEast,
                            estimateProfit(currentNode.northEast.samples, currentNode.northEast.counts)));
                }

                // southwest
                if (currentNode.southWest != null) {
                    cX = _ncX - halfDimension;
                    cY = _ncY + halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southWest,
                            estimateProfit(currentNode.southWest.samples, currentNode.southWest.counts)));
                }

                // southeast
                if (currentNode.southEast != null) {
                    cX = _ncX + halfDimension;
                    cY = _ncY + halfDimension;
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southEast,
                            estimateProfit(currentNode.southEast.samples, currentNode.southEast.counts)));
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
                int count = this.samples == null? 0: this.samples.size();
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
            this.counts[level] = this.samples.size();

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
                System.out.print(currentNode.samples == null? "0": currentNode.samples.size());
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
                int currentNumberOfSamples = currentNode.samples == null? 0: currentNode.samples.size();
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
                int currentNumberOfSamples = currentNode.samples == null? 0: currentNode.samples.size();
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

            System.out.println("=================== RA-QuadTree Histogram for Samples on Intermediate Nodes ===================");
            System.out.println("# of samples on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println(i + ",    " + histogramForSamplesOnIntermediateNodes[i]);
            }
            System.out.println(">=100,    " + histogramForSamplesOnIntermediateNodes[100]);

            System.out.println("=================== RA-QuadTree Histogram for Raw Points on Leaf Nodes ===================");
            System.out.println("# of raw points on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println(i + ",    " + histogramForRawPointsOnLeafNodes[i]);
            }
            System.out.println(">=100,    " + histogramForRawPointsOnLeafNodes[100]);

            System.out.println("=================== RA-QuadTree Histogram for Samples on Intermediate Nodes at level " + someLevel + " ===================");
            System.out.println("# of samples on node,    # of nodes");
            for (int i = 0; i < 100; i ++) {
                System.out.println(i + ",    " + histogramForSamplesOnIntermediateNodesAtLevel[i]);
            }
            System.out.println(">=100,    " + histogramForSamplesOnIntermediateNodesAtLevel[100]);

            System.out.println("=================== RA-QuadTree Histogram for Raw Points on Intermediate Nodes at level " + someLevel + " ===================");
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

    /** For query stats */
    static int[] numberOfNodesStoppedAtLevels; // for current query, count how many nodes stopped at a certain level
    static int[] numberOfSamplesStoppedAtLevels; // for current query, count how many samples stopped at a certain level

    /** For query time analysis */
    static Map<String, Double> times; // for current query, store times for different parts

    /** For query estimate benefit */
    static int exactLevel; // for current query, the exact visualization appears at which level
    static int resX, resY; // for current query, the resolution

    static Random random = new Random();

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public RAQuadTree() {
        this.quadTree = new QuadTree();

        oneNodeResolution = Constants.TILE_RESOLUTION;

        // zoom level 0 is fixed with dimension 1.0
        highestLevelNodeDimension = 1.0 / Math.pow(2, Constants.MAX_ZOOM);

        renderer = new DeckGLRenderer(Constants.RADIUS_IN_PIXELS);

        errorMetric = new MeanSquaredError();

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        /** For query stats */
        numberOfNodesStoppedAtLevels = new int[Constants.MAX_ZOOM + 2];
        numberOfSamplesStoppedAtLevels = new int[Constants.MAX_ZOOM + 2];

        /** For query time analysis */
        times = new HashMap<>();

        MyMemory.printMemory();
    }

    public void load(List<Point> points) {
        System.out.println("[RA-QuadTree] loading " + points.size() + " points ... ...");

        MyTimer.startTimer();
        this.totalNumberOfPoints += points.size();
        int count = 0;
        int skip = 0;
        MyTimer.startTimer();
        for (Point point: points) {
            if (this.quadTree.insert(0.5, 0.5, 0.5, lngLatToXY(point), renderer, 0))
                count ++;
            else
                skip ++;
        }
        MyTimer.stopTimer();
        double insertTime = MyTimer.durationSeconds();
        this.totalStoredNumberOfPoints += count;
        System.out.println("[RA-QuadTree] inserted " + count + " points and skipped " + skip + " points.");
        System.out.println("[RA-QuadTree] insertion time: " + insertTime + " seconds.");

        // build counts array for each node in the QuadTree
        MyTimer.startTimer();
        int[] rootCounts = this.quadTree.buildCounts(0);
        MyTimer.stopTimer();
        double buildCountsTime = MyTimer.durationSeconds();
        System.out.println("[RA-QuadTree] built counts array for each node is done!");
        System.out.println("[RA-QuadTree] counts building time: " + buildCountsTime + " seconds.");
        //-DEBUG-//
        System.out.println("[RA-QuadTree] root node counts array: ");
        for (int i = 0; i < Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + rootCounts[i]);
        }
        //-DEBUG-//

        MyTimer.stopTimer();
        double loadTime = MyTimer.durationSeconds();

        if (keepTiming) timing.put("total", timing.get("total") + loadTime);
        System.out.println("[RA-QuadTree] loading is done!");
        System.out.println("[RA-QuadTree] loading time: " + loadTime + " seconds.");
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

    public static double estimateProfit(List<Point> samples, int[] counts) {

        int exactSampleSize = Math.max(counts[exactLevel], 1);

        // in worst case how many more points are in exact samples
        int extraSampleSize = Math.max(exactSampleSize - (samples == null? 0: samples.size()), 0);

        double estimatedProfit = (double) extraSampleSize / (double) exactSampleSize;

        return estimatedProfit;
    }

    public static int estimatePerfectSampleSize(QuadTree _node, double _ncX, double _ncY, double _nhalfDimension,
                                                double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight) {
        //--time--//
        long startTime = System.nanoTime();

        // 1 how many samples are at the perfect level under this _node
        int perfectSampleSize = _node.counts[exactLevel];

        // 2 overlap area of _node range with query range
        double overlapArea;
        double nLeft = _ncX - _nhalfDimension;
        double nRight = _ncX + _nhalfDimension;
        double nTop = _ncY - _nhalfDimension;
        double nBottom = _ncY + _nhalfDimension;
        double rLeft = _rcX - _rhalfWidth;
        double rRight = _rcX + _rhalfWidth;
        double rTop = _rcY - _rhalfHeight;
        double rBottom = _rcY + _rhalfHeight;
        // if no overlap
        if (rRight < nLeft || rLeft > nRight || rBottom < nTop || rTop > nBottom) {
            overlapArea = 0.0;
        }
        // if node is within query range
        else if (nLeft >= rLeft && nRight <= rRight && nTop >= rTop && nBottom <= rBottom) {
            overlapArea = _nhalfDimension * 2 * _nhalfDimension * 2;
        }
        // if query range is within node
        else if (rLeft >= nLeft && rRight <= nRight && rTop >= nTop && rBottom <= nBottom) {
            overlapArea = _rhalfWidth * 2 * _rhalfHeight * 2;
        }
        // else they are overlapped
        else {
            double oLeft = Math.max(nLeft, rLeft);
            double oRight = Math.min(nRight, rRight);
            double oTop = Math.max(nTop, rTop);
            double oBottom = Math.min(nBottom, rBottom);
            overlapArea = (oRight - oLeft) * (oBottom - oTop);
        }

        // 3 assume the perfect samples are uniformly distributed under _node at the perfect level,
        //   then the ratio is overlap area / node area
        double nodeArea = _nhalfDimension * 2 * _nhalfDimension * 2;
        perfectSampleSize = (int) Math.round((overlapArea / nodeArea) * perfectSampleSize);

        //-DEBUG-//
//        if (perfectSampleSize == 0) {
//            if (_node.counts[exactLevel] != 0) {
//                System.out.println("[perfect result size] perfect level size = " + _node.counts[exactLevel]);
//                System.out.println("[perfect result size] overlap area = " + overlapArea);
//                System.out.println("[perfect result size] node area = " + nodeArea);
//            }
//        }

        long endTime = System.nanoTime();
        times.put("estimatePerfectSampleSize", times.get("estimatePerfectSampleSize") + ((double) (endTime - startTime) / 1000000000.0));

        return perfectSampleSize;
    }

    public static int targetSampleSize(int _perfectResultSize, int _totalPerfectResultSize, int _totalSampleSize) {
        if (_perfectResultSize == 0) return 0;
        // as long as perfect level result size for this node is not 0, sample at least one point for this node
        return Math.max((int) Math.round(((double) _perfectResultSize / (double) _totalPerfectResultSize) * _totalSampleSize), 1);
    }

    /**
     * obtain _targetSampleSize sample points from given _node
     *
     * @param _node
     * @param _targetSampleSize
     * @return null - if given _node has no samples at all or sample size is not enough for target sample size
     */
    public static List<Point> sampleOnNode(QuadTree _node, int _targetSampleSize) {
        if (_targetSampleSize == 0) return null;
        if (_node.samples == null) {
            return null;
        }
        else {
            if (_targetSampleSize > _node.samples.size()) {
                return null;
            }
            else {
                //--time--//
                long startTime = System.nanoTime();
                int startIndex = _node.samples.size() - _targetSampleSize;
                List<Point> samples = _node.samples.subList(startIndex, _node.samples.size());
                //--time--//
                long endTime = System.nanoTime();
                times.put("sampleOnNode", times.get("sampleOnNode") + ((double) (endTime - startTime) / 1000000000.0));
                return samples;
            }
        }
    }

    /**
     * estimate the benefit for given _entry
     *
     * @param _entry
     * @param _rcX
     * @param _rcY
     * @param _rhalfWidth
     * @param _rhalfHeight
     * @param _rPixelScale
     * @param _availableSampleSize - available extra sample size
     * @return
     */
    public static void estimateBenefit(QuadTree.QEntry _entry,
                                       double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight,
                                       double _rPixelScale, int _availableSampleSize) {
        //--time--//
        long startTime = System.nanoTime();

        double _ncX = _entry.ncX;
        double _ncY = _entry.ncY;
        double _nhalfDimension = _entry.nhalfDimension;
        QuadTree _node = _entry.node;

        // corner case
        if (_node.samples == null) {
            _entry.benefit = 0.0;
            return;
        }

        // compute how many samples are at the perfect level under this _node
        _entry.perfectSampleSize = _entry.perfectSampleSize == -1?
                estimatePerfectSampleSize(_node, _ncX, _ncY, _nhalfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight):
                _entry.perfectSampleSize;

        // get the resolution for given _node as piece of the result
        int resolution = (int) Math.round(2 * _nhalfDimension / _rPixelScale);

        int sampleSize = _entry.sampleSize;

        //--time--//
        long startTime0 = System.nanoTime();
        // render current sampleSize points to rendering0
        byte[] rendering0 = renderer.createRendering(resolution, true);
        for (int i = 0; i < sampleSize; i++) {
            renderer.render(rendering0, _ncX, _ncY, _nhalfDimension, resolution, true, _node.samples.get(i));
        }
        //--time--//
        long endTime0 = System.nanoTime();
        times.put("rendering", times.get("rendering") + ((double) (endTime0 - startTime0) / 1000000000.0));

        /** Move #1 */
        double benefit1 = 0.0;

        // compute enlarged sample size by double current sample size
        int enlargedSampleSize = sampleSize == 0 ? 1 : sampleSize * 2;
        // enlarged sample size - current sample size <= available sample size
        if ((enlargedSampleSize - sampleSize) > _availableSampleSize) enlargedSampleSize = sampleSize + _availableSampleSize;
        // enlarged sample size <= node.samples.size()
        if (enlargedSampleSize > _node.samples.size()) enlargedSampleSize = _node.samples.size();

        if (enlargedSampleSize > sampleSize) {
            List<Point> enlargedSamples = sampleOnNode(_node, enlargedSampleSize);

            //--time--//
            long startTime1 = System.nanoTime();
            // render newResultSize points to rendering1
            byte[] rendering1 = renderer.createRendering(resolution, true);
            for (Point point : enlargedSamples) {
                renderer.render(rendering1, _ncX, _ncY, _nhalfDimension, resolution, true, point);
            }
            //--time--//
            long endTime1 = System.nanoTime();
            times.put("rendering", times.get("rendering") + ((double) (endTime1 - startTime1) / 1000000000.0));

            //--time--//
            long startTime1e = System.nanoTime();
            // compute the error between rendering0 and rendering1 as the gain1
            double gain1 = errorMetric.totalError(rendering0, rendering1, resolution, true);
            //--time--//
            long endTime1e = System.nanoTime();
            times.put("computeError", times.get("computeError") + ((double) (endTime1e - startTime1e) / 1000000000.0));
            int cost1 = enlargedSampleSize - sampleSize;
            benefit1 = gain1 / cost1;

            //-DEBUG-//
//            if (benefit1 <= 0.0) {
//                System.out.println("[benefit] -- benefit1 = " + benefit1 + " --");
//                System.out.println("[benefit] enlarged samples = " + enlargedSamples.size());
//                System.out.println("[benefit] gain1 = " + gain1);
//                System.out.println("[benefit] cost1 = " + cost1);
//                System.out.println("1st point in enlargedSamples = ");
//                Point point = enlargedSamples.get(0);
//                System.out.println("[" + point.getX() + ", " + point.getY() + "]");
//                System.out.println("_ncX = " + _ncX + ", _ncY = " + _ncY + ", _nhalfDimension = " + _nhalfDimension);
//                System.out.println("resolution = " + resolution);
//                printRenderingGray("rendering0", rendering0, resolution, true);
//                printRenderingGray("rendering1", rendering1, resolution, true);
//            }
        }
        //-DEBUG-//
        else {
            //System.out.println("[benefit] enlarged sample size <= sample size!");
        }

        /** Move #2 */
        double benefit2 = 0.0;
        int sampleSizeNW = 0;
        int sampleSizeNE = 0;
        int sampleSizeSW = 0;
        int sampleSizeSE = 0;
        if (_node.northWest != null) {
            // for each children of the _node, compute a sampleSize proportional to their perfectSampleSize
            double cX, cY;
            double halfDimension = _nhalfDimension / 2;

            // northwest
            if (_entry.perfectSampleSizeNW == -1) {
                cX = _ncX - halfDimension;
                cY = _ncY - halfDimension;
                _entry.perfectSampleSizeNW = estimatePerfectSampleSize(_node.northWest, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
            }
            // northeast
            if (_entry.perfectSampleSizeNE == -1) {
                cX = _ncX + halfDimension;
                cY = _ncY - halfDimension;
                _entry.perfectSampleSizeNE = estimatePerfectSampleSize(_node.northEast, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
            }
            // southwest
            if (_entry.perfectSampleSizeSW == -1) {
                cX = _ncX - halfDimension;
                cY = _ncY + halfDimension;
                _entry.perfectSampleSizeSW = estimatePerfectSampleSize(_node.southWest, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
            }
            // southeast
            if (_entry.perfectSampleSizeSE == -1) {
                cX = _ncX + halfDimension;
                cY = _ncY + halfDimension;
                _entry.perfectSampleSizeSE = estimatePerfectSampleSize(_node.southEast, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
            }

            int totalPerfectSampleSizeOnChildren = _entry.perfectSampleSizeNW + _entry.perfectSampleSizeNE + _entry.perfectSampleSizeSW + _entry.perfectSampleSizeSE;

            sampleSizeNW = (int) Math.round(((double) _entry.perfectSampleSizeNW / (double) totalPerfectSampleSizeOnChildren) * sampleSize);
            sampleSizeNE = (int) Math.round(((double) _entry.perfectSampleSizeNE / (double) totalPerfectSampleSizeOnChildren) * sampleSize);
            sampleSizeSW = (int) Math.round(((double) _entry.perfectSampleSizeSW / (double) totalPerfectSampleSizeOnChildren) * sampleSize);
            sampleSizeSE = (int) Math.round(((double) _entry.perfectSampleSizeSE / (double) totalPerfectSampleSizeOnChildren) * sampleSize);
            sampleSizeNW = _node.northWest.samples == null? 0: (Math.min(sampleSizeNW, _node.northWest.samples.size()));
            sampleSizeNE = _node.northEast.samples == null? 0: (Math.min(sampleSizeNE, _node.northEast.samples.size()));
            sampleSizeSW = _node.southWest.samples == null? 0: (Math.min(sampleSizeSW, _node.southWest.samples.size()));
            sampleSizeSE = _node.southEast.samples == null? 0: (Math.min(sampleSizeSE, _node.southEast.samples.size()));
            List<Point> samplesOnChildNW = sampleOnNode(_node.northWest, sampleSizeNW);
            List<Point> samplesOnChildNE = sampleOnNode(_node.northEast, sampleSizeNE);
            List<Point> samplesOnChildSW = sampleOnNode(_node.southWest, sampleSizeSW);
            List<Point> samplesOnChildSE = sampleOnNode(_node.southEast, sampleSizeSE);

            // concatenate all samples on children
            List<Point> samplesOnChildren = new ArrayList<>();
            if (samplesOnChildNW != null) samplesOnChildren.addAll(samplesOnChildNW);
            if (samplesOnChildNE != null) samplesOnChildren.addAll(samplesOnChildNE);
            if (samplesOnChildSW != null) samplesOnChildren.addAll(samplesOnChildSW);
            if (samplesOnChildSE != null) samplesOnChildren.addAll(samplesOnChildSE);

            //--time--//
            long startTime2 = System.nanoTime();
            // render samplesOnChildren to rendering2
            byte[] rendering2 = renderer.createRendering(resolution, true);
            for (Point point : samplesOnChildren) {
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, true, point);
            }
            //--time--//
            long endTime2 = System.nanoTime();
            times.put("rendering", times.get("rendering") + ((double) (endTime2 - startTime2) / 1000000000.0));

            //--time--//
            long startTime2e = System.nanoTime();
            // compute the error between rendering0 and rendering2 as the gain2
            double gain2 = errorMetric.totalError(rendering0, rendering2, resolution, true);
            //--time--//
            long endTime2e = System.nanoTime();
            times.put("computeError", times.get("computeError") + ((double) (endTime2e - startTime2e) / 1000000000.0));
            int cost2 = sampleSizeNW + sampleSizeNE + sampleSizeSW + sampleSizeSE - sampleSize;
            cost2 = cost2 == 0 ? 1 : cost2;
            benefit2 = gain2 / cost2;

            //-DEBUG-//
//            if (benefit2 <= 0.0) {
//                System.out.println("[benefit] samplesOnChildNW = " + (samplesOnChildNW == null? "null": samplesOnChildNW.size()));
//                System.out.println("[benefit] samplesOnChildNE = " + (samplesOnChildNE == null? "null": samplesOnChildNE.size()));
//                System.out.println("[benefit] samplesOnChildSW = " + (samplesOnChildSW == null? "null": samplesOnChildSW.size()));
//                System.out.println("[benefit] samplesOnChildSE = " + (samplesOnChildSE == null? "null": samplesOnChildSE.size()));
//                System.out.println("[benefit] gain2 = " + gain2);
//                System.out.println("[benefit] cost2 = " + cost2);
//            }
        }
        //-DEBUG-//
        else {
            //System.out.println("[benefit] No children of this node!");
        }

        //-DEBUG-//
        double benefit = 0.0;

        // take move #1
        if (benefit1 > benefit2) {
            _entry.bestMove = 1;
            _entry.benefit = benefit1;
            _entry.enlargedSampleSize = enlargedSampleSize;
            //-DEBUG-//
            benefit = benefit1;
        }
        // take move #2
        else {
            _entry.bestMove = 2;
            _entry.benefit = benefit2;
            _entry.sampleSizeNW = sampleSizeNW;
            _entry.sampleSizeNE = sampleSizeNE;
            _entry.sampleSizeSW = sampleSizeSW;
            _entry.sampleSizeSE = sampleSizeSE;
            //-DEBUG-//
            benefit = benefit2;
        }

        //--time--//
        long endTime = System.nanoTime();
        times.put("estimateBenefit", times.get("estimateBenefit") + ((double) (endTime - startTime) / 1000000000.0));

        //-DEBUG-//
//        if (Math.abs(benefit - 0.0) < 1E-4) {
//            System.out.println("[benefit] -- benefit = " + benefit + " --");
//            System.out.println("[benefit] level = " + _entry.level);
//            System.out.println("[benefit] node.samples.size = " + _node.samples.size());
//            System.out.println("[benefit] best move = " + _entry.bestMove);
//            System.out.println("[benefit] sample size = " + _entry.sampleSize);
//            System.out.println("[benefit] enlarged sample size = " + _entry.enlargedSampleSize);
//            System.out.println("[benefit] sample size NW = " + _entry.sampleSizeNW);
//            System.out.println("[benefit] sample size NE = " + _entry.sampleSizeNE);
//            System.out.println("[benefit] sample size SW = " + _entry.sampleSizeSW);
//            System.out.println("[benefit] sample size SE = " + _entry.sampleSizeSE);
//            System.out.println("[benefit] resolution = " + resolution);
//        }
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
        int sampleSize = query.sampleSize;
        int samplePercentage = query.samplePercentage;

        MyTimer.startTimer();
        System.out.println("[RA-QuadTree] is answering query: \n" +
                "Q = { \n" +
                "    range: [" + lng0 + ", " + lat0 + "] ~ [" + lng1 + ", " + lat1 + "], \n" +
                "    resolution: [" + resX + " x " + resY + "], \n" +
                "    zoom: " + zoom + ",\n " +
                "    sampleSize: " + sampleSize + ", \n" +
                "    samplePercentage: " + samplePercentage + "\n" +
                " }");

        double iX0 = lngX(lng0);
        double iY0 = latY(lat0);
        double iX1 = lngX(lng1);
        double iY1 = latY(lat1);
        double pixelScale = 1.0 / 256 / Math.pow(2, zoom);
        double rcX = (iX0 + iX1) / 2;
        double rcY = (iY0 + iY1) / 2;
        double rhalfWidth = (iX1 - iX0) / 2;
        double rhalfHeight = (iY0 - iY1) / 2;

        System.out.println("[RA-QuadTree] starting range search on QuadTree with: \n" +
                "bbox = [(" + iX0 + ", " + iY0 + "), (" + iX1 + ", " + iY1 + ")] ; \n" +
                "range = [(" + rcX + ", " + rcY + "), " + rhalfWidth + ", " + rhalfHeight + "] ; \n" +
                "pixelScale = " + pixelScale + ";");

        /** For query stats*/
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfNodesStoppedAtLevels[i] = 0;
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfSamplesStoppedAtLevels[i] = 0;

        /** For query time analysis */
        times.put("estimateBenefit", 0.0);
        times.put("estimatePerfectSampleSize", 0.0);
        times.put("sampleOnNode", 0.0);
        times.put("rendering", 0.0);
        times.put("computeError", 0.0);

        /** For query estimate benefit */
        // find the level where exact visualization samples are
        exactLevel = Math.min((int) Math.ceil(Math.log(1.0 / pixelScale / oneNodeResolution) / Math.log(2)), Constants.MAX_ZOOM);
        //-DEBUG-//
        System.out.println("exact level = " + exactLevel);
        //-DEBUG-//

        MyTimer.startTimer();
        List<Point> points;
        // if given sampleSize parameter, use it to do sampleAfterTraversal
        if (sampleSize > 0) {
            System.out.println("[RA-QuadTree] is doing a Sample-After-Traversal with sampleSize = " + sampleSize + ".");
            if (Constants.SAMPLING_METHOD.equalsIgnoreCase("random")) {
                System.out.println(" ---- Random Sampling ----");
                points = this.quadTree.randomSampleAfterTraverse(0.5, 0.5, 0.5,
                        rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0, sampleSize);
            }
            else if (Constants.SAMPLING_METHOD.equalsIgnoreCase("bfs")) {
                System.out.println(" ---- BFS Sampling ----");
                points = this.quadTree.bfs(0.5, 0.5, 0.5,
                        rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, sampleSize);
            }
            else {
                System.out.println(" ---- Stratified Sampling ----");
                points = this.quadTree.sampleAfterTraverse(0.5, 0.5, 0.5,
                        rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0, sampleSize);
            }
        }
        // otherwise if given sampleRatio, use it to do sampleInTraversal
        else if (samplePercentage > 0 && samplePercentage < 100) {
            System.out.println("[RA-QuadTree] is doing a Sample-In-Traversal with samplePercentage = " + samplePercentage + ".");
            points = this.quadTree.sampleInTraverse(0.5, 0.5, 0.5,
                    rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0, samplePercentage);
        }
        else {
            System.out.println("[RA-QuadTree] is doing DFS for a perfect visualization.");
            points = this.quadTree.dfs(0.5, 0.5, 0.5,
                    rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0);
        }
        MyTimer.stopTimer();
        double treeTime = MyTimer.durationSeconds();

        MyTimer.temporaryTimer.put("treeTime", treeTime);
        System.out.println("[RA-QuadTree] tree search got " + points.size() + " data points.");
        System.out.println("[RA-QuadTree] tree search time: " + treeTime + " seconds.");
        System.out.println("[RA-QuadTree]     - estimate benefit time: " + times.get("estimateBenefit") + " seconds.");
        System.out.println("[RA-QuadTree]         - estimate perfect sample size time: " + times.get("estimatePerfectSampleSize") + " seconds.");
        System.out.println("[RA-QuadTree]         - sample on node time: " + times.get("sampleOnNode") + " seconds.");
        System.out.println("[RA-QuadTree]         - rendering time: " + times.get("rendering") + " seconds.");
        System.out.println("[RA-QuadTree]         - compute error time: " + times.get("computeError") + " seconds.");

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

        System.out.println("[RA-QuadTree] build binary result with  " + resultSize + " points.");
        System.out.println("[RA-QuadTree] build binary result time: " + buildBinaryTime + " seconds.");

        MyTimer.stopTimer();
        System.out.println("[RA-QuadTree] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
        System.out.println("[RA-QuadTree] ---- # of nodes stopping at each level ----");
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + numberOfNodesStoppedAtLevels[i]);
        }
        System.out.println("[RA-QuadTree] ---- # of samples stopping at each level ----");
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + numberOfSamplesStoppedAtLevels[i]);
        }
        return messageBuilder.getBuffer();
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }

    public static int expandResolution(int _resolution) {
        return _resolution + 2 * (Constants.RADIUS_IN_PIXELS + 1);
    }

    public static void printRenderingGray(String name, byte[] _rendering, int _resolution, boolean _expansion) {
        int side = _expansion? expandResolution(_resolution): _resolution;
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
