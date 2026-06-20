import java.util.Scanner;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            String[] parts = input.split(" ");
            String command = parts[0];

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {

                StringBuilder message = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    message.append(parts[i]).append(i == parts.length - 1 ? "" : " ");
                }
                System.out.println(message.toString());
            } else if (command.equals("type")) {
                if (parts.length < 2) {

                } else {
                    String target = parts[1];
                    if (target.equals("exit") || target.equals("echo") || target.equals("type")) {
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

                    List<String> commandList = new ArrayList<>(Arrays.asList(parts));

                    commandList.set(0, fullPath);

                    ProcessBuilder pb = new ProcessBuilder(commandList);

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