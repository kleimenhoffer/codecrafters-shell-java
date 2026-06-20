import java.util.Scanner;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();

            if (input.equals("exit")) {
                break;
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else if (input.equals("echo")) {
                System.out.println();
            } else if (input.startsWith("type ")) {
                String command = input.substring(5).trim();

                if (command.equals("exit") || command.equals("echo") || command.equals("type")) {
                    System.out.println(command + " is a shell builtin");
                } else {

                    String pathEnv = System.getenv("PATH");

                    String[] folders = pathEnv.split(File.pathSeparator);

                    boolean found = false;
                    for (String folder : folders) {

                        File file = new File(folder, command);

                        if (file.exists() && Files.isExecutable(Paths.get(file.getAbsolutePath()))) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.out.println(command + ": not found");
                    }
                }
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}