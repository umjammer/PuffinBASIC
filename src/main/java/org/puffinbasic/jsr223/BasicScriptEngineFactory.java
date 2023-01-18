/*
 * Copyright (c) 2023 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package org.puffinbasic.jsr223;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.script.ScriptEngineFactory;


/**
 * BasicScriptEngineFactory.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 220118 nsano make the initial version <br>
 */
public class BasicScriptEngineFactory implements ScriptEngineFactory {

    /** */
    private static final String FILEEXT = ".bas";

    /** */
    private static final String [] MIMETYPES = {
        "text/plain",
        "text/x-basic",
        "application/x-basic"
    };

    /** */
    private static final String [] NAMES = {
        "PuffinBasic",
        "basic"
    };

    /** */
    private BasicScriptEngine scriptEngine;
    /** */
    private List<String> extensions;
    /** */
    private List<String> mimeTypes;
    /** */
    private List<String> names;

    /** */
    public BasicScriptEngineFactory() {
        scriptEngine = new BasicScriptEngine(this);
        extensions = Collections.nCopies(1, FILEEXT);
        mimeTypes = Arrays.asList(MIMETYPES);
        names = Arrays.asList(NAMES);
    }

    @Override
    public String getEngineName() {
        return getScriptEngine().get(BasicScriptEngine.ENGINE).toString();
    }

    @Override
    public String getEngineVersion() {
        return getScriptEngine().get(BasicScriptEngine.ENGINE_VERSION).toString();
    }

    @Override
    public List<String> getExtensions() {
        return extensions;
    }

    @Override
    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    @Override
    public List<String> getNames() {
        return names;
    }

    @Override
    public String getLanguageName() {
        return getScriptEngine().get(BasicScriptEngine.LANGUAGE).toString();
    }

    @Override
    public String getLanguageVersion() {
        return getScriptEngine().get(BasicScriptEngine.LANGUAGE_VERSION).toString();
    }

    @Override
    public Object getParameter(String key) {
        return getScriptEngine().get(key).toString();
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args)  {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(obj).append(" ").append(m);
        int len = args.length;
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return "print \"" + toDisplay + "\"";
    }

    @Override
    public String getProgram(String ... statements) {
        StringBuilder sb = new StringBuilder();
        int len = statements.length;
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(statements[i]);
        }
        return sb.toString();
    }

    @Override
    public BasicScriptEngine getScriptEngine() {
        return scriptEngine;
    }
}

/* */
