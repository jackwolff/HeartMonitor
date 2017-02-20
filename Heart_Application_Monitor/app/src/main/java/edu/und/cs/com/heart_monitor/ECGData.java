package edu.und.cs.com.heart_monitor;
import java.util.ArrayList;
import android.util.Log;
/**
 * Created by Ryan Mindt on 2/16/2017.
 *
 * ECGdata contains the information from ECG readings and filters it as the data is added
 */

public class ECGData {

    private static ArrayList<Integer> rawList = new ArrayList<Integer>();
    private static ArrayList<Integer> LPList = new ArrayList<Integer>();
    private static ArrayList<Integer> HPList = new ArrayList<Integer>();

    private static ArrayList<Integer> timeList = new ArrayList<Integer>();

    public static void addPoint(int timeValue, int rawValue) {
        rawList.add(rawValue);
        timeList.add(timeValue);
        lowPass();
        highPass();
    }

    public static int getHighPassVal(int x) {
        int val = 0;
        try {
            val = HPList.get(x);
        }
        catch(Exception e) {
            Log.d("ERROR", e.getMessage());
        }
        return val;
    }

    public static int getLowPassVal() {
        return LPList.get(LPList.size() - 1);
    }

    private static void lowPass(){
        if(rawList.size() < 12) {
            LPList.add(rawList.get(rawList.size() - 1));
            return;
        }

        int x, x6, x12, y1, y2, y; //x -> past inputs(unfiltered y) y->past outputs (filtered y)
        x = rawList.get(rawList.size() - 1);
        x6 = rawList.get(rawList.size() - 7);
        x12 = rawList.get(rawList.size() - 13);
        y1 = LPList.get(LPList.size() - 2);
        y2 = LPList.get(LPList.size() - 3);
        y = 2*y1 - y2 + x - 2*x6 + x12;
        LPList.add(y);
    }
    private static void highPass(){
        if(LPList.size() < 32) {
            HPList.add(LPList.get(LPList.size() - 1));
            return;
        }
        int x, x16, x32, y1, y; //x-> outputs from lowPass y->outputs from highPass
        x = LPList.get(LPList.size() - 1);
        x16 = LPList.get(LPList.size() - 17);
        x32 = LPList.get(LPList.size() - 33);
        y1 = HPList.get(HPList.size() - 2);
        y = 32*x16 - (y1 + x - x32);
        HPList.add(y);
    }
}
