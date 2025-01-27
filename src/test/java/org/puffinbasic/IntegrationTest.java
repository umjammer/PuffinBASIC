package org.puffinbasic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.puffinbasic.PuffinBasicInterpreter.UserOptions;
import org.puffinbasic.error.PuffinBasicRuntimeError;
import org.puffinbasic.runtime.Environment;
import org.puffinbasic.runtime.Environment.SystemEnv;

import static org.junit.jupiter.api.Assertions.*;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.IO_ERROR;

public class IntegrationTest {

    private Environment env;
    private PuffinBasicInterpreter interpreter;

    @BeforeEach
    public void setup() {
        interpreter = new PuffinBasicInterpreter();
        env = new SystemEnv();
    }

    @Test
    public void testForLoop() {
        runTest("forloop.bas", "forloop.bas.output");
    }

    @Test
    public void testNestedForLoop() {
        runTest("nested_forloop.bas", "nested_forloop.bas.output");
    }

    @Test
    public void testScalarVariable() {
        runTest("scalar_var.bas", "scalar_var.bas.output");
    }

    @Test
    public void testArrayVariable() {
        runTest("array_var.bas", "array_var.bas.output");
    }

    @Test
    public void testArrayCopy() {
        runTest("array_copy.bas", "array_copy.bas.output");
    }

    @Test
    public void testArrayFunc() {
        runTest("array_func.bas", "array_func.bas.output");
    }

    @Test
    public void testWhile() {
        runTest("while.bas", "while.bas.output");
    }

    @Test
    public void testExpr() {
        runTest("expr.bas", "expr.bas.output");
    }

    @Test
    public void testFunc() {
        runTest("func.bas", "func.bas.output");
    }

    @Test
    public void testFunc2() {
        runTest("func2.bas", "func2.bas.output");
    }

    @Test
    public void testIf() {
        runTest("if.bas", "if.bas.output");
    }

    @Test
    public void testIfThenBegin() {
        runTest("ifthenbegin.bas", "ifthenbegin.bas.output");
    }

    @Test
    public void testReadData() {
        runTest("readdata.bas", "readdata.bas.output");
    }

    @Test
    public void testGosub() {
        runTest("gosub.bas", "gosub.bas.output");
    }

    @Test
    public void testGosublabel() {
        runTest("gosublabel.bas", "gosublabel.bas.output");
    }

    @Test
    public void testGotolabel() {
        runTest("gotolabel.bas", "gotolabel.bas.output");
    }

    @Test
    public void testDef() {
        runTest("def.bas", "def.bas.output");
    }

    @Test
    public void testUdf() {
        runTest("udf.bas", "udf.bas.output");
    }

    @Test
    public void testStrStmt() {
        runTest("strstmt.bas", "strstmt.bas.output");
    }

    @Test
    public void testPrintUsing() {
        runTest("printusing.bas", "printusing.bas.output");
    }

    @Test
    public void testWrite() {
        runTest("write.bas", "write.bas.output");
    }

    @Test
    public void testSwap() {
        runTest("swap.bas", "swap.bas.output");
    }

    @Test
    public void testRef() {
        runTest("ref.bas", "ref.bas.output");
    }

    @Test
    public void testRandomAccessFile() throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        String filename = "puffin_basic_test_random_access_file_"
                + Instant.now().getEpochSecond() + ".data";
        env.set("TEST_TMP_DIR", tmpdir);
        env.set("TEST_FILENAME", filename);
        runTest("randomaccessfile.bas", "randomaccessfile.bas.output");
        Files.delete(Paths.get(tmpdir, filename));
    }

    @Test
    public void testSequentialAccessFile() throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        String filename = "puffin_basic_test_sequential_access_file_"
                + Instant.now().getEpochSecond() + ".data";
        env.set("TEST_TMP_DIR", tmpdir);
        env.set("TEST_SEQ_FILENAME", filename);
        runTest("sequentialaccessfile.bas", "sequentialaccessfile.bas.output");
        Files.delete(Paths.get(tmpdir, filename));
    }

    @Test
    public void testStruct() {
        runTest("struct.bas", "struct.bas.output");
    }

    @Test
    public void testList() {
        runTest("list.bas", "list.bas.output");
    }

    @Test
    public void testSet() {
        runTest("set.bas", "set.bas.output");
    }

    @Test
    public void testDict() {
        runTest("dict.bas", "dict.bas.output");
    }

    private void runTest(String source, String output) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos);
        interpreter.interpretAndRun(
                UserOptions.ofTest(),
                loadSourceCodeFromResource(source),
                out,
                env);
        out.close();

        assertEquals(
                loadOutputFromResource(output),
                bos.toString()
        );
    }

    private String loadSourceCodeFromResource(String filename) {
        return loadResource(getClass().getClassLoader().getResource(filename));
    }

    private String loadOutputFromResource(String filename) {
        return loadResource(getClass().getClassLoader().getResource(filename));
    }

    private static String loadResource(URL resource) {
        try {
            return new String(Files.readAllBytes(Paths.get(resource.toURI())));
        } catch (IOException | URISyntaxException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to read file: " + resource + ", error: " + e.getMessage()
            );
        }
    }
}
