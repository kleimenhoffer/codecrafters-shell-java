import java.util.Scanner;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    // This is our "Virtual Tracker". It keeps track of where the shell "thinks" it
    // is.
    private static String currentDirectory = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();

            if (input.trim().isEmpty()) {
                continue;
            }

            String[] parts = input.split(" ");
            String command = parts[0];

            if (command.equals("exit")) {
                break;
            }
            // --- NEW LOGIC FOR CD STARTS HERE ---
            else if (command.equals("cd")) {
                if (parts.length < 2) {
                    // If user just types 'cd', we do nothing or could go to home
                } else {
                    String newPath = parts[1];

                    // We are only handling Absolute Paths for now (those starting with /)
                    if (newPath.startsWith("/")) {
                        File directory = new File(newPath);
                        if (directory.exists() && directory.isDirectory()) {
                            // Update our tracker!
                            currentDirectory = directory.getAbsolutePath();
                        } else {
                            System.out.println("cd: " + newPath + ": No such file or directory");
                        }
                    } else {
                        // For this stage, we ignore relative paths like '..' or '.'
                        System.out.println("cd: " + newPath + ": No such file or directory");
                    }
                }
            }
            // --- NEW LOGIC FOR CD ENDS HERE ---
            else if (command.equals("pwd")) {
                // Now we print the tracker variable, NOT the system property
                System.out.println(currentDirectory);
            } else if (command.equals("echo")) {
                StringBuilder message = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    message.append(parts[i]).append(i == parts.length - 1 ? "" : " ");
                }
                System.out.println(message.toString());
            } else if (command.equals("type")) {
                if (parts.length < 2) {
                    // Do nothing
                } else {
                    String target = parts[1];
                    // Added 'cd' to the list of builtins
                    if (target.equals("exit") || target.equals("echo") || target.equals("type") || target.equals("pwd")
                            || target.equals("cd")) {
                        System.out.println(target + " is a shell builtin");
                    } else {
                        String path = findExecutableInPath(target);
                        if (path != null) {
                            System.out.println(target + " is " + path);
                        } else {
                            System.out.println(target + ": not found");
                        }
                    }
                }
            } else {
                String fullPath = findExecutableInPath(command);
                if (fullPath != null) {
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", input);

                    // CRUCIAL: This tells the external program to run inside our virtual folder
                    pb.directory(new File(currentDirectory));

                    pb.inheritIO();
                    Process process = pb.start();
                    process.waitFor();
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }
    }

    private static String findExecutableInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null)
            return null;

        String[] folders = pathEnv.split(File.pathSeparator);
        for (String folder : folders) {
            File file = new File(folder, command);
            if (file.exists() && Files.isExecutable(Paths.get(file.getAbsolutePath()))) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}