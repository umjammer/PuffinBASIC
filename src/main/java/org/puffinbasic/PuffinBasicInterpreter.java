package org.puffinbasic;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.puffinbasic.antlr4.PuffinBasicLexer;
import org.puffinbasic.antlr4.PuffinBasicParser;
import org.puffinbasic.domain.PuffinBasicSymbolTable;
import org.puffinbasic.domain.STObjects;
import org.puffinbasic.error.PuffinBasicRuntimeError;
import org.puffinbasic.error.PuffinBasicSyntaxError;
import org.puffinbasic.parser.LinenumberListener;
import org.puffinbasic.parser.LinenumberListener.ThrowOnDuplicate;
import org.puffinbasic.parser.PuffinBasicIR;
import org.puffinbasic.parser.PuffinBasicIRListener;
import org.puffinbasic.parser.PuffinBasicImportPath;
import org.puffinbasic.parser.PuffinBasicSourceFile;
import org.puffinbasic.runtime.Environment;
import org.puffinbasic.runtime.Environment.SystemEnv;
import org.puffinbasic.runtime.PuffinBasicRuntime;

import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.IMPORT_ERROR;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.IO_ERROR;
import static org.puffinbasic.parser.LinenumberListener.ThrowOnDuplicate.LOG;
import static org.puffinbasic.parser.LinenumberListener.ThrowOnDuplicate.THROW;

public final class PuffinBasicInterpreter {

    private static final String UNKNOWN_SOURCE_FILE = "<UNKNOWN>";

    private enum SourceFileMode {
        MAIN,
        LIB
    }

    public static void main(String... args) {
        PuffinBasicInterpreter app = new PuffinBasicInterpreter();

        UserOptions userOptions = app.parseCommandLineArgs(args);

        String mainSource = userOptions.filename;

        Instant t0 = Instant.now();
        String sourceCode = app.loadSource(mainSource);
        app.logTimeTaken("LOAD", t0, userOptions.timing);

        app.interpretAndRun(userOptions, mainSource, sourceCode, System.out, new SystemEnv());
    }

    private UserOptions parseCommandLineArgs(String... args) {
        ArgumentParser parser = ArgumentParsers
                .newFor("PuffinBasic")
                .build();
        parser.addArgument("-d", "--logduplicate")
                .help("Log error on duplicate")
                .action(Arguments.storeTrue());
        parser.addArgument("-l", "--list")
                .help("Print Sorted Source Code")
                .action(Arguments.storeTrue());
        parser.addArgument("-i", "--ir")
                .help("Print IR")
                .action(Arguments.storeTrue());
        parser.addArgument("-t", "--timing")
                .help("Print timing")
                .action(Arguments.storeTrue());
        parser.addArgument("-g", "--graphics")
                .help("Enable graphics")
                .action(Arguments.storeTrue());
        parser.addArgument("file").nargs(1);
        Namespace res = null;
        try {
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        if (res == null) {
            throw new IllegalStateException();
        }

        return new UserOptions(
                res.getBoolean("logduplicate"),
                res.getBoolean("list"),
                res.getBoolean("ir"),
                res.getBoolean("timing"),
                res.getBoolean("graphics"),
                (String) res.getList("file").get(0)
        );
    }

    private String loadSource(String filename) {
        StringBuilder sb = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(filename))) {
            stream.forEach(s -> sb.append(s).append(System.lineSeparator()));
        } catch (IOException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to read source code: " + filename + ", error: " + e.getMessage()
            );
        }
        return sb.toString();
    }

    public Object interpretAndRun(
            UserOptions userOptions,
            String sourceCode,
            PrintStream out,
            Environment env)
    {
        return interpretAndRun(userOptions, UNKNOWN_SOURCE_FILE, sourceCode, out, env);
    }

    public Object interpretAndRun(
            UserOptions userOptions,
            String sourceFilename,
            String sourceCode,
            PrintStream out,
            Environment env)
    {
        PuffinBasicImportPath importPath = new PuffinBasicImportPath(sourceFilename);

        Instant t1 = Instant.now();
        PuffinBasicSourceFile sourceFile = syntaxCheckAndSortByLineNumber(
                importPath,
                sourceFilename,
                sourceCode,
                userOptions.logOnDuplicate ? LOG : THROW,
                SourceFileMode.MAIN);
        if (sourceFile.getSourceCode().isEmpty()) {
            throw new PuffinBasicSyntaxError(
                    "Failed to parse source code! Check if a linenumber is missing");
        }
        logTimeTaken("SORT", t1, userOptions.timing);

        log("LIST", userOptions.listSourceCode);
        log(sourceFile.getSourceCode(), userOptions.listSourceCode);

        Instant t2 = Instant.now();
        PuffinBasicIR ir = generateIR(sourceFile, userOptions.graphics);
        logTimeTaken("IR", t2, userOptions.timing);
        log("IR", userOptions.printIR);
        if (userOptions.printIR) {
            int i = 0;
            for (PuffinBasicIR.Instruction instruction : ir.getInstructions()) {
                log(i++ + ": " + instruction, true);
            }
        }

        log("RUN", userOptions.timing);
        Instant t3 = Instant.now();
        Object result = run(ir, out, env);
        logTimeTaken("RUN", t3, userOptions.timing);
        return result;
    }

    private static void log(String s, boolean log) {
        if (log) {
            System.out.println(s);
        }
    }

    private static void logTimeTaken(String tag, Instant t1, boolean log) {
        Duration duration  = Duration.between(t1, Instant.now());
        double timeSec = duration.getSeconds() + duration.getNano() / 1000_000_000.0;
        log("[" + tag + "] time taken = " + timeSec + " s", log);
    }

    private static Object run(PuffinBasicIR ir, PrintStream out, Environment env) {
        PuffinBasicRuntime runtime = new PuffinBasicRuntime(ir, out, env);
        STObjects.STEntry entry = runtime.run();
        // TODO complete
        switch (entry.getType().getTypeId()) {
        case SCALAR:
            switch (entry.getType().getAtomTypeId()) {
            case INT32:
                return entry.getValue().getInt32();
            case DOUBLE:
                return entry.getValue().getFloat64();
            case STRING:
                return entry.getValue().getString();
            default:
                return null;
            }
        case ARRAY:
        default:
            return null;
        }
    }

    private static PuffinBasicIR generateIR(PuffinBasicSourceFile sourceFile, boolean graphics) {
        PuffinBasicSymbolTable symbolTable = new PuffinBasicSymbolTable();
        PuffinBasicIR ir = new PuffinBasicIR(symbolTable);
        for (PuffinBasicSourceFile importFile : sourceFile.getImportFiles()) {
            generateIR(importFile, ir, graphics);
        }
        generateIR(sourceFile, ir, graphics);
        return ir;
    }

    private static void generateIR(PuffinBasicSourceFile sourceFile, PuffinBasicIR ir, boolean graphics) {
        CharStream in = sourceFile.getSourceCodeStream();
        PuffinBasicLexer lexer = new PuffinBasicLexer(in);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PuffinBasicParser parser = new PuffinBasicParser(tokens);
        PuffinBasicParser.ProgContext tree = parser.prog();
        ParseTreeWalker walker = new ParseTreeWalker();
        PuffinBasicIRListener irListener = new PuffinBasicIRListener(sourceFile, in, ir, graphics);
        walker.walk(irListener, tree);
        irListener.semanticCheckAfterParsing();
    }

    private PuffinBasicSourceFile syntaxCheckAndSortByLineNumber(
            PuffinBasicImportPath importPath,
            String sourceFile,
            String input,
            ThrowOnDuplicate throwOnDuplicate,
            SourceFileMode sourceFileMode)
    {
        CharStream in = CharStreams.fromString(input);
        ThrowingErrorListener syntaxErrorListener = new ThrowingErrorListener(input);
        PuffinBasicLexer lexer = new PuffinBasicLexer(in);
        lexer.removeErrorListeners();
        lexer.addErrorListener(syntaxErrorListener);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PuffinBasicParser parser = new PuffinBasicParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(syntaxErrorListener);
        PuffinBasicParser.ProgContext tree = parser.prog();
        ParseTreeWalker walker = new ParseTreeWalker();
        LinenumberListener linenumListener = new LinenumberListener(in, throwOnDuplicate);
        walker.walk(linenumListener, tree);

        if (sourceFileMode == SourceFileMode.LIB) {
            if (linenumListener.hasLineNumbers()) {
                throw new PuffinBasicRuntimeError(
                        IMPORT_ERROR,
                        "Lib " + sourceFile + " should not have line numbers!"
                );
            }
            if (linenumListener.getLibtag() == null) {
                throw new PuffinBasicRuntimeError(
                        IMPORT_ERROR,
                        "Lib " + sourceFile + " should set a LIBTAG!"
                );
            }
        }

        LinkedHashSet<PuffinBasicSourceFile> importSourceFiles = new LinkedHashSet<>();
        for (String importFilename : linenumListener.getImportFiles()) {
            String importedInput = loadSource(importPath.find(importFilename));
            PuffinBasicSourceFile importSourceFile = syntaxCheckAndSortByLineNumber(
                    importPath, importFilename, importedInput,
                    throwOnDuplicate, SourceFileMode.LIB);
            importSourceFiles.add(importSourceFile);
            importSourceFiles.addAll(importSourceFile.getImportFiles());
        }

        String sortedCode = linenumListener.getSortedCode();
        return new PuffinBasicSourceFile(
                sourceFile,
                linenumListener.getLibtag(),
                sortedCode,
                CharStreams.fromString(sortedCode),
                importSourceFiles);
    }

    private static final class ThrowingErrorListener extends BaseErrorListener {

        private final String input;

        ThrowingErrorListener(String input) {
            this.input = input;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e)
        {
            int lineIndex = line - 1;
            String[] lines = input.split(System.lineSeparator());
            String inputLine;
            if (lineIndex >= 0 && lineIndex < lines.length) {
                inputLine = lines[lineIndex];
                if (charPositionInLine >= 0 && charPositionInLine <= inputLine.length()) {
                    inputLine = inputLine + System.lineSeparator()
                            + Strings.repeat(" ", Math.max(0, charPositionInLine)) + '^';
                }
            } else {
                inputLine = "<LINE OUT OF RANGE>";
            }
            throw new PuffinBasicSyntaxError(
                    "[" + line + ":" + charPositionInLine + "] " + msg + System.lineSeparator()
                    + inputLine
            );
        }
    }

    public static final class UserOptions {

        static UserOptions ofTest() {
            return new UserOptions(
                    false, false, false, false, false, null
            );
        }

        public static UserOptions ofScript() {
            return new UserOptions(
                    false, false, false, false, true, null
            );
        }

        final boolean logOnDuplicate;
        final boolean listSourceCode;
        final boolean printIR;
        final boolean timing;
        final boolean graphics;
        public final String filename;

        UserOptions(
                boolean logOnDuplicate,
                boolean listSourceCode,
                boolean printIR,
                boolean timing,
                boolean graphics,
                String filename)
        {
            this.logOnDuplicate = logOnDuplicate;
            this.listSourceCode = listSourceCode;
            this.printIR = printIR;
            this.timing = timing;
            this.graphics = graphics;
            this.filename = filename;
        }
    }
}
