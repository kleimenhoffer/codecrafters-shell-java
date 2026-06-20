import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();

            if (input.equals("exit")) {
                break;
            } else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } else if (input.equals("echo")) {
                System.out.println();
            }

            else if (input.startsWith("type ")) {

                String command = input.substring(5).trim();

                if (command.equals("exit") || command.equals("echo") || command.equals("type")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    System.out.println(command + ": not found");
                }
            }

            else {
                System.out.println(input + ": command not found");
            }
        }
    }
}