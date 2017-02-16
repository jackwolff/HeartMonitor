package edu.und.cs.com.heart_monitor;
import java.util.ArrayList;

/**
 * Created by Ryan Mindt on 2/16/2017.
 *
 * ECGdata contains the information from ECG readings and filters it as the data is added
 */

public class ECGData {

    private ArrayList<Integer> rawList = new ArrayList<Integer>(250);
    private ArrayList<Integer> LPList = new ArrayList<Integer>(250);
    private ArrayList<Integer> HPList = new ArrayList<Integer>(250);

    private ArrayList<Integer> timeList = new ArrayList<Integer>(250);

    public void addPoint(int timeValue, int rawValue) {
        rawList.add(rawValue);
        timeList.add(timeValue);
        lowPass();
        highPass();
    }

    private void lowPass(){
        if(rawList.size() < 12) {
            LPList.add(rawList.size());
            return;
        }

        int x, x6, x12, y1, y2, y; //x -> past inputs(unfiltered y) y->past outputs (filtered y)
        x = rawList.get(rawList.size());
        x6 = rawList.get(rawList.size() - 6);
        x12 = rawList.get(rawList.size() - 12);
        y1 = LPList.get(LPList.size() - 1);
        y2 = LPList.get(LPList.size() - 2);
        y = 2*y1 - y2 +x - 2*x6 + x12;
        LPList.add(y);
    }
    private void highPass(){
        if(LPList.size() <32) {
            HPList.add(LPList.get(LPList.size()));
            return;
        }
        int x, x16, x32, y1, y; //x-> outputs from lowPass y->outputs from highPass
        x = LPList.get(LPList.size());
        x16 = LPList.get(LPList.size() - 16);
        x32 = LPList.get(LPList.size() - 32);
        y1 = HPList.get(HPList.size() - 1);
        y = 32*x16 - (y1 + x - x32);
        HPList.add(y);
    }




}
