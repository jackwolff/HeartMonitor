package edu.und.cs.com.heart_monitor;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Ryan Mindt on 2/16/2017.
 *
 * ECGFilter contains information from an ECG reading and filters it as data is added
 */

public class ECGFilter {

    //Raw values from the ECG reading
    private ArrayList<Integer> rawList = new ArrayList<Integer>();
    //Values from the low pass filter
    private ArrayList<Integer> lowPassList = new ArrayList<Integer>();
    //Values that have gone through both the high and low pass and are filtered
    private ArrayList<Integer> filterList = new ArrayList<Integer>();

    int lowPassMovAverage = 0;

    /**
     * Takes a value from an ECG reading and filters it
     * @param rawValue value from the ECG reading
     */
    public void addPoint(int rawValue) {
        rawList.add(rawValue);
        lowPass();
        highPass();
    }

    public int getValue(int i) {
        if(i >= filterList.size())
            return 0;
        else if(filterList.size() == 0)
            return 0;
        return filterList.get(i);
    }

    /**
     * Uses Pan-Tompkins difference equation to filter out
     * high frequencies
     */
    private void lowPass() {
        //Need enough data to properly filter
        if (rawList.size() < 13) {
            lowPassList.add(rawList.get(rawList.size() - 1));
            return;
        }

        //x -> past inputs(unfiltered y) y->past outputs (filtered y)
        int x, x6, x12, y1, y2, y;
        x = rawList.get(rawList.size() - 1);
        x6 = rawList.get(rawList.size() - 7);
        x12 = rawList.get(rawList.size() - 13);
        y1 = lowPassList.get(lowPassList.size() - 1);
        y2 = lowPassList.get(lowPassList.size() - 2);
        y = 2 * y1 - y2 + x - 2 * x6 + x12;
        lowPassList.add(y);
    }

    /**
     * Uses Pan-Tompkins difference equation to filter out
     * low frequencies
     */
    private void highPass(){
        //Need enough data to properly filter
        if(lowPassList.size() < 33) {
            filterList.add(lowPassList.get(lowPassList.size() - 1));
            return;
        }

        //x-> outputs from lowPass y->outputs from highPass
        int x, x16, x32, y1, y;
        x = lowPassList.get(lowPassList.size() - 1);
        x16 = lowPassList.get(lowPassList.size() - 17);
        x32 = lowPassList.get(lowPassList.size() - 33);
        y1 = filterList.get(filterList.size() - 1);
        y = 32*x16 - (y1 + x - x32);
        filterList.add(y);
    }
}
