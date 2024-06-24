/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2024 Neil C Smith.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * version 3 for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 * 
 * 
 * Please visit https://www.praxislive.org if you need additional information or
 * have any questions.
 */
package org.praxislive.project;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.praxislive.core.Value;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PResource;

import static org.praxislive.core.syntax.Token.Type.EOL;
import static org.praxislive.core.syntax.Token.Type.PLAIN;
import static org.praxislive.project.ProjectModel.BUILD_LEVEL_SWITCH;
import static org.praxislive.project.ProjectModel.INCLUDE_CMD;
import static org.praxislive.project.ProjectModel.RUN_LEVEL_SWITCH;

/**
 *
 */
class ProjectParser {

    private static enum ExecutionLevel {
        SETUP, BUILD, RUN
    };

    private final String script;
    private final URI context;

    private ExecutionLevel level;

    private ProjectParser(String script, URI context) {
        this.script = script;
        this.context = context;
    }

    private ProjectModel doParse() throws ParseException {
        try {
            level = ExecutionLevel.SETUP;
            Iterator<Token> tokens = new Tokenizer(script).iterator();
            List<Token> line = new ArrayList<>();
            ProjectModel.Builder builder = ProjectModel.builder();
            if (context != null) {
                builder.context(context);
            }

            while (tokens.hasNext()) {
                line.clear();
                tokensToEOL(tokens, line);
                if (line.isEmpty()) {
                    continue;
                }
                Token first = line.getFirst();
                switch (first.getType()) {
                    case PLAIN ->
                        parseCommand(builder, line);
                    case COMMENT ->
                        parseComment(line);
                    default ->
                        throw new ParseException("Unexpected token type");
                }
            }
            return builder.build();
        } catch (ParseException pex) {
            throw pex;
        } catch (Exception ex) {
            throw new ParseException(ex);
        }
    }

    private void addElement(ProjectModel.Builder builder, ProjectElement element) {
        switch (level) {
            case SETUP ->
                builder.setupElement(element);
            case BUILD ->
                builder.buildElement(element);
            case RUN ->
                builder.runElement(element);
        }
    }

    private void parseCommand(ProjectModel.Builder builder, List<Token> tokens)
            throws ParseException {
        String command = tokens.get(0).getText();
        if (INCLUDE_CMD.equals(command)) {
            parseInclude(builder, tokens);
        } else {
            String line = script.substring(tokens.get(0).getStartIndex(),
                    tokens.get(tokens.size() - 1).getEndIndex());
            addElement(builder, ProjectElement.line(line));
        }
    }

    private void parseInclude(ProjectModel.Builder builder, List<Token> tokens) throws ParseException {
        if (tokens.size() != 2) {
            throw new ParseException("Unexpected number of arguments in include command");
        }
        Value resource;
        if (context != null) {
            resource = SyntaxUtils.valueFromToken(context, tokens.get(1));
        } else {
            resource = SyntaxUtils.valueFromToken(tokens.get(1));
        }
        addElement(builder, ProjectElement.file(
                PResource.from(resource)
                        .map(PResource::value)
                        .orElseThrow(() -> new ParseException("Include is not a valid resource"))
        ));
    }

    private void parseComment(List<Token> tokens) throws ParseException {
        String text = tokens.get(0).getText();
        if (text.contains(BUILD_LEVEL_SWITCH)) {
            switchLevel(ExecutionLevel.BUILD);
        } else if (text.contains(RUN_LEVEL_SWITCH)) {
            switchLevel(ExecutionLevel.RUN);
        }
    }

    private void switchLevel(ExecutionLevel level) throws ParseException {
        if (level.compareTo(this.level) < 0) {
            throw new ParseException("Can't move level down");
        }
        this.level = level;
    }

    private static void tokensToEOL(Iterator<Token> tokens, List<Token> line) {
        while (tokens.hasNext()) {
            Token t = tokens.next();
            if (t.getType() == EOL) {
                break;
            }
            line.add(t);
        }
    }

    static ProjectModel parse(String script) throws ParseException {
        return new ProjectParser(Objects.requireNonNull(script), null).doParse();
    }

    static ProjectModel parse(URI context, String script) throws ParseException {
        return new ProjectParser(Objects.requireNonNull(script),
                Objects.requireNonNull(context)).doParse();
    }

}
