package edu.und.cs.com.heart_monitor;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.util.Log;
import android.content.res.AssetManager;
import android.content.DialogInterface;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.io.IOException;
import java.util.ArrayList;

import roboguice.activity.RoboActivity;

/**
 * Created by Jack Wolff on 1/15/2017.
 * Class used to test ECG readings without using the bitalino board.
 */

public class ECGTest extends RoboActivity implements View.OnClickListener {

    //Series that has been through the high and low pass filters
    //private LineGraphSeries highPassFilterSeries;
    //private LineGraphSeries lowPassFilterSeries;
    //Series that reads directly from the file
    private LineGraphSeries fileSeries;
    private GraphView myGraphView;

    private int lastQRS = 0;
    private double averageRR = -1;

    private ArrayList<Integer> RR = new ArrayList<Integer>();

    private PointsGraphSeries QRSMark;

    // THR_SIG
    private double THR_SIG = Integer.MIN_VALUE;
    private LineGraphSeries thr_sig_series;

    //THR_NOISE
    private double THR_NOISE = Integer.MIN_VALUE;;
    private LineGraphSeries thr_noise_series;

    private float SPKI;
    private float NPKI;

    //Derivative Series
    //private LineGraphSeries derivativeSeries;

    AsyncTask task;
    private boolean isAsyncTaskCancelled = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_test);

        //highPassFilterSeries = new LineGraphSeries();
        //lowPassFilterSeries = new LineGraphSeries();
        thr_sig_series = new LineGraphSeries();
        thr_sig_series.setColor(Color.BLUE);
        thr_noise_series = new LineGraphSeries();
        thr_noise_series.setColor(Color.GREEN);
        fileSeries = new LineGraphSeries();
        fileSeries.setColor(Color.BLACK);
        QRSMark = new PointsGraphSeries();
        QRSMark.setColor(Color.BLACK);
        QRSMark.setSize(10);
        //derivativeSeries = new LineGraphSeries();
        //derivativeSeries.setColor(Color.BLACK);
        //lowPassFilterSeries.setColor(Color.GREEN);
        myGraphView = (GraphView)findViewById(R.id.graph);
        //myGraphView.addSeries(highPassFilterSeries);
        myGraphView.addSeries(fileSeries);
        myGraphView.addSeries(thr_sig_series);
        myGraphView.addSeries(thr_noise_series);
        myGraphView.addSeries(QRSMark);
        //myGraphView.addSeries(lowPassFilterSeries);
        //Set graph options
        myGraphView.getViewport().setXAxisBoundsManual(true);
        myGraphView.getViewport().setYAxisBoundsManual(true);

        myGraphView.getViewport().setMinX(0);
        myGraphView.getViewport().setMaxX(200);

        myGraphView.getViewport().setMinY(-100);
        myGraphView.getViewport().setMaxY(200);

        //Find the buttons by their ID
        final Button startButton = (Button) findViewById(R.id.startBTN);
        final Button stopButton = (Button) findViewById(R.id.stopBTN);
        final Button fileButton = (Button) findViewById(R.id.fileBTN);
        final Button backButton = (Button) findViewById(R.id.backBTN);

        //Listen for button presses
        startButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        fileButton.setOnClickListener(this);
        backButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startBTN:
                onStartButton();
                break;
            case R.id.stopBTN:
                onStopButton();
                break;
            case R.id.fileBTN:
                onFileButton();
                break;
            case R.id.backBTN:
                onBackButton();
                break;
        }
    }

    /**
     * Button to start the test was pressed.
     */
    private void onStartButton() {

    }

    /**
     * Button to stop the test was pressed.
     */
    private void onStopButton() {
        isAsyncTaskCancelled = true;
    }

    /**
     * Button to chose a file was pressed.
     */
    private void onFileButton() {

        try {
            AssetManager mnger = getAssets();
            InputStream stream = mnger.open("samples/Sample1-Filtered.txt");
        }
        catch(Exception e) {
            Log.d("TAG", e.getMessage());
        }

        //Get the AssetManager and get all the sample files
        AssetManager mngr = getAssets();

        try {
            final String[] samples = mngr.list("samples");
            //Create a dialog to select a file to read from
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Pick Sample");
            builder.setItems(samples, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int index) {
                    //Close the dialog
                    dialog.cancel();
                    //Set the chosen file
                    task = new TestAsyncTask().execute(samples[index]);
                }
            });
            builder.show();
        }
        catch(Exception e) {
        }
    }


    /**
     * Button to go back was pressed.
     */
    private void onBackButton() {
        if (getFragmentManager().getBackStackEntryCount() != 0) {
            isAsyncTaskCancelled = true;
            getFragmentManager().popBackStack();
        }
        else {
            isAsyncTaskCancelled = true;
            super.onBackPressed();
        }
    }

    private class TestAsyncTask extends AsyncTask<String, String, Void> {
        //Current cur_x value in the graph
        int cur_x;

        private final int sRate         = 250;
        private final int maxSample    = 20000;
        int x = 0;

        //private int[] qrs;
        //private float[] highFilter;
        //private float[] lowFilter;
        private int[] file = new int[maxSample];
        //private float[] derivative;

        BufferedReader reader;
        /**
         * Read from the file and update the graph.
         * @param params A single array containing the filename
         * @return null
         */
        protected Void doInBackground(String... params) {
            //Get the filename and open the file for parsing
            String fileName = params[0];
            AssetManager mnger = getAssets();
            //Start at 0
            cur_x = 0;
            try {
                InputStream stream = mnger.open("samples/"+fileName);
                reader = new BufferedReader(new InputStreamReader(stream));
            }
            catch(Exception e) {
                task.cancel(true);
                return null;
            }
            readFromFile();

            boolean read = true;

            while(read && x < maxSample) {
                try {
                    //If this task has been cancelled, stop immediately
                    if(isAsyncTaskCancelled){break;}

                    if(cur_x % 2 == 0)
                    {
                        file[x] = readFromFile();
                        Log.d("Adding to File: ", "(" + x + "," + file[x] + ")");

                        //Plot the points
                        publishProgress();
                        try {
                            Thread.sleep(0, 1);
                            Log.d("WAIT", "Waiting...");
                        }
                        catch(Exception e) {
                        }
                        x++;
                    }
                    else
                    {
                        reader.readLine();
                    }
                    cur_x++;
                }
                catch(Exception e) {
                    read = false;
                    break;
                }
            }

            task.cancel(true);
            return null;
        }

        private int readFromFile() {
            try
            {
                String[] line = reader.readLine().split(",");
                int i = Integer.parseInt(line[1]);
                Log.d("Point: ", "(" + x + "," + i + ")");

                if (x < 500)
                {
                    THR_SIG = THR_SIG >= i ? THR_SIG : i;
                }
                else if (x == 500)
                {
                    lastQRS = 0;
                    THR_SIG = THR_SIG*0.8;
                    THR_NOISE = THR_SIG/2;
                }
                else
                {
                    Log.d("Checking for maxima:  ", file[x-2] + "<" + file[x-1] + "<" + i);
                    Log.d("Is maxima: ", "" + file[x-1] + ">" + THR_SIG + ":" + (file[x-1] > THR_SIG));
                    if (file[x-1] > i && file[x-2] < file[x-1] && file[x-1] > THR_SIG) //file[x-1] is a local maxima
                    {
                        addQRS(x-1);
                    }
                    else
                    {
                        lastQRS++;
                    }
                    Log.d("THR_SIG: ", "" + THR_SIG);
                    Log.d("THR_NOISE: ", "" + THR_NOISE);
                    Log.d("Samples since last QRS", "" + lastQRS);
                    if (averageRR > 0 && lastQRS > 1.66*averageRR)
                    {
                        //Log.d("Initiate Traceback", lastQRS + " > " + 1.66*averageRR);
                        traceback();
                    }
                }

                return i;
            }
            catch (IOException e)
            {
                Log.d("ECGTest", e.getMessage());
            }

            /**
            file = new int[sample];
            String[] line;
            for (int x = 0; x < sample; x++) {
                try {
                    line = reader.readLine().split(",");
                    file[x] = Integer.parseInt(line[1]);
                }
                catch(Exception e) {
                    Log.d("ECGTest", e.getMessage());
                }
            }

            //highFilter = QRSDetection.highPass(file, sample);
            //lowFilter = QRSDetection.lowPass(highFilter, sample);
            //qrs = QRSDetection.QRS(lowFilter, sample);
            //derivative = QRSDetection.derivative(file, 500);
             */
            return 100;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            //DataPoint from the file
            Log.d("Plotting: ", "(" + x + "," + file[x] + ")");
            DataPoint fileDataPoint = new DataPoint(x, file[x]);
            if (x > 500)
            {
                DataPoint THR_SIG_Point = new DataPoint(x, THR_SIG);
                DataPoint THR_NOISE_POINT = new DataPoint(x, THR_NOISE);

                thr_sig_series.appendData(THR_SIG_Point, true, 200);
                thr_noise_series.appendData(THR_NOISE_POINT, true, 200);
            }
            fileSeries.appendData(fileDataPoint, true, 200);
        }

        protected void calcRR(int x)
        {
            //Log.d("----------BPM---------",  "Adding " + x);
            RR.add(RR.size(), new Integer(x));


            if (RR.size() > 1)
            {
                averageRR = 0;
                for (int i = 0; i < RR.size()-1; i++)
                {
                    averageRR += RR.get(i+1) - RR.get(i);
                    //Log.d("----------BPM---------",  "Update Ave RR: " + RR.get(i+1) + "-" + RR.get(i) + " = " + (RR.get(i+1) - RR.get(i)));

                }

                averageRR = averageRR/(RR.size()-1);

                double bpm = 60/(averageRR/sRate);

                Log.d("----------BPM---------", bpm + "");
            }
        }

        protected void traceback()
        {
            int maxPeakIndex = -1;

            for (int i = RR.get(RR.size()-1)+5; i < RR.get(RR.size()-1)+lastQRS; i++) //change +5 to delay
            {
                if (file[i-1] < file[i] && file[i] < file[i+1] && file[i] > THR_NOISE)
                {
                    if (maxPeakIndex == -1)
                    {
                        maxPeakIndex = i;
                    }
                    else
                    {
                        if (file[maxPeakIndex] < file[i])
                        {
                            maxPeakIndex = i;
                        }
                    }
                }
            }

            if (maxPeakIndex != -1)
            {
                addQRS(maxPeakIndex);
            }
        }

        protected void addQRS(int i)
        {
            QRSMark.appendData(new DataPoint(i, file[i]), true, 200);
            THR_SIG = THR_SIG*0.875 + file[i]*0.125;
            calcRR(i);

            lastQRS = 0;
        }
    }
}