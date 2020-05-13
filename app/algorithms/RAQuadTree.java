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

        /**
         * breadth first search with sampling in traversal
         *
         * explore nodes with higher estimated profit first
         * - profit means how much more quality we can get if we expand this node
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
         * @param sampleSize - return results only with this sample size
         * @return
         */
        public List<Point> bfs(double ncX, double ncY, double nhalfDimension,
                                            double rcX, double rcY, double rhalfWidth, double rhalfHeight,
                                            double rPixelScale, int level, int sampleSize) {

            class QEntry {
                int level;
                double ncX;
                double ncY;
                double nhalfDimension;
                QuadTree node;
                double estimatedProfit;
                int perfectResultSize;

                QEntry(int _level, double _ncX, double _ncY, double _nhalfDimension, QuadTree _node, double _estimatedProfit, int _perfectResultSize) {
                    level = _level;
                    ncX = _ncX;
                    ncY = _ncY;
                    nhalfDimension = _nhalfDimension;
                    node = _node;
                    estimatedProfit = _estimatedProfit;
                    perfectResultSize = _perfectResultSize;
                }
            }

            List<Point> pointsInRange = new ArrayList<>();

            // explore larger estimatedProfit node first
            PriorityQueue<QEntry> queue = new PriorityQueue<>(new Comparator<QEntry>() {
                @Override
                public int compare(QEntry o1, QEntry o2) {
                    if (o2.estimatedProfit > o1.estimatedProfit)
                        return 1;
                    else if (o2.estimatedProfit < o1.estimatedProfit)
                        return -1;
                    else
                        return 0;
                }
            });

            // estimate how many samples at the perfect level are within query range
            int totalPerfectResultSize = estimatePerfectResultSize(level, this, ncX, ncY, nhalfDimension, rcX, rcY, rhalfWidth, rhalfHeight);

            double estimatedProfit = estimateProfit(level, this, ncX, ncY, nhalfDimension, rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, totalPerfectResultSize, sampleSize);

            // add root node
            queue.add(new QEntry(level, ncX, ncY, nhalfDimension, this, estimatedProfit, totalPerfectResultSize));

            while (queue.size() > 0) {

                QEntry currentEntry = queue.poll();
                int _level = currentEntry.level;
                double _ncX = currentEntry.ncX;
                double _ncY = currentEntry.ncY;
                double _nhalfDimension = currentEntry.nhalfDimension;
                QuadTree currentNode = currentEntry.node;
                int _perfectResultSize = currentEntry.perfectResultSize;
                double _estimatedProfit = currentEntry.estimatedProfit;

                // if the largest estimatedProfit is smaller than some threshold, entering collecting samples mode
                if (_estimatedProfit <= Constants.STOP_CRITERIA) {
                    int localTargetSampleSize = targetSampleSize(_perfectResultSize, totalPerfectResultSize, sampleSize);
                    //-DEBUG-//
//                    System.out.println("[queue] perfect result size = " + _perfectResultSize);
//                    System.out.println("[queue] total perfect result size = " + totalPerfectResultSize);
//                    System.out.println("[queue] sample size = " + sampleSize);
//                    System.out.println("[queue] ---> local target sample size = " + localTargetSampleSize);
                    //-DEBUG-//
                    numberOfNodesStoppedAtLevels[_level] ++;
                    if (currentNode.samples != null) {
                        //-DEBUG-//
                        //System.out.println("[queue] ===> node sample size = " + currentNode.samples.size());
                        //-DEBUG-//
                        if (localTargetSampleSize >= currentNode.samples.size()) {
                            numberOfSamplesStoppedAtLevels[_level] += currentNode.samples.size();
                            pointsInRange.addAll(currentNode.samples);
                        }
                        else {
                            localTargetSampleSize = localTargetSampleSize == 0? 1: localTargetSampleSize;
                            numberOfSamplesStoppedAtLevels[_level] += localTargetSampleSize;
                            pointsInRange.addAll(currentNode.samples.subList(0, localTargetSampleSize));
                        }
                    }
                    continue;
                }

                // if no children, ignore
                if (currentNode.northWest == null) {
                    continue;
                }

                // if this node's pixel scale is already smaller than the range query's pixel scale,
                // add samples to result set
//                if ((_nhalfDimension * 2 / oneNodeResolution) <= rPixelScale) {
//                    int localTargetSampleSize = targetSampleSize(_perfectResultSize, totalPerfectResultSize, sampleSize);
//                    numberOfNodesStoppedAtLevels[_level] ++;
//                    if (currentNode.samples != null) {
//                        if (localTargetSampleSize >= currentNode.samples.size()) {
//                            numberOfSamplesStoppedAtLevels[_level] += currentNode.samples.size();
//                            pointsInRange.addAll(currentNode.samples);
//                        }
//                        else {
//                            localTargetSampleSize = localTargetSampleSize == 0? 1: localTargetSampleSize;
//                            numberOfSamplesStoppedAtLevels[_level] += localTargetSampleSize;
//                            pointsInRange.addAll(currentNode.samples.subList(0, localTargetSampleSize));
//                        }
//                    }
//                    continue;
//                }

                // Otherwise, expand current node to its children
                double cX, cY;
                double halfDimension = _nhalfDimension / 2;
                int perfectResultSizeNW = 0;
                int perfectResultSizeNE = 0;
                int perfectResultSizeSW = 0;
                int perfectResultSizeSE = 0;

                // northwest
                cX = _ncX - halfDimension;
                cY = _ncY - halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    perfectResultSizeNW = estimatePerfectResultSize(_level + 1, currentNode.northWest, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight);
                    estimatedProfit = estimateProfit(_level + 1, currentNode.northWest, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, totalPerfectResultSize, sampleSize);
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northWest, estimatedProfit, perfectResultSizeNW));
                }

                // northeast
                cX = _ncX + halfDimension;
                cY = _ncY - halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    perfectResultSizeNE = estimatePerfectResultSize(_level + 1, currentNode.northEast, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight);
                    estimatedProfit = estimateProfit(_level + 1, currentNode.northEast, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, totalPerfectResultSize, sampleSize);
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.northEast, estimatedProfit, perfectResultSizeNE));
                }

                // southwest
                cX = _ncX - halfDimension;
                cY = _ncY + halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    perfectResultSizeSW = estimatePerfectResultSize(_level + 1, currentNode.southWest, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight);
                    estimatedProfit = estimateProfit(_level + 1, currentNode.southWest, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, totalPerfectResultSize, sampleSize);
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southWest, estimatedProfit, perfectResultSizeSW));
                }

                // southeast
                cX = _ncX + halfDimension;
                cY = _ncY + halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight)) {
                    perfectResultSizeSE = estimatePerfectResultSize(_level + 1, currentNode.southEast, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight);
                    estimatedProfit = estimateProfit(_level + 1, currentNode.southEast, cX, cY, halfDimension, rcX, rcY, rhalfWidth, rhalfHeight, rPixelScale, totalPerfectResultSize, sampleSize);
                    queue.add(new QEntry(_level + 1, cX, cY, halfDimension, currentNode.southEast, estimatedProfit, perfectResultSizeSE));
                }

                // update totalPerfectResultSize with finer estimation
                totalPerfectResultSize -= _perfectResultSize;
                totalPerfectResultSize += (perfectResultSizeNW + perfectResultSizeNE + perfectResultSizeSW + perfectResultSizeSE);
            }

            return pointsInRange;
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

        /** For stats */
        numberOfNodesStoppedAtLevels = new int[Constants.MAX_ZOOM + 2];
        numberOfSamplesStoppedAtLevels = new int[Constants.MAX_ZOOM + 2];

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

    public static int estimatePerfectResultSize(int _level, QuadTree _node, double _ncX, double _ncY, double _nhalfDimension,
                                                double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight) {
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

        return perfectSampleSize;
    }

    public static int targetSampleSize(int _perfectResultSize, int _totalPerfectResultSize, int _totalSampleSize) {
        return (int) Math.round(((double) _perfectResultSize / (double) _totalPerfectResultSize) * _totalSampleSize);
    }

    /**
     * obtain _targetSampleSize sample points from given _node
     *
     * @param _node
     * @param _targetSampleSize
     * @return null - if given _node has no samples at all or sample size is not enough for target sample size
     */
    public static List<Point> sampleOnNode(QuadTree _node, int _targetSampleSize) {
        if (_node.samples == null) {
            return null;
        }
        else {
            if (_targetSampleSize > _node.samples.size()) {
                return null;
            }
            else {
                return _node.samples.subList(0, _targetSampleSize);
            }
        }
    }

    /**
     * estimate how much more quality we can get if we expand given _node to its children
     *
     * @param _level
     * @param _node
     * @param _ncX
     * @param _ncY
     * @param _nhalfDimension
     * @param _rcX
     * @param _rcY
     * @param _rhalfWidth
     * @param _rhalfHeight
     * @param _rPixelScale
     * @param _totalPerfectResultSize - total perfectResultSize for this query range
     * @param _totalSampleSize - total target sample size
     * @return
     */
    public static double estimateProfit(int _level, QuadTree _node, double _ncX, double _ncY, double _nhalfDimension,
                                        double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight,
                                        double _rPixelScale, int _totalPerfectResultSize, int _totalSampleSize) {
        // 0 corner case
        if (_level >= exactLevel) return Double.MIN_VALUE;

        // 1 how many samples are at the perfect level under this _node
        int perfectResultSize = estimatePerfectResultSize(_level, _node, _ncX, _ncY, _nhalfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);

        // 2 how many samples we need to sample on given _node
        int targetSampleSize = targetSampleSize(perfectResultSize, _totalPerfectResultSize, _totalSampleSize);

        // 3 get the sample on _node with targetSampleSize
        List<Point> samplesOnParent = sampleOnNode(_node, targetSampleSize);
        // if there is no sample on this _node at all, this node will need be expanded for sure
        if (samplesOnParent == null) {
            return Double.MAX_VALUE;
        }

        // 4 for each children of the _node, compute the same targetSampleSize
        double cX, cY;
        double halfDimension = _nhalfDimension / 2;
        // if no children, this node should not be expanded at all
        if (_node.northWest == null) {
            return Double.MIN_VALUE;
        }
        // northwest
        cX = _ncX - halfDimension;
        cY = _ncY - halfDimension;
        int perfectResultSizeNW = estimatePerfectResultSize(_level + 1, _node.northWest, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
        int targetSampleSizeNW = targetSampleSize(perfectResultSizeNW, _totalPerfectResultSize, _totalSampleSize);
        List<Point> samplesOnChildNW = sampleOnNode(_node.northWest, targetSampleSizeNW);

        // northeast
        cX = _ncX + halfDimension;
        cY = _ncY - halfDimension;
        int perfectResultSizeNE = estimatePerfectResultSize(_level + 1, _node.northEast, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
        int targetSampleSizeNE = targetSampleSize(perfectResultSizeNE, _totalPerfectResultSize, _totalSampleSize);
        List<Point> samplesOnChildNE = sampleOnNode(_node.northEast, targetSampleSizeNE);

        // southwest
        cX = _ncX - halfDimension;
        cY = _ncY + halfDimension;
        int perfectResultSizeSW = estimatePerfectResultSize(_level + 1, _node.southWest, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
        int targetSampleSizeSW = targetSampleSize(perfectResultSizeSW, _totalPerfectResultSize, _totalSampleSize);
        List<Point> samplesOnChildSW = sampleOnNode(_node.southWest, targetSampleSizeSW);

        // southeast
        cX = _ncX + halfDimension;
        cY = _ncY + halfDimension;
        int perfectResultSizeSE = estimatePerfectResultSize(_level + 1, _node.southEast, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight);
        int targetSampleSizeSE = targetSampleSize(perfectResultSizeSE, _totalPerfectResultSize, _totalSampleSize);
        List<Point> samplesOnChildSE = sampleOnNode(_node.southEast, targetSampleSizeSE);

        // concatenate all samples on children
        List<Point> samplesOnChildren = new ArrayList<>();
        if (samplesOnChildNW != null) samplesOnChildren.addAll(samplesOnChildNW);
        if (samplesOnChildNE != null) samplesOnChildren.addAll(samplesOnChildNE);
        if (samplesOnChildSW != null) samplesOnChildren.addAll(samplesOnChildSW);
        if (samplesOnChildSE != null) samplesOnChildren.addAll(samplesOnChildSE);

        // 5 get the resolution for given _node as piece of the result
        int resolution = (int) Math.round(_nhalfDimension / _rPixelScale);

        // 6 render samplesOnParent to renderingParent
        byte[] renderingParent = renderer.createRendering(resolution, true);
        for (Point point: samplesOnParent) {
            renderer.render(renderingParent, _ncX, _ncY, _nhalfDimension, resolution, true, point);
        }

        // 7 render samplesOnChildren to renderingChildren
        byte[] renderingChildren = renderer.createRendering(resolution, true);
        for (Point point: samplesOnChildren) {
            renderer.render(renderingChildren, _ncX, _ncY, _nhalfDimension, resolution, true, point);
        }

        // 8 compute the error between renderingParent and renderingChildren as the profit
        double error = errorMetric.totalError(renderingParent, renderingChildren, resolution);

        //-DEBUG-//
//        if (Math.abs(error - 0.0) < 1E-4) {
//            System.out.println("[error] error = " + error);
//            System.out.println("[error] perfect result size = " + perfectResultSize);
//            System.out.println("[error] target sample size = " + targetSampleSize);
//            System.out.println("[error] sample on parent = " + samplesOnParent.size());
//            if (samplesOnChildNW != null) System.out.println("[error] sample on child north-west = " + samplesOnChildNW.size());
//            if (samplesOnChildNE != null) System.out.println("[error] sample on child north-east = " + samplesOnChildNE.size());
//            if (samplesOnChildSW != null) System.out.println("[error] sample on child south-west = " + samplesOnChildSW.size());
//            if (samplesOnChildSE != null) System.out.println("[error] sample on child south-east = " + samplesOnChildSE.size());
//            System.out.println("[error] resolution = " + resolution);
//        }

        return error;
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

        /** For stats*/
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfNodesStoppedAtLevels[i] = 0;
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfSamplesStoppedAtLevels[i] = 0;

        /** For sample analysis */
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
                        rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, 0, sampleSize);
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
