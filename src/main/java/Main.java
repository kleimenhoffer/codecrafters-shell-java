import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    static String findInPath(String cmd) {
        String path = System.getenv("PATH");
        if (path == null)
            return null;
        for (String dir : path.split(File.pathSeparator)) {
            File f = new File(dir, cmd);
            if (f.isFile() && f.canExecute())
                return f.getAbsolutePath();
        }
        return null;
    }

    static List<String> parseInput(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean insideSingleQuote = false;

        for (char c : input.toCharArray()) {
            if (c == '\'') {
                insideSingleQuote = !insideSingleQuote;
            } else if (c == ' ' && !insideSingleQuote) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }
        return args;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        File cwd = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            String rawInput = scanner.nextLine().trim();
            if (rawInput.isEmpty())
                continue;

            List<String> inputs = parseInput(rawInput);
            String command = inputs.get(0);

            if (command.equals("pwd")) {
                System.out.println(cwd.getAbsolutePath());
            } else if (command.equals("cd")) {
                if (inputs.size() < 2) {

                } else {
                    String dir = inputs.get(1);
                    if (dir.equals("~"))
                        dir = System.getenv("HOME");
                    File target = new File(dir).isAbsolute() ? new File(dir) : new File(cwd, dir);
                    if (target.isDirectory())
                        cwd = target.getCanonicalFile();
                    else
                        System.out.println("cd: " + dir + ": No such file or directory");
                }
            } else if (command.equals("type")) {
                if (inputs.size() < 2) {

                } else {
                    String cmd = inputs.get(1);
                    if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd")
                            || cmd.equals("cd")) {
                        System.out.println(cmd + " is a shell builtin");
                    } else {
                        String path = findInPath(cmd);
                        if (path != null)
                            System.out.println(cmd + " is " + path);
                        else
                            System.out.println(cmd + ": not found");
                    }
                }
            } else if (command.equals("echo")) {

                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < inputs.size(); i++) {
                    sb.append(inputs.get(i));
                    if (i < inputs.size() - 1)
                        sb.append(" ");
                }
                System.out.println(sb.toString());
            } else if (command.equals("exit")) {
                int code = (inputs.size() < 2) ? 0 : Integer.parseInt(inputs.get(1));
                System.exit(code);
            } else {

                String execPath = findInPath(command);
                if (execPath != null) {

                    Process p = new ProcessBuilder(inputs).inheritIO().start();
                    p.waitFor();
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }
}