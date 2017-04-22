package edu.und.cs.com.heart_monitor;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;

import java.util.ArrayList;

import edu.und.cs.com.heart_monitor.R;

public class ViewRecording extends ActionBarActivity implements View.OnClickListener {

    Button btnBack,btnEmail;
    LinearLayout lytGraphLayout;
    TextView txtFileName;
    FileHelper myFileHelper;
    ArrayList<Integer> myFileInfo;

    GraphView myGraphView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_recording);

        myFileHelper = new FileHelper();

        /*dataValueSeries = new GraphViewSeries(new GraphView.GraphViewData[] {});
        myGraphView = new LineGraphView(this, "Electrocardiograph"){};
        lytGraphLayout = (LinearLayout) findViewById(R.id.graphLayoutTwo);
        myGraphView.addSeries(dataValueSeries);
        myGraphView.setManualYAxisBounds(900, 200);
        lytGraphLayout.addView(myGraphView);
        myGraphView.setViewPort(0,25);
        myGraphView.setScrollable(true);*/


        btnEmail = (Button) findViewById(R.id.emailBTN);
        btnBack = (Button) findViewById(R.id.backBTN);
        lytGraphLayout = (LinearLayout) findViewById(R.id.graphLayoutTwo);
        txtFileName = (TextView) findViewById(R.id.txtFileName);

        btnEmail.setOnClickListener(this);
        btnBack.setOnClickListener(this);

        Intent oldIntent =getIntent();
        Bundle myBundle = oldIntent.getExtras();
        String fileName = myBundle.getString("fileName");
        txtFileName.setText("File: " + fileName);
        myFileInfo = myFileHelper.loadFile(fileName, this);
        /*for(int i = 0; i < myFileInfo.size()/2; ++i){
            dataValueSeries.appendData(new GraphView.GraphViewData(myFileInfo.get(i), myFileInfo.get(++i)), false, 10000);
        }
        myGraphView.redrawAll();*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_view_recording, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.backBTN:
                android.os.Process.killProcess(android.os.Process.myPid());
                break;
        }
    }
}
