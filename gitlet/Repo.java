package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;

public class Repo {

    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File REPO = Utils.join(CWD, ".gitlet");
    public static final File BLOBS = Utils.join(REPO, "blobs");
    public static final File COMMITS = Utils.join(REPO, "commits");
    public static final File STAGINGAREA = Utils.join(REPO, "stagingarea");
    public static final File TOREMOVE = Utils.join(REPO, "toremove");
    public static final File BRANCHES = Utils.join(REPO, "branches");
    public static final File HEAD = Utils.join(REPO, "HEAD.txt");

    public void init() {
        if (REPO.exists()) {
            System.out.println("A Gitlet version-control system "
                    + "already exists in the current directory.");
            System.exit(0);
        }
        // create repository directories
        REPO.mkdir();
        BLOBS.mkdir();
        COMMITS.mkdir();
        STAGINGAREA.mkdir();
        TOREMOVE.mkdir();
        BRANCHES.mkdir();

        // create and save initial commit
        Commit initialCommit = new Commit("initial commit", new Date(0));
        String commit_ptr = sha1(serialize(initialCommit));

        writeObject(join(COMMITS, commit_ptr), initialCommit);

        // create and save initial branch
        Branch initialBranch = new Branch("master", commit_ptr);
        writeObject(join(BRANCHES, initialBranch.name), initialBranch);

        // create and save head (head is basically a branch that points to initialBranch.
        writeObject(HEAD, new Branch("HEAD", initialBranch.name));
    }

    public void add(String file) {
        File toAdd = Utils.join(CWD, file);
        if (!toAdd.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }
        byte[] content = Utils.readContents(toAdd);

        // Get the repo's current branch
        String myBranchID = Utils.readObject(HEAD, Branch.class).ptr();
        Branch myBranch = Utils.readObject(Utils.join(
                BRANCHES, myBranchID), Branch.class);

        // Get the current commit, we need this info to know how staging area should behave
        Commit myCommit = Utils.readObject(Utils.join(
                COMMITS, myBranch.ptr()), Commit.class);

        // if file was already staged for removal, unstage it
        File addOrRm = Utils.join(TOREMOVE, file);
        if (addOrRm.exists()) addOrRm.delete();

        // if file has not been changed, but is in staging area, unstage it
        addOrRm = Utils.join(STAGINGAREA, file);
        if (myCommit.blobs.containsKey(file)
            && myCommit.blobs.get(file).equals(Utils.sha1(content))) {
            if (addOrRm.exists() && sha1(readContents(toAdd)).equals(content)) {
                addOrRm.delete();
            }
            return;
        }

        // otherwise, we stage the file for addition
        Utils.writeContents(addOrRm, content);
    }

    public void commit(String msg) {
        if (STAGINGAREA.list().length == 0 && TOREMOVE.list().length == 0) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        if (msg.length() == 0) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }

        Branch head = Utils.readObject(HEAD, Branch.class);
        Branch myBranch = Utils.readObject(Utils.join(
                BRANCHES, head.ptr), Branch.class);
        Commit parent  = Utils.readObject(Utils.join(
                COMMITS, myBranch.ptr), Commit.class);
        Commit myCommit = new Commit(msg, myBranch.ptr, parent.blobs);

        // put the individual files in myCommit's blob list
        for (String file : Utils.plainFilenamesIn(STAGINGAREA)) {
            byte[] content = Utils.readContents(Utils.join(
                    STAGINGAREA, file));
            String hashID = Utils.sha1(content);
            myCommit.blobs.put(file, hashID);
            Utils.writeContents(Utils.join(BLOBS, hashID), content);
        }

        // remove individual files in removal staging area from blob list
        for (String file: Utils.plainFilenamesIn(TOREMOVE)) {
            myCommit.blobs.remove(file);
        }

        // clear staging area and removal area
        this.clearStagingArea();

        // write this object to the commit folder in .gitlet
        String myCommitID = Utils.sha1(Utils.serialize(myCommit));

        // advance branch's pointer to the newly created commit
        myBranch.advancePtr(myCommitID);

        // write the serialized commit object to .gitlet
        writeObject(join(COMMITS, myCommitID), myCommit);

        // overwrite the old branch
        Utils.writeObject(Utils.join(BRANCHES, myBranch.name), myBranch);
    }

    private void clearStagingArea() {
        for (File file: STAGINGAREA.listFiles()) {
            file.delete();
        }
        for (File file: TOREMOVE.listFiles()) {
            file.delete();
        }
    }

    public void checkOutCommands(String[] args) {
        switch (args.length) {
            case 1:
                branchCheckout(args[0]);
                break;
            case 2:
                if (!args[0].equals("--")) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                fileCheckout(args[1]);
                break;
            case 3:
                if (!args[1].equals("--")) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                commitCheckout(args[0], args[2]);
                break;
            default:
                System.out.println("Incorrect operands.");
                System.exit(0);
                break;
        }
    }

    private void branchCheckout(String destBranchName) {
        String currentBranchName = readObject(HEAD, Branch.class).ptr;
        // Edge case: checking out current branch
        if (currentBranchName.equals(destBranchName)) {
            System.out.println("No need to checkout the current branch.");
        }
        // Edge case: branch DNE
        if (!plainFilenamesIn(BRANCHES).contains(destBranchName)) {
            System.out.println("No such branch exists.");
            System.exit(0);
        }

        // get a list of blobs in current commit
        Branch currentBranch = readObject(join(
                BRANCHES, currentBranchName), Branch.class);
        Commit currentCommit = readObject(join(
                COMMITS, currentBranch.ptr), Commit.class);

        // get a list of blobs in dest commit
        Branch destBranch = readObject(join(
                BRANCHES, destBranchName), Branch.class);
        Commit destCommit = readObject(join(
                COMMITS, destBranch.ptr), Commit.class);

        List<String> workingFiles = plainFilenamesIn(CWD);
        for (String file : workingFiles) {
            boolean inCurrent = currentCommit.blobs.containsKey(file);
            boolean inDest = destCommit.blobs.containsKey(file);
            if (inDest && !inCurrent) {
                System.out.println(
                        "There is an untracked file in the way;"
                                + " delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        for (String file : workingFiles) {
            boolean inCurrent = currentCommit.blobs.containsKey(file);
            boolean inDest = destCommit.blobs.containsKey(file);
            // case 1: destBranch has file
            if (!inDest && inCurrent) {
                boolean deleted = join(CWD, file).delete();
                assert (deleted);
            }
        }

        for (java.util.Map.Entry<String, String> entry: destCommit.blobs.entrySet()) {
            byte[] contents = readContents(join(BLOBS, entry.getValue()));
            writeContents(join(CWD, entry.getKey()), contents);
        }

        clearStagingArea();

        Branch newHead = readObject(HEAD, Branch.class);
        newHead.advancePtr(destBranchName);
        writeObject(HEAD, newHead);
    }

    private void fileCheckout(String fileName) {
        String myBranchName = Utils.readObject(HEAD, Branch.class).ptr;
        Branch myBranch = Utils.readObject(Utils.join(
                BRANCHES, myBranchName), Branch.class);
        Commit myCommit = Utils.readObject(Utils.join(
                COMMITS, myBranch.ptr), Commit.class);
        if (!myCommit.blobs.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        byte[] toWrite = Utils.readContents(Utils.join(
                BLOBS, myCommit.blobs.get(fileName)));
        Utils.writeContents(Utils.join(CWD, fileName), toWrite);
    }

    private void commitCheckout(String commitID, String fileName) {
        if (Utils.join(COMMITS, commitID).exists()) {
            safeCommitCheckout(commitID, fileName);
        }
        // commitID too short
        if (commitID.length() < 6) {
            commitDNE();
        }
        // find matching commit in .gitlet
        String foundCommit = null;
        for (String c : Utils.plainFilenamesIn(COMMITS)) {
            if (commitID.length() <= c.length()
                    && c.substring(0, commitID.length()).equals(commitID)) {
                foundCommit = c;
                break;
            }
        }
        // commit was never found
        if (foundCommit == null) {
            commitDNE();
        }
        safeCommitCheckout(foundCommit, fileName);
    }

    private void commitDNE() {
        System.out.println("No commit with that id exists.");
        System.exit(0);
    }

    private void safeCommitCheckout(String commitID, String fileName) {
        Commit myCommit = Utils.readObject(Utils.join(
                COMMITS, commitID), Commit.class);
        if (!myCommit.blobs.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        byte[] toWrite = Utils.readContents(Utils.join(
                BLOBS, myCommit.blobs.get(fileName)));
        Utils.writeContents(Utils.join(CWD, fileName), toWrite);
    }

    public void log() {
        String myBranchName = Utils.readObject(HEAD, Branch.class).ptr;
        Branch myBranch = Utils.readObject(Utils.join(
                BRANCHES, myBranchName), Branch.class);
        String curName = myBranch.ptr;
        while (curName != null) {
            Commit myCommit = Utils.readObject(Utils.join(
                    COMMITS, curName), Commit.class);
            String mergeLine = "";
            if (myCommit.mergeParent != null) {
                mergeLine = "Merge: " + myCommit.parent.substring(0, 7)
                        + " " + myCommit.mergeParent.substring(0, 7) + "\n";
            }
            System.out.println(
                    "===\n"
                    + "commit " + Utils.sha1(Utils.serialize(myCommit)) + "\n"
                    + mergeLine
                    + "Date: " + myCommit.time + "\n"
                    + myCommit.msg + "\n"
                );
            curName = myCommit.parent;
        }
    }

    public void logGlobal() {
        for (String curName : plainFilenamesIn(COMMITS)) {
            Commit myCommit = Utils.readObject(Utils.join(
                    COMMITS, curName), Commit.class);
            String mergeLine = "";
            if (myCommit.mergeParent != null) {
                mergeLine = "Merge: " + myCommit.parent.substring(0, 7)
                        + " " + myCommit.mergeParent.substring(0, 7) + "\n";
            }
            System.out.println(
                    "===\n"
                    + "commit " + Utils.sha1(Utils.serialize(myCommit)) + "\n"
                    + mergeLine
                    + "Date: " + myCommit.time + "\n"
                    + myCommit.msg + "\n"
            );
        }
    }

    public void createBranch(String name) {
        // if branch name already exists, throw an error
        if (plainFilenamesIn(BRANCHES).contains(name)) {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }
        String myBranchName = Utils.readObject(HEAD, Branch.class).ptr;
        Branch myBranch = Utils.readObject(Utils.join(
                BRANCHES, myBranchName), Branch.class);
        Branch babyBranch = new Branch(name, myBranch.ptr);
        writeObject(join(BRANCHES, name), babyBranch);
    }

    public void rmBranch(String name) {
        // current branch cannot be removed
        String currentBranch = readObject(HEAD, Branch.class).ptr;
        if (currentBranch.equals(name)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }

        // if branch exists, delete the branch file
        if (plainFilenamesIn(BRANCHES).contains(name)) {
            boolean deleted = join(BRANCHES, name).delete();
            assert (deleted);
            return;
        }

        // else print an error message
        System.out.println("A branch with that name does not exist.");
        System.exit(0);
    }

    public void rm(String fileName) {
        String myBranchName = Utils.readObject(HEAD, Branch.class).ptr;
        Branch myBranch = Utils.readObject(Utils.join(
                BRANCHES, myBranchName), Branch.class);
        Commit myCommit = Utils.readObject(Utils.join(
                COMMITS, myBranch.ptr), Commit.class);

        List<String> forAddition = plainFilenamesIn(STAGINGAREA);
        if (!forAddition.contains(fileName)
                && !myCommit.blobs.containsKey(fileName)) {
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
        if (forAddition.contains(fileName)) {
            join(STAGINGAREA, fileName).delete();
        }
        if (myCommit.blobs.containsKey(fileName)) {
            if (join(CWD, fileName).exists()) join(CWD, fileName).delete();
            writeContents(join(
                    TOREMOVE, fileName), myCommit.blobs.get(fileName));
        }
    }

    public void find(String commitMsg) {
        String printStr = "";
        for (String commitID : plainFilenamesIn(COMMITS)) {
            if (readObject(join(
                    COMMITS, commitID), Commit.class).msg.equals(commitMsg)) {
                printStr += commitID + "\n";
            }
        }
        if (printStr.equals("")) {
            System.out.println("Found no commit with that message.");
            System.exit(0);
        }
        System.out.print(printStr);
    }


    public void status() {
        String statusStr = "";
        statusStr += "=== Branches ===" + "\n";
        String curBranch = readObject(HEAD, Branch.class).ptr;
        for (String b : plainFilenamesIn((BRANCHES))) {
            if (b.equals(curBranch)) {
                statusStr += "*";
            }
            statusStr += b + "\n";
        }
        System.out.println(statusStr);

        statusStr = "";
        statusStr += "=== Staged Files ===" + "\n";
        for (String s : plainFilenamesIn(STAGINGAREA)) {
            statusStr += s + "\n";
        }
        System.out.println(statusStr);

        statusStr = "";
        statusStr += "=== Removed Files ===" + "\n";
        for (String s : plainFilenamesIn(TOREMOVE)) {
            statusStr += s + "\n";
        }
        System.out.println(statusStr);

        // todo
        statusStr = "";
        statusStr += "=== Modifications Not Staged For Commit ===" + "\n";
        System.out.println(statusStr);

        // todo
        statusStr = "";
        statusStr += "=== Untracked Files ===";
        System.out.println(statusStr);
    }

    public void reset(String commitID) {
        if (commitID.length() < 6) {
            commitDNE();
        }
        // find matching commit in .gitlet
        String foundCommit = null;
        for (String c : Utils.plainFilenamesIn(COMMITS)) {
            if (commitID.length() <= c.length()) {
                if (c.substring(0, commitID.length()).equals(commitID)) {
                    foundCommit = c;
                    break;
                }
            }
        }
        // commit was never found
        if (foundCommit == null) {
            commitDNE();
        }
        safeReset(foundCommit);
    }

    private void safeReset(String commitID) {
        Branch currentBranch = readObject(join(
                BRANCHES, readObject(HEAD, Branch.class).ptr), Branch.class);
        Commit currentCommit = readObject(join(
                COMMITS, currentBranch.ptr), Commit.class);

        Commit destCommit = readObject(join(COMMITS, commitID), Commit.class);

        List<String> workingFiles = plainFilenamesIn(CWD);
        for (String file : workingFiles) {
            boolean inCurrent = currentCommit.blobs.containsKey(file);
            boolean inDest = destCommit.blobs.containsKey(file);
            if (inDest && !inCurrent) {
                System.out.println(
                        "There is an untracked file in the way; "
                                + "delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        for (String file : workingFiles) {
            boolean inCurrent = currentCommit.blobs.containsKey(file);
            boolean inDest = destCommit.blobs.containsKey(file);
            // case 1: destBranch has file
            if (!inDest && inCurrent) {
                boolean deleted = join(CWD, file).delete();
                assert (deleted);
            }
        }

        for (java.util.Map.Entry<String, String> entry: destCommit.blobs.entrySet()) {
            byte[] contents = readContents(join(BLOBS, entry.getValue()));
            writeContents(join(CWD, entry.getKey()), contents);
        }

        clearStagingArea();

        currentBranch.ptr = commitID;
        writeObject(join(
                BRANCHES, readObject(HEAD, Branch.class).ptr), currentBranch);
    }

    public void merge(String branchName) {
        if (plainFilenamesIn(STAGINGAREA).size() > 0
                || plainFilenamesIn(TOREMOVE).size() > 0) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
        if (!plainFilenamesIn(BRANCHES).contains(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        String myBranch = readObject(HEAD, Branch.class).ptr;
        if (myBranch.equals(branchName)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }

        String myCommitID = readObject(join(
                BRANCHES, myBranch), Branch.class).ptr;
        String destCommitID = readObject(join(
                BRANCHES, branchName), Branch.class).ptr;

        ArrayList<String> myParents = traverseParents(myCommitID);
        ArrayList<String> destParents = traverseParents(destCommitID);

        String lca = "";
        for (String id : myParents) {
            if (destParents.contains(id)) {
                lca = id;
                break;
            }
        }

        if (lca.equals(myCommitID)) {
            System.out.println("Current branch fast-forwarded.");
            branchCheckout(branchName);
            System.exit(0);
        } else if (lca.equals(destCommitID)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }

        Commit myCommitObj = readObject(join(COMMITS, myCommitID), Commit.class);
        Commit destCommitObj = readObject(join(COMMITS, destCommitID), Commit.class);
        Commit lcaObj = readObject(join(COMMITS, lca), Commit.class);

        for (String file : plainFilenamesIn(CWD)) {
            boolean inCurrent = myCommitObj.blobs.containsKey(file);
            boolean inDest = destCommitObj.blobs.containsKey(file);
            if(!inCurrent && inDest) {
                System.out.println("There is an untracked file in the way;"
                        + " delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        boolean hasConflict = false;
        for (String file : destCommitObj.blobs.keySet()) {
            boolean inCurrent = myCommitObj.blobs.containsKey(file);
            if (lcaObj.blobs.containsKey(file)) {
                if (inCurrent && myCommitObj.blobs.get(file).equals(lcaObj.blobs.get(file))
                        && !destCommitObj.blobs.get(file).equals(myCommitObj.blobs.get(file))) {
                    commitCheckout(destCommitID, file);
                    add(file);
                } else if (inCurrent
                        && !myCommitObj.blobs.get(file).equals(lcaObj.blobs.get(file))
                        && !destCommitObj.blobs.get(file).equals(lcaObj.blobs.get(file))
                        && !myCommitObj.blobs.get(file).equals(destCommitObj.blobs.get(file))) {
                    writeConflict(file, myCommitObj.blobs.get(file), destCommitObj.blobs.get(file));
                    hasConflict = true;
                } else if (!inCurrent
                        && !destCommitObj.blobs.get(file).equals(lcaObj.blobs.get(file))) {
                    writeConflict(file, "", destCommitObj.blobs.get(file));
                    hasConflict = true;
                }
            } else if (inCurrent
                    && !destCommitObj.blobs.get(file).equals(myCommitObj.blobs.get(file))) {
                writeConflict(file, myCommitObj.blobs.get(file), destCommitObj.blobs.get(file));
                hasConflict = true;
            } else if (!inCurrent) {
                commitCheckout(destCommitID, file);
                add(file);
            }
        }

        for (String file: lcaObj.blobs.keySet()) {
            if (myCommitObj.blobs.containsKey(file)
                    && myCommitObj.blobs.get(file).equals(lcaObj.blobs.get(file))
                    && !destCommitObj.blobs.containsKey(file)) {
                rm(file);
            } else if (myCommitObj.blobs.containsKey(file)
                    && !myCommitObj.blobs.get(file).equals(lcaObj.blobs.get(file))
                    && !destCommitObj.blobs.containsKey(file)) {
                writeConflict(file, myCommitObj.blobs.get(file), "");
                hasConflict = true;
            }
        }

        String msg = "Merged " + branchName + " into " + myBranch + ".";

        if (STAGINGAREA.list().length == 0 && TOREMOVE.list().length == 0) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        if (msg.length() == 0) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }
        Branch head = Utils.readObject(HEAD, Branch.class);
        Branch myBranchObj = Utils.readObject(Utils.join(
                BRANCHES, head.ptr), Branch.class);
        Commit parent  = Utils.readObject(Utils.join(
                COMMITS, myBranchObj.ptr), Commit.class);
        Commit myCommit = new Commit(
                msg, myCommitID, destCommitID, parent.blobs);

        // put the individual files in myCommit's blob list
        for (String file : Utils.plainFilenamesIn(STAGINGAREA)) {
            byte[] content = Utils.readContents(Utils.join(
                    STAGINGAREA, file));
            String hashID = Utils.sha1(content);
            myCommit.blobs.put(file, hashID);
            Utils.writeContents(Utils.join(BLOBS, hashID), content);
        }

        // remove individual files in removal staging area from blob list
        for (String file: Utils.plainFilenamesIn(TOREMOVE)) {
            myCommit.blobs.remove(file);
        }

        // clear staging area and removal area
        this.clearStagingArea();

        // write this object to the commit folder in .gitlet
        String myNewCommitID = Utils.sha1(Utils.serialize(myCommit));

        // advance branch's pointer to the newly created commit
        myBranchObj.advancePtr(myNewCommitID);

        // write the serialized commit object to .gitlet
        writeObject(join(COMMITS, myNewCommitID), myCommit);

        // overwrite the old branch
        Utils.writeObject(Utils.join(BRANCHES, myBranchObj.name), myBranch);

        if (hasConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    private ArrayList<String> traverseParents(String commitID) {
        LinkedList<String> q = new LinkedList<>();
        q.add(commitID);
        ArrayList<String> myParents = new ArrayList<>();
        while (!q.isEmpty()) {
            String cur = q.removeFirst();
            myParents.add(cur);
            Commit thisCommit = readObject(join(COMMITS, cur), Commit.class);
            if (thisCommit.parent != null) {
                q.addLast(thisCommit.parent);
            }
            if (thisCommit.mergeParent != null) {
                q.addLast(thisCommit.mergeParent);
            }
        }
        return myParents;
    }

    private void writeConflict(String file, String myBlob, String destBlob) {
        String myVersion = join(BLOBS, myBlob).exists()
                ? readContentsAsString(join(BLOBS, myBlob)) : "";
        String destVersion = join(BLOBS, destBlob).exists()
                ? readContentsAsString(join(BLOBS, destBlob)) : "";
        writeContents(join(CWD, file), "<<<<<<< HEAD\n" + myVersion
                + "=======\n" + destVersion + ">>>>>>>\n");
        this.add(file);
    }
}
