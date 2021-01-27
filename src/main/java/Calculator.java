import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.apfloat.Apfloat;
import org.apfloat.ApfloatMath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.List;

public class Calculator {
    private static final HashMap<String, Apfloat> calculationCache = new HashMap<>();

    private final int maxPrecision;
    private int pos = -1, ch, precision = 100;
    private boolean b1 = false, forcePrecision = false;
    private final String inputStr;

    Calculator(String inputStr, int maxPrecision) {
        this.inputStr = sanitizeInputString(inputStr);
        if (maxPrecision == -1) {
            this.maxPrecision = 1000000;
        } else {
            this.maxPrecision = maxPrecision;
            this.precision = maxPrecision;
            forcePrecision = true;
        }
    }

    private String sanitizeInputString(String inputStr) {
        return inputStr.toLowerCase()
                .replaceAll("\\s", "")
                .replace("\"", "")
                .replace("**", "^")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("e", "(e)")
                .replace("π", "(π)")
                .replace("pi", "(π)")
                .replace("τ", "(τ)")
                .replace("tau", "(τ)")
                .replace("rand()", "rand(1)")
                .replace(")(", ")*(")
                .replace("0(", "0*(")
                .replace("1(", "1*(")
                .replace("2(", "2*(")
                .replace("3(", "3*(")
                .replace("4(", "4*(")
                .replace("5(", "5*(")
                .replace("6(", "6*(")
                .replace("7(", "7*(")
                .replace("8(", "8*(")
                .replace("9(", "9*(");
    }

    public Apfloat calculate() {
        if (calculationCache.containsKey(inputStr)) {
            return calculationCache.get(inputStr);
        }
        Apfloat result = parse();
        calculationCache.put(inputStr, result);
        return result;
    }

    private void nextChar() {
        ch = (++pos < inputStr.length()) ? inputStr.charAt(pos) : -1;
    }

    private boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    private Apfloat parse() {
        nextChar();
        Apfloat  x = parseExpression();
        if (pos < inputStr.length()) throw new RuntimeException("Unexpected: \"" + (char)ch + "\" in \"" + inputStr + "\" at Index: " + pos);
        if(forcePrecision || b1) {
            return x;
        } else {
            pos = -1;
            String str = x.toString(true);
            if (str.contains(".")) {
                precision = str.lastIndexOf('.') + 16;
                b1 = true;
            } else {
                if (precision > str.length() + 5) return x;
                precision = str.length() + 16;
            }
            if(precision > maxPrecision) {
                precision = maxPrecision;
                b1 = true;
            }
            return parse();
        }
    }

    // Grammar:
    // expression = term | expression `+` term | expression `-` term
    // term = factor | term `*` factor | term `/` factor
    // factor = `+` factor | `-` factor | `(` expression `)`
    //        | number | functionName factor | factor `^` factor

    private Apfloat  parseExpression() {
        Apfloat  x = parseTerm();
        for (;;) {
            if      (eat('+')) x = x.add(parseTerm()); // addition
            else if (eat('-')) x = x.subtract(parseTerm()); // subtraction
            else return x;
        }
    }

    private Apfloat  parseTerm() {
        Apfloat  x = parseFactor();
        while (true) {
            if      (eat('*')) x = x.multiply(parseFactor()); // multiplication
            else if (eat('/')) x = x.divide(parseFactor()); // division
            else return x;
        }
    }

    private Apfloat parseFactor() {
        if (eat('+')) return parseFactor(); // unary plus
        if (eat('-')) return parseFactor().multiply(new Apfloat("-1", precision)); // unary minus

        Apfloat x;
        int startPos = this.pos;
        if (eat('(')) { // parentheses
            x = parseExpression();
            eat(')');
        } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
            while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
            x = new Apfloat(inputStr.substring(startPos, this.pos), precision);
        } else if(eat('π')) { //pi
            x = ApfloatMath.pi(precision);
        } else if(eat('e')) { //e:
            x = e(precision);
        } else if (eat('τ')) {
            x = ApfloatMath.pi(precision).multiply(new Apfloat(2, precision));
        } else if (ch >= 'a' && ch <= 'z') { // functions
            while (ch >= 'a' && ch <= 'z') nextChar();
            String func = inputStr.substring(startPos, this.pos);
            x = parseFactor();
            switch (func) {
                case "sqrt":
                    x = ApfloatMath.sqrt(x);
                    break;
                case "sin":
                    x = ApfloatMath.sin(ApfloatMath.toRadians(x));
                    break;
                case "cos":
                    x = ApfloatMath.cos(ApfloatMath.toRadians(x));
                    break;
                case "tan":
                    x = ApfloatMath.tan(ApfloatMath.toRadians(x));
                    break;
                case "arcsin":
                    x = ApfloatMath.toDegrees(ApfloatMath.asin(x));
                    break;
                case "arccos":
                    x = ApfloatMath.toDegrees(ApfloatMath.acos(x));
                    break;
                case "arctan":
                    x = ApfloatMath.toDegrees(ApfloatMath.atan(x));
                    break;
                case "sinh":
                    x = ApfloatMath.sinh(x);
                    break;
                case "cosh":
                    x = ApfloatMath.cosh(x);
                    break;
                case "tanh":
                    x = ApfloatMath.tanh(x);
                    break;
                case "log":
                    x = ApfloatMath.log(x);
                    break;
                case "cbrt":
                    x = ApfloatMath.cbrt(x);
                    break;
                case "rand":
                    x = ApfloatMath.random(precision).multiply(x);
                    break;
                case "w":
                    x = ApfloatMath.w(x);
                    break;
                case "rad":
                    x = ApfloatMath.toRadians(x);
                    break;
                case "degree":
                    x = ApfloatMath.toDegrees(x);
                    break;
                case "gamma":
                    x = ApfloatMath.gamma(x);
                    break;
                case "floor":
                case "int":
                    x = x.floor();
                    break;
                default:
                    throw new RuntimeException("Unknown function: " + func);
            }
        } else {
            if (ch != -1) throw new RuntimeException("Unexpected: \"" + (char) ch + "\" in \"" + inputStr + "\" at Index: " + pos);
            else throw new RuntimeException("Missing Character after: \"" + inputStr.charAt(pos - 1) + "\"");
        }

        if (eat('^')) x = ApfloatMath.pow(x, parseFactor()); // exponentiation
        return x;
    }

    private static Apfloat e(int precision) {
        Apfloat e = new Apfloat(1, precision);
        Apfloat fact = new Apfloat(1, precision);
        for (int i = 1; i < precision; i++) {
            fact = fact.multiply(new Apfloat(i));
            e = e.add(Apfloat.ONE.divide(fact));
        }
        return e;
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                Terminal terminal = new DefaultTerminalFactory(System.out, System.in, StandardCharsets.UTF_8).createTerminal();

                writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);

                List<String> history = new LinkedList<>();
                int historyIndex = 0;

                List<Character> chars = new LinkedList<>();
                while (true) {
                    boolean up = true;
                    KeyStroke stroke = terminal.pollInput();
                    if (stroke != null) {
                        switch (stroke.getKeyType()) {
                            case Backspace: {
                                int x = terminal.getCursorPosition().getColumn() - 3;
                                if (chars.size() > x && x >= 0) {
                                    chars.remove(x--); // move x left
                                    if (stroke.isCtrlDown()) {
                                        for (; chars.size() > x && x >= 0; x--) {
                                            if (Character.isWhitespace(chars.get(x))) {
                                                break;
                                            } else {
                                                chars.remove(x);
                                            }
                                        }
                                    }
                                    String str = charsToString(chars);
                                    writeToTerminal(terminal, str + " ", null, null, 2);
                                    terminal.setCursorPosition(x + 3, terminal.getTerminalSize().getRows());
                                    terminal.flush();
                                }
                                break;
                            }
                            case Character: {
                                char ch = stroke.getCharacter();
                                if (ch == '?') {
                                    if (chars.size() > 0) {
                                        history.add(charsToString(chars));
                                        chars.clear();
                                    }
                                    displayHelp(terminal);
                                    writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);
                                    break;
                                }
                                if (chars.size() + 2 < terminal.getTerminalSize().getColumns()) {
                                    int x = terminal.getCursorPosition().getColumn() - 2;
                                    chars.add(x, ch);
                                    writeToTerminal(terminal, charsToString(chars), null, null, 2);
                                    if (x < chars.size() - 1) {
                                        moveCursorX(terminal, x - chars.size());
                                    }
                                }
                                break;
                            }
                            case Enter: {
                                String line =  charsToString(chars);
                                writeToTerminal(terminal, line + "\n", null, null, 2);

                                historyIndex = 0;
                                if (!line.isEmpty()) {
                                    if (line.equals("quit") || line.equals("exit")) {
                                        terminal.close();
                                        System.exit(0);
                                    } else if (line.equals("help")) {
                                        displayHelp(terminal);
                                        writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);
                                        chars.clear();
                                        break;
                                    }
                                    history.add(line);
                                    try {
                                        calculate(terminal, line.split(" "));
                                    } catch (RuntimeException e) {
                                        writeToTerminal(terminal, e.getMessage() + "\n", TextColor.ANSI.RED);
                                    }
                                }
                                writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);
                                chars.clear();

                                break;
                            }
                            case ArrowLeft: {
                                moveCursorX(terminal, -1);
                                break;
                            }
                            case ArrowRight: {
                                moveCursorX(terminal, 1);
                                break;
                            }
                            case ArrowDown:
                                up = false;
                            case ArrowUp: {
                                historyIndex += up ? 1 : -1;
                                if (historyIndex < 0) {
                                    historyIndex = 0;
                                    break;
                                } else if (historyIndex > history.size()) {
                                    historyIndex = history.size();
                                    break;
                                }
                                writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);
                                chars.clear();

                                if (historyIndex != 0) {
                                    writeToTerminal(terminal, history.get(history.size() - historyIndex), null, null, 2);
                                    for (char ch : history.get(history.size() - historyIndex).toCharArray()) {
                                        chars.add(ch);
                                        ;
                                    }
                                }
                            }
                        }
                        terminal.flush();
                    } else {
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException ignored) {}
                    }
                }
            } else {
                Terminal terminal = new DefaultTerminalFactory(System.out, System.in, StandardCharsets.UTF_8).createTerminal();
                calculate(terminal, args);
                terminal.close();
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final static String HELP_STRING = "To quit write 'quit'/'exit'.\n" +
            "You can use following functions:\n"
            + " - sqrt\n"
            + " - cbrt\n"
            + " - sin\n"
            + " - cos\n"
            + " - tan\n"
            + " - arcsin\n"
            + " - arccos\n"
            + " - arctan\n"
            + " - sinh\n"
            + " - cosh\n"
            + " - tanh\n"
            + " - log [natural log]\n"
            + " - rand [random number]\n"
            + " - w\n"
            + " - rad\n"
            + " - degree\n"
            + " - int/floor\n"
            + " - gamma\n" +
            "You can use following operators:\n"
            + " - '*'/'×'\n"
            + " - '/'/'÷'\n"
            + " - '+'\n"
            + " - '-'\n"
            + " - '^'/'**'\n" +
            "You can use following constants:\n"
            + " - pi/π\n"
            + " - tau/τ\n"
            + " - e\n" +
            "You can use following flags:\n"
            + " - '--exponential'/'--e'/'--scientific'/'--s'\n"
            + " - '--precision=99'/'--p=99'\n";

    private static void displayHelp(Terminal terminal) throws IOException {
        writeToTerminal(terminal, HELP_STRING);
    }

    private static void moveCursorX(Terminal terminal, int x) throws IOException {
        TerminalPosition cursorPos = terminal.getCursorPosition();
        terminal.setCursorPosition(cursorPos.getColumn() + x, cursorPos.getRow());
    }

    private static String charsToString(List<Character> chars) {
        StringBuilder sb = new StringBuilder(chars.size());
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static void writeToTerminal(Terminal terminal, String str, TextColor color, SGR sgr, int cursorX) throws IOException {
        if (terminal != null) {
            if (cursorX == 0) {
                terminal.newTextGraphics().drawLine(0, terminal.getTerminalSize().getRows(), terminal.getTerminalSize().getColumns(), terminal.getTerminalSize().getRows() , ' ');
            }
            terminal.setForegroundColor(color == null ? TextColor.ANSI.DEFAULT : color);
            if (sgr != null) terminal.enableSGR(sgr);

            terminal.setCursorPosition(cursorX, terminal.getTerminalSize().getRows());
            terminal.putString(str);

            terminal.resetColorAndSGR();
            terminal.setForegroundColor(TextColor.ANSI.WHITE);

            terminal.flush();
        } else {
            System.out.println(str.trim());
        }
    }

    private static void writeToTerminal(Terminal terminal, String str, TextColor color, SGR sgr) throws IOException {
        writeToTerminal(terminal, str, color, sgr, 0);
    }

    private static void writeToTerminal(Terminal terminal, String str, TextColor color) throws IOException {
        writeToTerminal(terminal, str, color, null);
    }

    private static void writeToTerminal(Terminal terminal, String str) throws IOException {
        writeToTerminal(terminal, str, null);
    }

    private static void calculate(Terminal terminal, String[] input) throws IOException {
        long time = System.nanoTime();

        StringBuilder sb = new StringBuilder(input.length);
        boolean pretty = true;
        int precision = -1;
        for (String s : input) {
            String arg = s.toLowerCase();
            if (arg.startsWith("--")) {
                if (arg.equals("--exponential") || arg.equals("--scientific")
                        || arg.equals("--e") || arg.equals("--s")) {
                    pretty = false;
                } else if (arg.startsWith("--precision") || arg.startsWith("--p")) {
                    try {
                        precision = Integer.parseInt(arg.replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException e) {
                        writeToTerminal(terminal, e.getMessage() + "\n", TextColor.ANSI.RED);
                        writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);
                    }
                }
            } else {
                sb.append(arg);
            }
        }

        String result = new Calculator(sb.toString(), precision).parse().toString(pretty);
        if (!pretty) {
            result = result.replace("e", "×10^");
        }
        writeToTerminal(terminal, "→ " + result + "\n", TextColor.ANSI.GREEN_BRIGHT, SGR.BOLD);
        writeToTerminal(terminal, String.format("Calculated in %fms\n", (System.nanoTime() - time) / 1_000_000.0), TextColor.ANSI.BLUE_BRIGHT);
    }
}