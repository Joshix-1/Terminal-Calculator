import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.apfloat.Apfloat;
import org.apfloat.ApfloatMath;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class Calculator {
    private static final int DEFAULT_DECIMALS = 20;
    private static final HashMap<String, String> variables = new HashMap<>();

    private final int maxPrecision;
    private final String inputStr;

    private int pos = -1, ch, precision = 100;
    private boolean b1 = false, forcePrecision = false;
    private int historyIndex = 1;

    Calculator(String inputStr, int maxPrecision) {
        this.inputStr = inputStr;
        if (maxPrecision == -1) {
            this.maxPrecision = 1000000;
        } else {
            this.maxPrecision = maxPrecision;
            this.precision = maxPrecision;
            forcePrecision = true;
        }
    }

    private Calculator setHistoryIndex(int i) {
        historyIndex = i;
        return this;
    }

    //private static final Pattern ALMOST_ZERO = Pattern.compile("^0+1$");
    private static final Pattern ALMOST_ONE = Pattern.compile("^9+$");
    public Apfloat calculate() {
        Apfloat result = parse();
        if (result.isInteger() || forcePrecision) {
            return result;
        }
        String[] arr = result.toString(true).split("\\.");
        if (arr.length != 2) {
            return result;
        }
        String decimals = arr[1];
        if (decimals.length() < DEFAULT_DECIMALS - 1) {
            return result;
        }
        /*
        if (ALMOST_ZERO.matcher(decimals).matches()) {
            System.out.println(decimals);
            return result.floor();
        }
        */
        if (ALMOST_ONE.matcher(decimals).matches()) {
            return result.ceil();
        }
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
                precision = str.lastIndexOf('.') + DEFAULT_DECIMALS;
                b1 = true;
            } else {
                if (precision > str.length() + 5) return x;
                precision = str.length() + DEFAULT_DECIMALS;
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

    private boolean untilCharOrLeftBracket() {
        while (ch == ' ') nextChar();
        if (ch == '(' || Character.isLetter(ch)) {
            return true;
        }
        return false;
    }

    private Apfloat  parseTerm() {
        Apfloat  x = parseFactor();
        while (true) {
            if      (eat('*')) x = x.multiply(parseFactor()); // multiplication
            else if (untilCharOrLeftBracket()) {
                x = x.multiply(parseFactor());
            }
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
        } else if (ch >= 'a' && ch <= 'z') { // functions
            while (ch >= 'a' && ch <= 'z') nextChar();
            String func = inputStr.substring(startPos, this.pos);

            if (eat('(')) { // parentheses
                x = parseExpression();
                eat(')');

                switch (func) { //cannot start with e
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
                    case "ceil":
                        x = x.ceil();
                    case "round":
                        x = ApfloatMath.round(x, x.toString(true).split("\\.").length, RoundingMode.HALF_UP);
                        break;
                    default:
                        throw new RuntimeException("Unknown function: " + func);
                }
            } else { // vars and constants
                switch(func) {
                    case "ans": {
                        String last = history.get(history.size() - historyIndex);
                        String str = handleEqualSigns(last, precision, historyIndex + 1);
                        if (str == null) {
                            x = history.size() - historyIndex >= 0
                                    ? new Calculator(last.replaceAll("--[^\\s]+", ""), precision)
                                    .setHistoryIndex(historyIndex + 1).calculate()
                                    : Apfloat.ZERO;
                        } else if (str.equals("true")) {
                            x = new Apfloat(1, precision);
                        } else if (str.equals("false")) {
                            x = new Apfloat(0, precision);
                        } else {
                            x = new Apfloat(str, precision);
                        }
                        break;
                    }
                    case "c": {
                        x = new Apfloat(299792458, precision);
                        break;
                    }
                    case "e": {
                        x = e(precision);
                        break;
                    }
                    case "pi":
                    case "π": {
                        x = ApfloatMath.pi(precision);
                        break;
                    }
                    case "tau":
                    case "τ": {
                        x = ApfloatMath.pi(precision).multiply(new Apfloat(2, precision));
                        break;
                    }
                    default: {
                        if (variables.containsKey(func)) {
                            x = new Apfloat(variables.get(func), precision);
                        } else {
                            throw new RuntimeException("Unknown variable: " + func);
                        }
                    }
                }
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

    private static final List<String> history = new LinkedList<>();

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                Terminal terminal = new DefaultTerminalFactory(System.out, System.in, StandardCharsets.UTF_8).createTerminal();

                writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);

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
                                } else if (chars.size() <= x) {
                                    moveCursorX(terminal, -1);
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

                                if (chars.size() + 2 < terminal.getTerminalSize().getColumns() && terminal.getTerminalSize().getColumns() > 2) {
                                    int x = terminal.getCursorPosition().getColumn() - 2;
                                    while (x > chars.size()) {
                                        chars.add(' ');
                                    }
                                    if (x >= 0) {
                                        chars.add(x, ch);
                                        writeToTerminal(terminal, charsToString(chars), null, null, 2);
                                        if (x < chars.size() - 1) {
                                            moveCursorX(terminal, 1 + x - chars.size());
                                        }
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
                                    try {
                                        calculate(terminal, line);
                                    } catch (RuntimeException e) {
                                        writeToTerminal(terminal, e.getMessage() + "\n", TextColor.ANSI.RED);
                                    }
                                    history.add(line);
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
                                    }
                                }
                            }
                        }
                        terminal.flush();
                    } else {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {}
                    }
                }
            } else {
                Terminal terminal = new DefaultTerminalFactory(System.out, System.in, StandardCharsets.UTF_8).createTerminal();
                calculate(terminal, String.join(" ", args));
                terminal.close();
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final static String HELP_STRING = "To quit write 'quit'/'exit'.\n" +
            "You can use following functions (e.g. 'sqrt(2)'):\n"
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
            + " - int/floor [rounding down]\n"
            + " - ceil [rounding up]\n"
            + " - round [rounding mode: half up]"
            + " - gamma\n" +
            "You can use following operators (e.g. '3*3'):\n"
            + " - '*'/'×'\n"
            + " - '/'/'÷'\n"
            + " - '+'\n"
            + " - '-'\n"
            + " - '^'/'**'\n" +
            "You can use following constants:\n"
            + " - pi/π\n"
            + " - tau/τ\n"
            + " - e\n"
            + " - c=299792458\n" +
            "You can use following flags:\n"
            + " - '--exponential'/'--e'/'--scientific'/'--s'\n"
            + " - '--precision=99'/'--p=99'\n" +
            "You can compare expressions:\n"
            + " - 1=1=2-1 → true\n"
            + " - 1<3<2*3 → true\n"
            + " - 1<=3<=1*3 → true\n"
            + " - 2*3>3>1 → true\n"
            + " - 1*3>=3>=1 → true\n" +
            "You can assign variables:\n"
            + " - ':=' [x:=5 → {x=5 → true}]\n"
            + " - '*=' [x*=y → x:=x*y]\n"
            + " - '/=' [x/=y → x:=x/y]\n"
            + " - '+=' [x+=y → x:=x+y]\n"
            + " - '-=' [x-=y → x:=x-y]\n";

    private static void displayHelp(Terminal terminal) throws IOException {
        writeToTerminal(terminal, HELP_STRING);
    }

    private static void moveCursorX(Terminal terminal, int x) throws IOException {
        TerminalPosition cursorPos = terminal.getCursorPosition();
        terminal.setCursorPosition(Math.max(cursorPos.getColumn() + x, 2), cursorPos.getRow());
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

    private static void calculate(Terminal terminal, String input) throws IOException {
        long time = System.nanoTime();

        String result;
        try {
            result = new Calculator(input, -1).calculate().toString(true);
        } catch(Exception ignored) {
            String[] inputArr = input.split("\\s+");
            StringBuilder sb = new StringBuilder(inputArr.length);
            boolean pretty = true;
            int precision = -1;
            for (String s : inputArr) {
                String arg = s.toLowerCase();
                if (arg.startsWith("--")) {
                    if (arg.equals("--exponential") || arg.equals("--scientific")
                            || arg.equals("--e") || arg.equals("--s")) {
                        pretty = false;
                    } else if (arg.startsWith("--precision") || arg.startsWith("--p")) {
                        try {
                            precision = Math.min(69420, Integer.parseInt(arg.replaceAll("[^0-9]", "")));
                        } catch (NumberFormatException e) {
                            writeToTerminal(terminal, e.getMessage() + "\n", TextColor.ANSI.RED);
                            writeToTerminal(terminal, "> ", TextColor.ANSI.GREEN);
                        }
                    }
                } else {
                    sb.append(arg);
                }
            }

            input = sb.toString();
            result = handleEqualSigns(input, precision);

            if (result == null) {
                result = new Calculator(input, precision).calculate().toString(pretty);
                if (!pretty) {
                    result = result.replace("e", "×10^");
                }
            }

        }

        writeToTerminal(terminal, "→ " + result + "\n", TextColor.ANSI.GREEN_BRIGHT, SGR.BOLD);
        writeToTerminal(terminal, String.format("Calculated in %fms\n", (System.nanoTime() - time) / 1_000_000.0), TextColor.ANSI.BLUE_BRIGHT);
    }

    private static String handleEqualSigns(String input, int precision) {
        return handleEqualSigns(input, precision, 1);
    }

    private static String handleEqualSigns(String input, int precision, int historyIndex) {
        String[] varStrings = input.split("[:*/+-]=");
        if (varStrings.length > 1) {
            if (varStrings.length > 2) {
                return "false";
            }
            String name = varStrings[0].trim();
            Apfloat result = new Calculator(varStrings[1], precision).setHistoryIndex(historyIndex).calculate();
            int p = precision == -1 ? 1000 : precision;
            if (input.contains(":=")) {
                variables.put(name, result.toString(true));
            } else if (input.contains("+=")) {
                variables.put(name, new Apfloat(variables.getOrDefault(name, "0"), p).add(result).toString(true));
            } else if (input.contains("-=")) {
                variables.put(name, new Apfloat(variables.getOrDefault(name, "0"), p).subtract(result).toString(true));
            } else if (input.contains("*=")) {
                variables.put(name, new Apfloat(variables.getOrDefault(name, "1"), p).multiply(result).toString(true));
            } else {
                variables.put(name, new Apfloat(variables.getOrDefault(name, "1"), p).divide(result).toString(true));
            }
            return variables.get(name);
        }

        String[] signs = new String[]{"<=", ">=", "=" , "<", ">"}; //first check the longer
        for (String sign : signs) {
            String result = handleSign(input, precision, historyIndex, sign);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private static String handleSign(String input, int precision, int historyIndex, String sign) {
        if (input.contains(sign)) {
            String[] strings = input.split(sign);
            Apfloat[] results = new Apfloat[strings.length];

            boolean containsNull = false;
            for (int i = 0; i < strings.length; i++) {
                try {
                    results[i] = new Calculator(strings[i], precision).setHistoryIndex(historyIndex).calculate();
                } catch(Exception ignored) {
                    results[i] = null;
                    containsNull = true;
                };
            }

            if (containsNull) {
                return "false";
            } else {
                Function<Integer, Boolean> fun = getCompareFunctionBySign(sign);
                for (int i = 0; i < results.length - 1; i++) {
                    int comparator = results[i].compareTo(results[i + 1]);
                    if (!fun.apply(comparator)) {
                        return "false";
                    }

                }
                return "true";
            }
        }
        return null;
    }

    private static Function<Integer, Boolean> getCompareFunctionBySign(String sign) {
        switch (sign) {
            case "=": {
                return (i) -> i == 0;
            }
            case "<": {
                return (i) -> i < 0;
            }
            case ">": {
                return (i) -> i > 0;
            }
            case ">=": {
                return (i) -> i >= 0;
            }
            case "<=": {
                return (i) -> i <= 0;
            }
        }
        System.err.println("Unknown operator: " + sign);
        return (i) -> false;
    }
}