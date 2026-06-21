package io.codecrafters.terminal.parser;

import static io.codecrafters.terminal.parser.Redirect.Mode.APPEND;
import static io.codecrafters.terminal.parser.Redirect.Mode.OVERWRITE;
import static io.codecrafters.terminal.parser.Redirect.Stream.STDERR;
import static io.codecrafters.terminal.parser.Redirect.Stream.STDOUT;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class InputParser {

    private enum State {
        INSIDE_SINGLE_QUOTES,
        INSIDE_DOUBLE_QUOTES,
        BACKSLASH,
        DOUBLE_QUOTES_BACKSLASH,
        RAW
    }

    public ParsedInput parse(String input) {
        if (input.isBlank()) {
            return null;
        }

        List<String> tokens = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        State currenState = State.RAW;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            switch (currenState) {
                case RAW -> {
                    if (c == '\'') {
                        currenState = State.INSIDE_SINGLE_QUOTES;
                    } else if (c == '\"') {
                        currenState = State.INSIDE_DOUBLE_QUOTES;
                    } else if (c == '\\') {
                        currenState = State.BACKSLASH;
                    } else if (c != ' ') {
                        current.append(c);
                    } else if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                }
                case INSIDE_SINGLE_QUOTES -> {
                    if (c == '\'') {
                        currenState = State.RAW;
                    } else {
                        current.append(c);
                    }
                }
                case INSIDE_DOUBLE_QUOTES -> {
                    switch (c) {
                        case '\"' -> currenState = State.RAW;
                        case '\\' -> currenState = State.DOUBLE_QUOTES_BACKSLASH;
                        default -> current.append(c);
                    }
                }
                case BACKSLASH -> {
                    current.append(c);
                    currenState = State.RAW;
                }
                case DOUBLE_QUOTES_BACKSLASH -> {
                    Set<Character> specialChars = Set.of('"', '\\');
                    if (specialChars.contains(c)) {
                        current.append(c);
                    } else {
                        current.append("\\").append(c);
                    }
                    currenState = State.INSIDE_DOUBLE_QUOTES;
                }
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return buildParsedInput(tokens);
    }

    private ParsedInput buildParsedInput(List<String> tokens) {
        String command = tokens.getFirst();
        List<String> arguments = new ArrayList<>();
        List<Redirect> redirects = new ArrayList<>();

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            switch (token) {
                case ">", "1>" ->
                    redirects.add(
                            new Redirect(STDOUT, consumeTarget(tokens, ++i, token), OVERWRITE));
                case ">>", "1>>" ->
                    redirects.add(
                            new Redirect(STDOUT, consumeTarget(tokens, ++i, token), APPEND));
                case "2>" ->
                    redirects.add(
                            new Redirect(STDERR, consumeTarget(tokens, ++i, token), OVERWRITE));
                case "2>>" ->
                    redirects.add(
                            new Redirect(STDERR, consumeTarget(tokens, ++i, token), APPEND));
                default -> arguments.add(token);
            }
        }

        return new ParsedInput(command, arguments, redirects);
    }

    private Path consumeTarget(List<String> tokens, int index, String operator) {
        if (index >= tokens.size()) {
            throw new IllegalArgumentException("Expected filename after '" + operator + "'");
        }
        return Paths.get(tokens.get(index));
    }
}