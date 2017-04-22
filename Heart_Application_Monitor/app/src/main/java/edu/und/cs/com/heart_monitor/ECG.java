package edu.und.cs.com.heart_monitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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
import android.widget.TextView;

import com.bitalino.comm.BITalinoDevice;
import com.bitalino.comm.BITalinoException;
import com.bitalino.comm.BITalinoFrame;

import com.jjoe64.graphview.*;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.util.UUID;

import roboguice.activity.RoboActivity;

import static android.widget.Toast.makeText;

public class ECG extends RoboActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private LineGraphSeries liveValueSeries;
    //private LineGraphSeries thresValueSeries;
    //private LineGraphSeries sigValueSeries;

    //private PointsGraphSeries pointSeries;

    private GraphView myGraphView;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private AsyncTask myThread;

    private static final int GRAPH_SIZE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_ecg);

        //let user know it will take a few seconds to start getting readings
        makeText(this, "Establishing Connection to Sensor", Toast.LENGTH_LONG).show();

        liveValueSeries = new LineGraphSeries();

        //thresValueSeries = new LineGraphSeries();
        //thresValueSeries.setColor(Color.GREEN);

        //sigValueSeries = new LineGraphSeries();
        //sigValueSeries.setColor(Color.RED);

        //pointSeries = new PointsGraphSeries();

        //Get the graph
        myGraphView = (GraphView)findViewById(R.id.graph);
        //Set graph options
        myGraphView.addSeries(liveValueSeries);
        //myGraphView.addSeries(sigValueSeries);
        //myGraphView.addSeries(thresValueSeries);
        //myGraphView.addSeries(pointSeries);

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
        private final int frameSize = 10;
        private int[] reading;
        private final int SRATE = 100;
        private double THR_SIG = Integer.MIN_VALUE;
        private double THR_NOISE = 0;
        private int lastQRS = -1;
        private int candidateX = -1;
        private double candidateY = Integer.MIN_VALUE;
        private int numRR = 0;
        private double aveRR = 0;
        private int prevQRS = 0;

        private TextView lblBPM;
        private TextView lblAnomaly;

        private boolean runTest = true;

        DataPoint[] readingPoint;
        DataPoint[] thresPoint;
        DataPoint[] sigPoint;

        private static final int REC_SIZE = 400;

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

            readingPoint = new DataPoint[REC_SIZE];
            thresPoint = new DataPoint[REC_SIZE];
            sigPoint = new DataPoint[REC_SIZE];

            lblBPM =  (TextView)findViewById(R.id.lblBPM);
            lblAnomaly = (TextView)findViewById(R.id.lblAnomaly);

            for(int i = 0; i < readingPoint.length; i++) {
                readingPoint[i] = new DataPoint(i, 0);
                thresPoint[i] = new DataPoint(i, 0);
                sigPoint[i] = new DataPoint(i, 0);
            }

            try {
                while (runTest) {

                    if(isCancelled()) {
                        return null;
                    }

                    BITalinoFrame[] frames = null;
                    try {
                        frames = bitalino.read(this.frameSize);//read(number of frameSize to read)
                    }
                    catch(BITalinoException e) {
                        Log.d("BITalinoException", e.getMessage());
                    }

                    if(frames != null) {
                        reading = new int[frames.length];
                        // go into frameSize to gather data from sensors
                        for (int i = 0; i < frames.length; i++) {
                            //analog 2 == ECG
                            reading[i] = frames[i].getAnalog(ecgChannel);
                        }
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
                thresPoint[i] = thresPoint[i + reading.length];
                sigPoint[i] = sigPoint[i + reading.length];
            }

            //Add the new points to the end of the array
            int readIndex = 0;
            for(int i = readingPoint.length - reading.length; i < readingPoint.length; i++) {
                readingPoint[i] = new DataPoint((readingPoint[i].getX() + reading.length),
                        reading[readIndex]);
                thresPoint[i] = new DataPoint(readingPoint[i].getX(), THR_SIG);
                sigPoint[i] = new DataPoint(readingPoint[i].getX(), THR_NOISE);
                readIndex++;
            }

            if (readingPoint[readingPoint.length-1].getX() >= REC_SIZE + frameSize)
            {
                for (int i = 0; i < frameSize; i++)
                {
                    scanPoints(readingPoint.length - frameSize + i);
                }
            }

            liveValueSeries.resetData(readingPoint);
            //sigValueSeries.resetData(sigPoint);
            //thresValueSeries.resetData(thresPoint);
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

        protected void scanPoints(int i)
        {
            if (prevQRS > aveRR*1.66 && candidateX != -1)
            {
                addQRS(candidateX, candidateY);
            }
            double curY = readingPoint[i].getY();

            if (readingPoint[i].getX() < REC_SIZE + (2 * SRATE))
            {
                THR_SIG = THR_SIG >= curY ? THR_SIG : curY;
                Log.d("Learning", "THR_SIG: " + THR_SIG + " (" + readingPoint[i].getX() + "," +  + readingPoint[i].getY() + ")");
            }
            else if ((int)readingPoint[i].getX() == REC_SIZE + (2 * SRATE))
            {
                THR_SIG = THR_SIG * 0.95;
                THR_NOISE = THR_SIG / 2;
                prevQRS = 0;
                Log.d("Initial", "THR_SIG: " + THR_SIG + " (" + readingPoint[i].getX() + "," +  + readingPoint[i].getY() + ")");
            }
            else
            {
                Log.d("Checking for maxima:  ", "" + (readingPoint[i-2].getY() < readingPoint[i-1].getY() && readingPoint[i-1].getY() > readingPoint[i].getY() && readingPoint[i-1].getY() > THR_SIG));
                Log.d("Is maxima: ", "" + (readingPoint[i-2].getY() < readingPoint[i-1].getY() && readingPoint[i-1].getY() > readingPoint[i].getY() && readingPoint[i-1].getY() > THR_SIG));

                //point is a peak
                if (readingPoint[i-2].getY() < readingPoint[i-1].getY() && readingPoint[i-1].getY() > readingPoint[i].getY()) //file[x-1] is a local maxima
                {
                    if (readingPoint[i-1].getY() > THR_SIG)
                    {
                        //add QRS and adjust thresholds
                        addQRS((int)readingPoint[i-1].getX(), readingPoint[i-1].getY());
                    }
                    else if (readingPoint[i-1].getY() > THR_NOISE)
                    {
                        if (candidateX == -1)
                        {
                            candidateX = (int)readingPoint[i-1].getX();
                            candidateY = readingPoint[i-1].getY();
                        }
                        else if (readingPoint[i-1].getY() > candidateY)
                        {
                            candidateX = (int)readingPoint[i-1].getX();
                            candidateY = readingPoint[i-1].getY();
                        }
                    }
                }
            }
            prevQRS++;
        }

        protected void addQRS(int x, double y)
        {
            //pointSeries.appendData(new DataPoint(x, y), true, 10);

            //update threshold
            THR_SIG = THR_SIG*0.875 + y*0.125;
            Log.d("Adapting", "THR_SIG: " + THR_SIG + " (" + x + "," + y + ")");
            calcRR(x, y);
            candidateX = -1;
            candidateY = -1;
            prevQRS = 0;
        }

        protected void calcRR(int x, double y)
        {
            int newQRS = x;
            int RR = newQRS - lastQRS;
            if (lastQRS != -1 && RR != 0)
            {
                RR = newQRS - lastQRS;
                numRR++;
                aveRR = ((aveRR*numRR-1)+RR)/(numRR);
                double bpm = 0;

                bpm = 60/((double)RR/(double)SRATE);

                bpm = ((double)((int)(bpm*100)))/100;

                lblBPM.setText("BPM: " + bpm);
                if (bpm < 50)
                {
                    lblAnomaly.setText("Anomaly: Bradycardia");
                }
                else if (bpm > 100)
                {
                    lblAnomaly.setText("Anomaly: Tachycardia");
                }
                else
                {
                    lblAnomaly.setText("Anomaly: ---");
                }
            }
            lastQRS = newQRS;
        }
   }

}