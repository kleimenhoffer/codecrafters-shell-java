import static java.lang.IO.readln;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

public class Main {
    record Redirect(String path, boolean append) {
    }

    record Output(String std, String err) {
    }

    static final Set<String> BUILTINS = Set.of("cd", "echo", "exit", "pwd", "type", "jobs");
    // --- NEW: Counter for background jobs ---
    static int jobCounter = 0;

    public static void main(String[] args) {
        while (true) {
            final String input = readln("$ ");
            if (input == null)
                break;
            final String[] tokens = tokenize(input);
            if (tokens.length == 0)
                continue;

            var stdout = Optional.<Redirect>empty();
            var stderr = Optional.<Redirect>empty();

            int i = 1;
            while (i < tokens.length) {
                if (tokens[i].equals("1>") || tokens[i].equals(">")) {
                    if (i + 1 < tokens.length) {
                        stdout = Optional.of(new Redirect(tokens[++i], false));
                    }
                    i++;
                } else if (tokens[i].equals("1>>") || tokens[i].equals(">>")) {
                    if (i + 1 < tokens.length) {
                        stdout = Optional.of(new Redirect(tokens[++i], true));
                    }
                    i++;
                } else if (tokens[i].equals("2>")) {
                    if (i + 1 < tokens.length) {
                        stderr = Optional.of(new Redirect(tokens[++i], false));
                    }
                    i++;
                } else if (tokens[i].equals("2>>")) {
                    if (i + 1 < tokens.length) {
                        stderr = Optional.of(new Redirect(tokens[++i], true));
                    }
                    i++;
                } else {
                    i++;
                }
            }

            // --- NEW: Check for background execution '&' ---
            boolean isBackground = false;
            List<String> activeTokens = new ArrayList<>(Arrays.asList(tokens));
            if (!activeTokens.isEmpty() && activeTokens.get(activeTokens.size() - 1).equals("&")) {
                isBackground = true;
                activeTokens.remove(activeTokens.size() - 1);
            }

            // We pass the activeTokens and the background flag to handle
            var output = handle(activeTokens.toArray(new String[0]), isBackground, stdout, stderr);

            BiConsumer<Optional<Redirect>, String> lambda = (o, str) -> o.ifPresentOrElse(
                    r -> {
                        try {
                            var path = Paths.get(r.path());
                            if (path.getParent() != null)
                                Files.createDirectories(path.getParent());

                            if (r.append()) {
                                Files.writeString(path, str,
                                        StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.APPEND);
                            } else {
                                Files.writeString(path, str,
                                        StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.TRUNCATE_EXISTING);
                            }
                        } catch (IOException e) {
                            e.printStackTrace(System.out);
                        }
                    },
                    () -> System.out.append(str));

            lambda.accept(stdout, output.std());
            lambda.accept(stderr, output.err());
        }
    }

    static Output handle(String[] tokens, boolean isBackground, Optional<Redirect> stdout, Optional<Redirect> stderr) {
        try (var outStream = new ByteArrayOutputStream();
                var errStream = new ByteArrayOutputStream();
                var out = new PrintStream(outStream);
                var err = new PrintStream(errStream)) {

            List<String> cleanTokens = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].equals(">") || tokens[i].equals("1>") || tokens[i].equals(">>") ||
                        tokens[i].equals("1>>") || tokens[i].equals("2>") || tokens[i].equals("2>>")) {
                    i++;
                    continue;
                }
                cleanTokens.add(tokens[i]);
            }
            String[] args = cleanTokens.toArray(new String[0]);
            if (args.length == 0)
                return new Output("", "");

            switch (args[0]) {
                case "exit" -> System.exit(0);
                case "echo" -> {
                    if (args.length != 1) {
                        for (int i = 1; i < args.length; i++) {
                            out.print(args[i] + (i == args.length - 1 ? "" : " "));
                        }
                        out.println();
                    }
                }
                case "pwd" -> out.println(System.getProperty("user.dir"));
                case "cd" -> {
                    if (args.length == 1)
                        err.println("cd: missing operand");
                    else if (args.length > 2)
                        err.println("cd: too many arguments");
                    else if (args[1].equals("~"))
                        System.setProperty("user.dir", System.getenv("HOME"));
                    else {
                        var currentPath = Paths.get(System.getProperty("user.dir"));
                        var newPath = currentPath.resolve(args[1]).normalize();

                        if (Files.exists(newPath) && Files.isDirectory(newPath))
                            System.setProperty("user.dir", newPath.toAbsolutePath().toString());
                        else
                            err.printf("cd: %s: No such file or directory%n", args[1]);
                    }
                }
                case "type" -> {
                    if (args.length == 1)
                        err.println("type: missing operand");
                    else if (args.length > 2)
                        err.println("type: too many arguments");
                    else if (BUILTINS.contains(args[1]))
                        out.println(args[1] + " is a shell builtin");
                    else
                        getFile(args[1]).ifPresentOrElse(
                                f -> out.println(args[1] + " is " + f.getAbsolutePath()),
                                () -> err.println(args[1] + ": not found"));
                }
                case "jobs" -> {
                    // Empty for now as per previous stage
                }
                default -> getFile(args[0]).ifPresentOrElse(
                        _ -> runProgram(args, out, err, isBackground, stdout, stderr),
                        () -> err.println(args[0] + ": command not found"));
            }
            out.flush();
            err.flush();
            return new Output(outStream.toString(), errStream.toString());
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return new Output("", "");
        }
    }

    static Optional<File> getFile(String token) {
        final String PATH = System.getenv("PATH");
        if (PATH == null)
            return Optional.empty();
        for (final String filePath : PATH.split(File.pathSeparator)) {
            final var file = new File(filePath + File.separator + token);
            if (file.exists() && file.canExecute())
                return Optional.of(file);
        }
        return Optional.empty();
    }

    static void runProgram(String[] tokens, PrintStream out, PrintStream err, boolean isBackground,
            Optional<Redirect> stdout, Optional<Redirect> stderr) {
        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);

            // Handle redirection
            if (stdout.isPresent())
                pb.redirectOutput(new File(stdout.get().path()));
            else if (!isBackground)
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

            if (stderr.isPresent())
                pb.redirectError(new File(stderr.get().path()));
            else if (!isBackground)
                pb.redirectError(ProcessBuilder.Redirect.PIPE);

            Process process = pb.start();

            if (isBackground) {
                // --- BACKGROUND LOGIC ---
                jobCounter++;
                System.out.println("[" + jobCounter + "] " + process.pid());
                // We do NOT call process.waitFor(), so the shell continues immediately
            } else {
                // --- FOREGROUND LOGIC ---
                // Read streams and send them to our captured ByteArrays
                process.getInputStream().transferTo(out);
                process.getErrorStream().transferTo(err);
                process.waitFor();
            }
        } catch (IOException | InterruptedException e) {
            err.println("An I/O error occurred: " + e.getMessage());
        }
    }

    static String[] tokenize(String s) {
        var tokens = new ArrayList<String>();
        var sb = new StringBuilder();
        boolean inSingleQuote = false, inDoubleQuote = false;

        int i = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length())
                sb.append(inSingleQuote ? c : s.charAt(++i));
            else if (c == '\'' && !inDoubleQuote)
                inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) {
                if (inDoubleQuote && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote && Character.isWhitespace(c)) {
                if (!sb.isEmpty()) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        if (!sb.isEmpty())
            tokens.add(sb.toString());
        return tokens.toArray(String[]::new);
    }
}