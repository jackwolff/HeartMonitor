package edu.und.cs.com.heart_monitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.bitalino.comm.BITalinoDevice;
import com.bitalino.comm.BITalinoException;
import com.bitalino.comm.BITalinoFrame;

import com.jjoe64.graphview.*;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import roboguice.activity.RoboActivity;

import static android.widget.Toast.makeText;

public class ECG extends RoboActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private LineGraphSeries signalValueSeries;
    private LineGraphSeries filteredValueSeries;
    private GraphView myGraphView;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private AsyncTask myThread;
    private ECGFilter filter;

    private static int rawAverage = 0;

    private static final int GRAPH_SIZE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_ecg);

        filter = new ECGFilter();

        //let user know it will take a few seconds to start getting readings
        makeText(this, "Establishing Connection to Sensor", Toast.LENGTH_LONG).show();

        signalValueSeries = new LineGraphSeries();

        filteredValueSeries = new LineGraphSeries();
        filteredValueSeries.setColor(Color.GREEN);

        //Get the graph
        myGraphView = (GraphView)findViewById(R.id.graph);
        //Set graph options
        myGraphView.addSeries(signalValueSeries);
        //myGraphView.addSeries(filteredValueSeries);

        //Set x axis max and min
        myGraphView.getViewport().setMinX(0);
        myGraphView.getViewport().setMaxX(GRAPH_SIZE);

        //Set y axis max and min
        myGraphView.getViewport().setMinY(400);
        myGraphView.getViewport().setMaxY(700);

        myGraphView.getViewport().setXAxisBoundsManual(true);
        myGraphView.getViewport().setYAxisBoundsManual(true);

        //myGraphView.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        //myGraphView.getGridLabelRenderer().setVerticalLabelsVisible(false);

        //Find the buttons by their ID
        final Button startButton = (Button) findViewById(R.id.startBTN);
        final Button stopButton = (Button) findViewById(R.id.stopBTN);
        final Button backButton = (Button) findViewById(R.id.backBTN);

        //Listen for button presses
        startButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        backButton.setOnClickListener(this);

        // execute
        if(BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            myThread = new TestAsyncTask().execute();
        }
        else {
            Toast.makeText(this, "Enable bluetooth first.", Toast.LENGTH_LONG).show();
        }
    }

    public static int getRawAverage() {
        return rawAverage;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startBTN:
                if(myThread == null) {
                    myThread = new TestAsyncTask().execute();
                }
                else if(myThread.getStatus() != AsyncTask.Status.RUNNING ||
                        myThread.getStatus() == AsyncTask.Status.PENDING) {
                    myThread = new TestAsyncTask().execute();
                }
                break;
            case R.id.stopBTN:
                if(myThread != null) {
                    myThread.cancel(true);
                }
                break;
            case R.id.backBTN:
                if(myThread != null) {
                    myThread.cancel(true);
                }
                if (getFragmentManager().getBackStackEntryCount() != 0) {
                    getFragmentManager().popBackStack();
                }
                else {
                    super.onBackPressed();
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private class TestAsyncTask extends AsyncTask<Void, String, Void> {

        int index = 0;
        private BluetoothDevice dev = null;
        private BluetoothSocket sock = null;
        private BITalinoDevice bitalino;
        SharedPreferences getPreference = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String macAddress = getPreference.getString("macAddress",null );
        private final int ecgChannel = 1;
        private final int sampleRate = 10;
        private int[] reading;

        private boolean runTest = true;

        DataPoint[] readingPoint;

        DataPoint[] filteredPoint;

        /**
         * Attempt to connect the to the Bitalino board.
         * Will throw an exception if the device is not found or
         * some other problem has occurred.
         * @throws Exception
         */
        private void connectToBitalino() throws Exception {
            // Get the remote Bluetooth device
            final String remoteDevice = macAddress;
            final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

            dev = btAdapter.getRemoteDevice(remoteDevice);
            //establish bluetooth connection
            sock = dev.createRfcommSocketToServiceRecord(MY_UUID);
            sock.connect();
            bitalino = new BITalinoDevice(100, new int[]{ecgChannel});
            bitalino.open(sock.getInputStream(), sock.getOutputStream());
            // start acquisition on predefined analog channels
            bitalino.start();
        }

        protected Void doInBackground(Void... paramses) {
            //Attempt to connect to the Bitalino board
            try {
                connectToBitalino();
            }
            catch(Exception e) {
                publishProgress(new String[] {"Unable to connect to Bitalino Board."});
                Log.d("BlueTooth", "Unable to connect to Bitalino Board.");
                return null;
            }

            filteredPoint = new DataPoint[GRAPH_SIZE];
            readingPoint = new DataPoint[GRAPH_SIZE];

            for(int i = 0; i < readingPoint.length; i++) {
                filteredPoint[i] = new DataPoint(i, 0);
                readingPoint[i] = new DataPoint(i, 0);
            }

            try {
                while (runTest) {

                    if(isCancelled()) {
                        return null;
                    }

                    BITalinoFrame[] frames = null;
                    try {
                        frames = bitalino.read(sampleRate);//read(number of frames to read)
                    }
                    catch(BITalinoException e) {
                        Log.d("BITalinoException", e.getMessage());
                    }

                    if(frames != null) {
                        reading = new int[frames.length];
                        // go into frames to gather data from sensors
                        for (int i = 0; i < frames.length; i++) {
                            //analog 2 == ECG
                            reading[i] = frames[i].getAnalog(ecgChannel);
                            rawAverage += reading[i];
                            //Add the readingPoint to the filter
                            //filter.addPoint(reading[i]);
                        }
                        rawAverage = rawAverage / reading.length;
                        //output results to screen using onProgressUpdate()
                        publishProgress();
                        try {
                            Thread.sleep(5);
                        }
                        catch(Exception e) {

                        }
                    }
                }
            }catch(Exception e) {              //error opening socket
                Log.d("Exception", e.getMessage());
            }
            //close input and output streams and close socket
            //terminate thread
            return null;
        }
        /*
        *       onProgress update allows the asynctask to send data gathered to User interface
         */
        @Override
        protected void onProgressUpdate(String... values) {
            //If we have something in values, we have an error
            if(values.length > 0) {
                Toast.makeText(getApplicationContext(), values[0],
                        Toast.LENGTH_LONG).show();
                return;
            }

            //Need to move up the data points and get rid of the data points
            //on the left hand part of the graph
            for(int i = 0; i < readingPoint.length - reading.length; i++) {
                readingPoint[i] = readingPoint[i + reading.length];
                filteredPoint[i] = filteredPoint[i + reading.length];
            }

            //Add the new points to the end of the array
            int readIndex = 0;
            for(int i = readingPoint.length - reading.length; i < readingPoint.length; i++) {
                readingPoint[i] = new DataPoint((readingPoint[i].getX() + reading.length),
                        reading[readIndex]);
                filteredPoint[i] = new DataPoint((readingPoint[i].getX()),
                        filter.getValue(index));
                index++;
                readIndex++;
            }
            signalValueSeries.resetData(readingPoint);
            //filteredValueSeries.resetData(filteredPoint);
            myGraphView.getViewport().scrollToEnd();
        }

        @Override
        protected void onCancelled() {
            // stop acquisition and close bluetooth connection
            try {
                bitalino.stop();         //signal board to quit sending packets
                sock.close();               //close socket on this end
            } catch (Exception e) {
                Log.e(TAG, "There was an error.", e);
            }
        }

   }

}