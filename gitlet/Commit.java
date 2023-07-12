package gitlet;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class Commit implements Serializable {

    static SimpleDateFormat timeFormatter = new SimpleDateFormat(
            "EEE MMM d HH:mm:ss yyyy Z");
    public String msg;
    public String time;
    public String parent;
    public String mergeParent;
    public HashMap<String, String> blobs = new HashMap<>();

    public Commit(String s) {
        this.msg = s;
        this.time = timeFormatter.format(new Date());
    }

    public Commit(String s, Date date) {
        this.msg = s;
        this.time = timeFormatter.format(date);
    }

    public Commit(String s, String parent, HashMap<String, String> blobs) {
        this.msg = s;
        this.time = timeFormatter.format(new Date());
        this.parent = parent;
        this.blobs = blobs;
    }

    public Commit(String s, String parent, String mergeParent, HashMap<String, String> blobs) {
        this.msg = s;
        this.time = timeFormatter.format(new Date());
        this.parent = parent;
        this.blobs = blobs;
        this.mergeParent = mergeParent;
    }

    // todo: add more constructors as needed
}