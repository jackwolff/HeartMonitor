package edu.und.cs.com.heart_monitor;

/**
 * Created by Andrew on 2/18/2015.
 */
import com.bitalino.comm.BITalinoFrame;

public class BITalinoReading {

    private long timestamp;
    private BITalinoFrame[] frames;

    public BITalinoReading() {
    }

    public BITalinoReading(final long timestamp, final BITalinoFrame[] frames) {
        this.timestamp = timestamp;
        this.frames = frames;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public BITalinoFrame[] getFrames() {
        return frames;
    }

    public void setFrames(BITalinoFrame[] frames) {
        this.frames = frames;
    }

}
