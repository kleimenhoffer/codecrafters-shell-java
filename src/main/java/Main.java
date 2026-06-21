import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {

    static class BackgroundJob {
        int id;
        long pid;
        String command;
        Process process;

        BackgroundJob(int id, long pid, String command, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    static List<BackgroundJob> jobs = new ArrayList<>();
    static int nextJobId = 1;

    static File currentDirectory = new File(System.getProperty("user.dir"));

    static void reapBackgroundJobs(boolean showAll) {
        List<BackgroundJob> currentJobs = new ArrayList<>(jobs);
        if (currentJobs.isEmpty()) {
            return;
        }

        String[] statuses = new String[currentJobs.size()];
        char[] markers = new char[currentJobs.size()];

        for (int i = 0; i < currentJobs.size(); i++) {
            BackgroundJob job = currentJobs.get(i);
            boolean alive = job.process.isAlive();
            statuses[i] = alive ? "Running" : "Done";

            char marker = ' ';
            if (i == currentJobs.size() - 1) {
                marker = '+';
            } else if (i == currentJobs.size() - 2) {
                marker = '-';
            }
            markers[i] = marker;
        }

        for (int i = 0; i < currentJobs.size(); i++) {
            BackgroundJob job = currentJobs.get(i);
            String status = statuses[i];
            char marker = markers[i];

            if (showAll || status.equals("Done")) {
                if (status.equals("Running")) {
                    System.out.printf("[%d]%c  %-24s%s &\n", job.id, marker, status, job.command);
                } else {
                    System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, status, job.command);
                }
            }
        }

        jobs.removeIf(job -> !job.process.isAlive());
    }

    public static void main(String[] args) throws IOException {

        try (Scanner scanner = new Scanner(System.in)) {

            while (true) {

                reapBackgroundJobs(false);

                System.out.print("$ ");

                if (!scanner.hasNextLine())
                    break;

                String input = scanner.nextLine().trim();
                if (input.isEmpty())
                    continue;

                List<String> tokens = tokenize(input);
                if (tokens.isEmpty())
                    continue;

                // Parse background operator '&'
                boolean isBackground = false;
                if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                    isBackground = true;
                    tokens.remove(tokens.size() - 1);
                }

                // Parse redirection
                String redirectOutPath = null;
                boolean appendOut = false;
                String redirectErrPath = null;
                boolean appendErr = false;

                List<String> cmdArgs = new ArrayList<>();

                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i);
                    if (token.equals(">") || token.equals("1>")) {
                        if (i + 1 < tokens.size()) {
                            redirectOutPath = tokens.get(i + 1);
                            i++;
                        }
                    } else if (token.equals(">>") || token.equals("1>>")) {
                        if (i + 1 < tokens.size()) {
                            redirectOutPath = tokens.get(i + 1);
                            appendOut = true;
                            i++;
                        }
                    } else if (token.equals("2>")) {
                        if (i + 1 < tokens.size()) {
                            redirectErrPath = tokens.get(i + 1);
                            i++;
                        }
                    } else if (token.equals("2>>")) {
                        if (i + 1 < tokens.size()) {
                            redirectErrPath = tokens.get(i + 1);
                            appendErr = true;
                            i++;
                        }
                    } else {
                        cmdArgs.add(token);
                    }
                }

                if (cmdArgs.isEmpty())
                    continue;
                String command = cmdArgs.get(0);

                // Set up redirection for builtins
                java.io.PrintStream originalOut = System.out;
                java.io.PrintStream originalErr = System.err;
                java.io.PrintStream outRedirectStream = null;
                java.io.PrintStream errRedirectStream = null;

                try {
                    if (redirectOutPath != null) {
                        File file = new File(redirectOutPath);
                        if (!file.isAbsolute()) {
                            file = new File(currentDirectory, redirectOutPath);
                        }
                        if (file.getParentFile() != null) {
                            file.getParentFile().mkdirs();
                        }
                        outRedirectStream = new java.io.PrintStream(
                                new java.io.FileOutputStream(file, appendOut));
                        System.setOut(outRedirectStream);
                    }

                    if (redirectErrPath != null) {
                        File file = new File(redirectErrPath);
                        if (!file.isAbsolute()) {
                            file = new File(currentDirectory, redirectErrPath);
                        }
                        if (file.getParentFile() != null) {
                            file.getParentFile().mkdirs();
                        }
                        errRedirectStream = new java.io.PrintStream(
                                new java.io.FileOutputStream(file, appendErr));
                        System.setErr(errRedirectStream);
                    }

                    // ---------------- EXIT ----------------
                    if (command.equals("exit")) {
                        if (cmdArgs.size() == 1 || cmdArgs.get(1).equals("0")) {
                            break;
                        }
                    }

                    // ---------------- ECHO ----------------
                    else if (command.equals("echo")) {
                        for (int i = 1; i < cmdArgs.size(); i++) {
                            if (i > 1)
                                System.out.print(" ");
                            System.out.print(cmdArgs.get(i));
                        }
                        System.out.println();
                    }

                    // ---------------- PWD ----------------
                    else if (command.equals("pwd")) {
                        System.out.println(currentDirectory.getCanonicalPath());
                    }

                    // ---------------- CD ----------------
                    else if (command.equals("cd")) {
                        if (cmdArgs.size() > 1) {
                            String original = cmdArgs.get(1);
                            String path = original;

                            // HOME support
                            if (path.equals("~")) {
                                path = System.getenv("HOME");
                            } else if (path.startsWith("~/")) {
                                path = System.getenv("HOME") + path.substring(1);
                            }

                            File newDir;
                            if (path.startsWith("/")) {
                                newDir = new File(path);
                            } else {
                                newDir = new File(currentDirectory, path);
                            }

                            try {
                                newDir = newDir.getCanonicalFile();
                                if (newDir.exists() && newDir.isDirectory()) {
                                    currentDirectory = newDir;
                                } else {
                                    System.out.println(
                                            "cd: " + original + ": No such file or directory");
                                }
                            } catch (Exception e) {
                                System.out.println(
                                        "cd: " + original + ": No such file or directory");
                            }
                        }
                    }

                    // ---------------- TYPE ----------------
                    else if (command.equals("type")) {
                        if (cmdArgs.size() > 1) {
                            String arg = cmdArgs.get(1);
                            if (arg.equals("echo")
                                    || arg.equals("exit")
                                    || arg.equals("type")
                                    || arg.equals("pwd")
                                    || arg.equals("cd")
                                    || arg.equals("jobs")) {
                                System.out.println(arg + " is a shell builtin");
                            } else {
                                File exe = findExecutable(arg);
                                if (exe != null) {
                                    System.out.println(arg + " is " + exe.getAbsolutePath());
                                } else {
                                    System.out.println(arg + ": not found");
                                }
                            }
                        }
                    }

                    // ---------------- JOBS ----------------
                    else if (command.equals("jobs")) {
                        reapBackgroundJobs(true);
                    }

                    // ---------------- EXTERNAL COMMANDS ----------------
                    else {
                        File exe = findExecutable(command);
                        if (exe != null) {
                            try {
                                ProcessBuilder pb = new ProcessBuilder(cmdArgs);
                                pb.directory(currentDirectory);

                                if (redirectOutPath != null) {
                                    File file = new File(redirectOutPath);
                                    if (!file.isAbsolute()) {
                                        file = new File(currentDirectory, redirectOutPath);
                                    }
                                    if (file.getParentFile() != null) {
                                        file.getParentFile().mkdirs();
                                    }
                                    pb.redirectOutput(
                                            appendOut
                                                    ? ProcessBuilder.Redirect.appendTo(file)
                                                    : ProcessBuilder.Redirect.to(file));
                                } else {
                                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                }

                                if (redirectErrPath != null) {
                                    File file = new File(redirectErrPath);
                                    if (!file.isAbsolute()) {
                                        file = new File(currentDirectory, redirectErrPath);
                                    }
                                    if (file.getParentFile() != null) {
                                        file.getParentFile().mkdirs();
                                    }
                                    pb.redirectError(
                                            appendErr
                                                    ? ProcessBuilder.Redirect.appendTo(file)
                                                    : ProcessBuilder.Redirect.to(file));
                                } else {
                                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                }

                                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                Process p = pb.start();

                                if (isBackground) {
                                    long pid = p.pid();
                                    int jobId = nextJobId++;
                                    String jobCmd = String.join(" ", cmdArgs);
                                    jobs.add(new BackgroundJob(jobId, pid, jobCmd, p));
                                    System.out.println("[" + jobId + "] " + pid);
                                } else {
                                    p.waitFor();
                                }
                            } catch (Exception e) {
                                System.out.println(command + ": command failed");
                            }
                        } else {
                            System.out.println(command + ": command not found");
                        }
                    }

                } catch (IOException e) {
                    originalErr.println("shell: failed to redirect: " + e.getMessage());
                } finally {
                    if (outRedirectStream != null) {
                        outRedirectStream.close();
                    }
                    if (errRedirectStream != null) {
                        errRedirectStream.close();
                    }
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
            }
        }
    }

    // ---------------- TOKENIZER (quotes) ----------------
    static List<String> tokenize(String input) {

        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        boolean inSingle = false;
        boolean inDouble = false;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {

            char c = input.charAt(i);

            if (!inSingle && c == '\\' && !escape) {
                escape = true;
                continue;
            }

            if (escape) {
                cur.append(c);
                escape = false;
                continue;
            }

            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }

            if (c == ' ' && !inSingle && !inDouble) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(c);
        }

        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }

        return tokens;
    }

    // ---------------- FIND EXECUTABLE ----------------
    static File findExecutable(String command) {
        if (command.contains("/") || command.contains(File.separator)) {
            File file = new File(command);
            if (!file.isAbsolute()) {
                file = new File(currentDirectory, command);
            }
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
            return null;
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv == null)
            return null;

        for (String dir : pathEnv.split(File.pathSeparator)) {

            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }
}