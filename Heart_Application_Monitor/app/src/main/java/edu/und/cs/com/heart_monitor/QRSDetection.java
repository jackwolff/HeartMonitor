package edu.und.cs.com.heart_monitor;
import android.util.Log;
/**
 * Created by Jack Wolff on 1/22/2017.
 */

public class QRSDetection {
    public static final int M = 5;
    public static final int N = 30;
    public static final int winSize = 250;
    public static final float HP_CONSTANT = (float) 1/M;

    // High pass filter
// y1[n] = 1/M * Sum[m=0, M-1] cur_x[n-m]
// y2[n] = cur_x[n - (M+1)/2]
    public static float[] highPass(int[] sig0, int nsamp) {
        float[] highPass = new float[nsamp];
        float constant = (float) 1/M;

        for(int i=0; i<sig0.length; i++) {
            float y1 = 0;
            float y2 = 0;

            int y2_index = i-((M+1)/2);
            if(y2_index < 0) {
                y2_index = nsamp + y2_index;
            }
            y2 = sig0[y2_index];

            float y1_sum = 0;
            for(int j=i; j>i-M; j--) {
                int x_index = i - (i-j);
                if(x_index < 0) {
                    x_index = nsamp + x_index;
                }
                y1_sum += sig0[x_index];
            }

            y1 = constant * y1_sum;
            highPass[i] = y2 - y1;

        }

        return highPass;
    }

    public static float[] lowPass(float[] sig0, int nsamp) {
        float[] lowPass = new float[nsamp];
        for(int i=0; i<sig0.length; i++) {
            float sum = 0;
            if(i+30 < sig0.length) {
                for(int j=i; j<i+30; j++) {
                    float current = sig0[j] * sig0[j];
                    sum += current;
                }
            }
            else if(i+30 >= sig0.length) {
                int over = i+30 - sig0.length;
                for(int j=i; j<sig0.length; j++) {
                    float current = sig0[j] * sig0[j];
                    sum += current;
                }
                for(int j=0; j<over; j++) {
                    float current = sig0[j] * sig0[j];
                    sum += current;
                }
            }

            lowPass[i] = sum;
        }

        return lowPass;

    }

    public static int[] QRS(float[] lowPass, int nsamp) {
        int[] QRS = new int[nsamp];

        double treshold = 0;

        for(int i=0; i<200; i++) {
            if(lowPass[i] > treshold) {
                treshold = lowPass[i];
            }
        }

        int frame = 250;

        for(int i=0; i<lowPass.length; i+=frame) {
            float max = 0;
            int index = 0;
            if(i + frame > lowPass.length) {
                index = lowPass.length;
            }
            else {
                index = i + frame;
            }
            for(int j=i; j<index; j++) {
                if(lowPass[j] > max) max = lowPass[j];
            }
            boolean added = false;
            for(int j=i; j<index; j++) {
                if(lowPass[j] > treshold && !added) {
                    QRS[j] = 1;
                    added = true;
                }
                else {
                    QRS[j] = 0;
                }
            }

            double gama = (Math.random() > 0.5) ? 0.15 : 0.20;
            double alpha = 0.01 + (Math.random() * ((0.1 - 0.01)));

            treshold = alpha * gama * max + (1 - alpha) * treshold;
        }



        return QRS;
    }

    /**
     * Takes in the filtered ECG signal and returns the slope information. Slopes correspond to x+h values
     * (Offset by h from original signal), where h is 1/2 of dx (time in seconds between each sample).
     *
     * Uses symmetric derivative equation which is:
     *
     * f'(x) = (f'(x + h) - f'(x - h))/(2h)
     *
     *   OR the equivalent:
     *
     * f'(x + dx/2) - f'(x - dx/2)/(dx) where dx is the change in time between each sample
     *
     * This finds the slope at the tangent line in the middle of these two points (hence the offset by h or dx/2).
     *
     * @param filtered The filtered signal data from file or leads
     * @param sRate The sample rate in Hz (currently hardcoded to 500 Hz)
     * @return Slope information (derivative curve)
     */
    public static float[] derivative(int[] filtered, int sRate)
    {
        float[] deriv = new float[filtered.length-1]; // derivative requires 2 points so 1 raw -> 0 derivatives, 2 raw -> 1 derivatives, etc.
        sRate = 500;
        int dx = 1/sRate;

        deriv[0] = 0;
        deriv[1] = 1;

        for (int i = 0; i < filtered.length - 4; i++)
        {
            //deriv[i] = (filtered[i+1] - filtered[i])/(dx);
            //deriv[i] = (filtered[i+1] - filtered[i])*(filtered[i+1] - filtered[i]);   //hardcoding for now
            deriv[i+2] = (filtered[i+4]+2*filtered[i+3]-2*filtered[i+1]-filtered[i])*(filtered[i+4]+2*filtered[i+3]-2*filtered[i+1]-filtered[i]);
        }

        return deriv;
    }
}
