/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package org.puffinbasic;

import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.junit.jupiter.api.Test;
import vavi.util.Debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Test1.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2022/03/03 umjammer initial version <br>
 */
class Test1 {

    @Test
    public void test0() throws Exception {
        ScriptEngineManager sem = new ScriptEngineManager();
        List<ScriptEngineFactory> list = sem.getEngineFactories();

        for (int i = 0; i < list.size(); i++) {
            ScriptEngineFactory f = list.get(i);

            String engineName = f.getEngineName();
            String engineVersion = f.getEngineVersion();
            String langName = f.getLanguageName();
            String langVersion = f.getLanguageVersion();
            System.out.println("\n---- " + i + " ----\n" + engineName + " " +
                    engineVersion + " (" +
                    langName + " " +
                    langVersion + ")");
        }

        assertTrue(list.stream().anyMatch(f -> f.getEngineName().equals("Puffin BASIC")));
    }

    @Test
    void testJsr223() throws Exception {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine engine = sem.getEngineByName("PuffinBasic");
Debug.println("engine: " + engine);

        String statement =
                "100 FOR i = 1 to 10\n" +
                "110 FOR j = i to 10\n" +
                "120 PRINT \"*\";\n" +
                "130 NEXT j\n" +
                "140 NEXT i\n"; // TODO needs last lf
        Object result = engine.eval(statement);
Debug.println("result: " + result);

        statement = "100 LET A = 1 + 100\n"; // TODO needs last lf
        result = engine.eval(statement);
        assertEquals(101.0, result);
Debug.println("result: " + result);

        statement = "100 LET A% = 1 + 100\n"; // TODO needs last lf
        result = engine.eval(statement);
        assertEquals(101, result);
Debug.println("result: " + result);
    }
}

/* */
