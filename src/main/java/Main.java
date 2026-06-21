import java.io.*;
import java.util.*;
import java.nio.file.*;

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
        boolean insideDoubleQuote = false;
        boolean isEscaped = false;

        for (char c : input.toCharArray()) {
            if (isEscaped) {
                currentArg.append(c);
                isEscaped = false;
            } else if (c == '\\' && !insideSingleQuote) {
                isEscaped = true;
            } else if (c == '\'') {
                if (!insideDoubleQuote)
                    insideSingleQuote = !insideSingleQuote;
                else
                    currentArg.append(c);
            } else if (c == '\"') {
                if (!insideSingleQuote)
                    insideDoubleQuote = !insideDoubleQuote;
                else
                    currentArg.append(c);
            } else if (c == ' ' && !insideSingleQuote && !insideDoubleQuote) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
            } else {
                currentArg.append(c);
            }
        }
        if (currentArg.length() > 0)
            args.add(currentArg.toString());
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

            File outputFile = null;
            int redirectIndex = -1;

            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i).equals(">") || inputs.get(i).equals("1>")) {
                    redirectIndex = i;
                    if (i + 1 < inputs.size()) {
                        outputFile = new File(cwd, inputs.get(i + 1));
                    }
                    break;
                }
            }

            if (redirectIndex != -1) {
                if (redirectIndex + 1 < inputs.size()) {
                    inputs.remove(redirectIndex + 1);
                }
                inputs.remove(redirectIndex);
            }

            if (inputs.isEmpty())
                continue;
            String command = inputs.get(0);

            if (command.equals("pwd")) {
                printWithRedirection(cwd.getAbsolutePath(), outputFile);
            } else if (command.equals("cd")) {
                if (inputs.size() >= 2) {
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
                if (inputs.size() >= 2) {
                    String cmd = inputs.get(1);
                    if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd")
                            || cmd.equals("cd")) {
                        printWithRedirection(cmd + " is a shell builtin", outputFile);
                    } else {
                        String path = findInPath(cmd);
                        if (path != null)
                            printWithRedirection(cmd + " is " + path, outputFile);
                        else
                            printWithRedirection(cmd + ": not found", outputFile);
                    }
                }
            } else if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < inputs.size(); i++) {
                    sb.append(inputs.get(i));
                    if (i < inputs.size() - 1)
                        sb.append(" ");
                }
                printWithRedirection(sb.toString(), outputFile);
            } else if (command.equals("exit")) {
                int code = (inputs.size() < 2) ? 0 : Integer.parseInt(inputs.get(1));
                System.exit(code);
            } else {
                String execPath = findInPath(command);
                if (execPath != null) {
                    ProcessBuilder pb = new ProcessBuilder(inputs);
                    if (outputFile != null) {
                        pb.redirectOutput(outputFile);
                    } else {
                        pb.inheritIO();
                    }
                    Process p = pb.start();
                    p.waitFor();
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    private static void printWithRedirection(String text, File outputFile) {
        if (outputFile == null) {
            System.out.println(text);
        } else {
            try {

                PrintStream fileOut = new PrintStream(new FileOutputStream(outputFile));
                fileOut.println(text);
                fileOut.close();
            } catch (IOException e) {
                System.err.println("Error writing to file: " + e.getMessage());
            }
        }
    }
}