package gitlet;

import java.io.Serializable;

public class Branch implements Serializable {

    public String name;
    public String ptr;

    public Branch(String name , String ptr) {
        this.name = name;
        this.ptr = ptr;
    }

    public String name() {
        return name;
    }

    public void setName(String _name) {
        this.name = _name;
    }

    public String ptr() {
        return ptr;
    }

    public void advancePtr(String commitAddress) {
        this.ptr = commitAddress;
    }
}
