package gitlet;

import java.util.Arrays;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        // FILL THIS IN
        Repo myRepo = new Repo();
        String command = args[0];
        switch(command) {
            case "init":
                if (args.length != 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                myRepo.init();
                break;
            case "add":
                checkInput(args, 2);
                myRepo.add(args[1]);
                break;
            case "commit":
                checkInput(args, 2);
                myRepo.commit(args[1]);
                break;
            case "rm":
                checkInput(args, 2);
                myRepo.rm(args[1]);
                break;
            case "checkout":
                if (args.length < 2 || args.length > 4) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                if (!Repo.REPO.exists()) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    System.exit(0);
                }
                myRepo.checkOutCommands(Arrays.copyOfRange(args, 1, args.length));
                break;
            case "log":
                checkInput(args, 1);
                myRepo.log();
                break;
            case "global-log":
                checkInput(args, 1);
                myRepo.logGlobal();
                break;
            case "find":
                checkInput(args, 2);
                myRepo.find(args[1]);
                break;
            case "status":
                checkInput(args, 1);
                myRepo.status();
                break;
            case "branch":
                checkInput(args, 2);
                myRepo.createBranch(args[1]);
                break;
            case "rm-branch":
                checkInput(args, 2);
                myRepo.rmBranch(args[1]);
                break;
            case "reset":
                checkInput(args, 2);
                myRepo.reset(args[1]);
                break;
            case "merge":
                checkInput(args, 2);
                myRepo.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
                System.exit(0);
        }
    }

    private static void checkInput(String[] args, int numOperands) {
        if (args.length != numOperands) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
        if (!Repo.REPO.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }
}
