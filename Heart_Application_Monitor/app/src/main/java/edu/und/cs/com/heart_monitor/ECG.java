package edu.und.cs.com.heart_monitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.bitalino.comm.BITalinoDevice;
import com.bitalino.comm.BITalinoException;
import com.bitalino.comm.BITalinoFrame;

import com.jjoe64.graphview.*;
import com.jjoe64.graphview.series.BaseSeries;
import com.jjoe64.*;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import edu.und.cs.com.heart_monitor.R;
import retrofit.RestAdapter;
import retrofit.client.Response;
import roboguice.activity.RoboActivity;
import java.util.ArrayList;
import static android.widget.Toast.makeText;

public class ECG extends RoboActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final boolean UPLOAD = false;
    private LineGraphSeries signalValueSeries;
    private GraphView myGraphView;
    boolean runTest = true;
    boolean testFailed = false;
    boolean connectionFailure = false;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private boolean testInitiated = false;
    private AsyncTask myThread;
    private boolean keepFile;
    private int RSSI;                                           //bluetooth signal strength TODO ANDREW
    public FileHelper myFileHelper;
    private ECGFilter filter;

    private final int GRAPH_SIZE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_ecg);

        keepFile = false;                                                               //flag used to indicate whether or not file should be kept

        filter = new ECGFilter();

        //let user know it will take a few seconds to start getting readings
        makeText(this, "Establishing Connection to Sensor", Toast.LENGTH_LONG).show();

        signalValueSeries = new LineGraphSeries();

        //Get the graph
        myGraphView = (GraphView)findViewById(R.id.graph);
        //Set graph options
        myGraphView.addSeries(signalValueSeries);

        myGraphView.getViewport().setMinX(0);
        myGraphView.getViewport().setMaxX(GRAPH_SIZE);
        myGraphView.getViewport().setXAxisBoundsManual(true);

        //Find the buttons by their ID
        final Button startButton = (Button) findViewById(R.id.startBTN);
        final Button quitButton = (Button) findViewById(R.id.quitBTN);
        final Button backButton = (Button) findViewById(R.id.backBTN);
        final Button storeButton = (Button) findViewById(R.id.storeBTN);

        //Listen for button presses
        startButton.setOnClickListener(this);
        quitButton.setOnClickListener(this);
        backButton.setOnClickListener(this);
        storeButton.setOnClickListener(this);

        // execute
        if (!testInitiated) {
            myThread = new TestAsyncTask().execute();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startBTN:
                runTest = false;                                                                                    //ensure current test terminates correctly
                myFileHelper.closeFile(myFileHelper, getApplicationContext());                                       //close file output stream
                if(keepFile != true) getApplicationContext().deleteFile(myFileHelper.fileName);
                startActivity(new Intent(ECG.this, ECG.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                break;
            case R.id.quitBTN:
                runTest = false;
                myFileHelper.closeFile(myFileHelper, getApplicationContext());
                break;
            case R.id.backBTN:
                runTest = false;
                myFileHelper.closeFile(myFileHelper, getApplicationContext());
                if(keepFile != true) getApplicationContext().deleteFile(myFileHelper.fileName);
                android.os.Process.killProcess(android.os.Process.myPid());
                startActivity(new Intent(ECG.this, MainActivity.class));
                break;
            case R.id.storeBTN:
                if((runTest == true)&&(testFailed == false)&&(testInitiated == true)){                              //test is still running
                    Toast.makeText(this, "Stop test first!", Toast.LENGTH_LONG).show();
                }else if((runTest == false)&&(testFailed == false)&&(testInitiated == true)){                       //test has run successfully, has stopped, can store file
                    Toast.makeText(this, "File " + myFileHelper.fileName + " created", Toast.LENGTH_LONG).show();
                    keepFile = true;
                    myFileHelper.closeFile(myFileHelper, getApplicationContext());
                }else if((runTest == false)&&(testFailed == true)&&(testInitiated == false)){                       //test is not running and an error of some kind occurred
                    Toast.makeText(this, "No recording made", Toast.LENGTH_LONG).show();
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
        public int currentValue = 0;
        public int currentFrameNumber = 0;
        SharedPreferences getPreference = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String macAddress = getPreference.getString("macAddress",null );
        private final int ecgChannel = 1;
        private final int sampleRate = 6;
        private int[] reading;

        DataPoint[] point;

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
            testInitiated = true;
            bitalino = new BITalinoDevice(1000, new int[]{ecgChannel});
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
                Toast.makeText(getApplicationContext(), "Unable to connect to Bitalino board.",
                        Toast.LENGTH_LONG);
                return null;
            }

            point = new DataPoint[GRAPH_SIZE];
            for(int i = 0; i < point.length; i++) {
                point[i] = new DataPoint(i, 0);
            }

            try {
                while (runTest) {
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
                            //Add the point to the filter
                            filter.addPoint(reading[i]);
                        }
                        //output results to screen using onProgressUpdate()
                        publishProgress();
                        try {
                            Thread.sleep(20);
                        }
                        catch(Exception e) {

                        }
                    }
                }
            }catch(Exception e) {              //error opening socket
                Log.d("Exception", e.getMessage());
                connectionFailure = true;     //flag that connection has failed
                runTest = false;              //flag that the test has not run
                testFailed = true;            //flag indicates the test has failed
                publishProgress();

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
            //If the connection has failed, show a message
            if(connectionFailure == true) {
                Toast.makeText(getApplicationContext(),"Unable to establish connection",
                        Toast.LENGTH_LONG).show();
            }else {

                for(int i = 0; i < point.length - reading.length; i++) {
                    point[i] = point[i + reading.length];
                }
                int readIndex = 0;
                for(int i = point.length - reading.length; i < point.length; i++) {
                    point[i] = new DataPoint((point[i].getX() + reading.length), reading[readIndex]);
                    readIndex++;
                }
                signalValueSeries.resetData(point);
                myGraphView.getViewport().scrollToEnd();
            }
        }

        @Override
        protected void onCancelled() {
            // stop acquisition and close bluetooth connection
            try {
                bitalino.stop();         //signal board to quit sending packets
                InputStream is = null;      //close input and output streams
                OutputStream os = null;
                sock.close();               //close socket on this end
            } catch (Exception e) {
                Log.e(TAG, "There was an error.", e);
            }
        }

   }

}