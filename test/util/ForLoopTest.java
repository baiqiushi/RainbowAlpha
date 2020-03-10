package util;

import model.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForLoopTest {
    public static void main(String[] agrs) {
        int size = 10000000; // 10M

        // (1) generate size Point instances and put into a List
        MyTimer.startTimer();
        List<Point> buffer = new ArrayList<>();
        for (int i = 0; i < size; i ++) {
            buffer.add(new Point(Math.random(), Math.random()));
        }
        MyTimer.stopTimer();
        double generateTime = MyTimer.durationSeconds();
        System.out.println("(1) Generated " + size / 1000000 + "M Point instances. Takes " + generateTime + " seconds.");

        // generate a random indexing sequence to loop all points in the buffer
        MyTimer.startTimer();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < size; i ++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes);
        MyTimer.stopTimer();
        double shuffleTime = MyTimer.durationSeconds();
        System.out.println("Shuffled indexes. Takes " + shuffleTime + " seconds.");

        // (2) loop the list of Point instances, count how many have sum > 100.0;
        MyTimer.startTimer();
        int countOver100 = 0;
        double sum = 0.0;
        for (int i = 0; i < size; i ++) {
            Point point = buffer.get(indexes.get(i));
            sum = point.getX() + point.getY();
            if (sum > 100.0) {
                countOver100 ++;
            }
        }
        MyTimer.stopTimer();
        double loopTime = MyTimer.durationSeconds();
        System.out.println("(2) Looped " + size / 1000000 + "M Point instances, " + countOver100 + " of them has x+y>100. Takes " + loopTime + " seconds");
    }
}
