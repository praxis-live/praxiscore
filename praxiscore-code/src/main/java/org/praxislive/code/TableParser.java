/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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
package org.praxislive.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.praxislive.code.userapi.Table;
import org.praxislive.core.Value;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
/**
 *
 */
class TableParser {

    private TableParser() {
    }
    
    static class Response {
        
        private final List<Table> tables;
        
        Response(List<Table> tables) {
            this.tables = tables;
        }
        
        List<Table> tables() {
            return tables;
        }
        
    }

    static Response parse(String data) throws ValueFormatException {
        if (data.isEmpty()) {
            return new Response(List.of());
        }
        return new Response(parseImpl(data));

    }
    
    private static List<Table> parseImpl(String data) throws ValueFormatException {
        List<TableImpl> tables = new ArrayList<>();
        List<Optional<Value>> table = new ArrayList<>();
        int maxColumn = 0;
        int column = 0;
        Tokenizer tk = new Tokenizer(data);
        for (Token t : tk) {
            Token.Type type = t.getType();
            switch (type) {
                case PLAIN:
                case QUOTED:
                case BRACED:
                    if (maxColumn > 0 && column >= maxColumn) {
                        throw new ValueFormatException();
                    }
                    if (type == Token.Type.PLAIN) {
                        table.add(getPlainArgument(t.getText()));
                    } else {
                        table.add(getQuotedArgument(t.getText()));
                    }
                    column++;
                    break;
                case EOL:
                    if (column > 0) {
                        // inside pattern
                        if (maxColumn == 0) {
                            maxColumn = column;
                        } else {
                            // pad to end of row
                            while (column < maxColumn) {
                                table.add(Optional.empty());
                                column++;
                            }
                        }
                        column = 0;
                    } else if (maxColumn > 0) {
                        // end of pattern
                        int columns = maxColumn;
                        int size = table.size();
                        int rows = size / columns;
                        tables.add(new TableImpl(List.copyOf(table), rows, columns));
                        column = 0;
                        maxColumn = 0;
                        table.clear();
                    }
                    break;

                case COMMENT:
                    break;
                default:
                    throw new ValueFormatException();
            }
        }
        if (maxColumn > 0) {
            // still in pattern
            int columns = maxColumn;
            int size = table.size();
            int rows = size / columns;
            tables.add(new TableImpl(List.copyOf(table), rows, columns));
        }

        return List.copyOf(tables);
    }

    private static Optional<Value> getPlainArgument(String token) {
        if (".".equals(token)) {
            return Optional.empty();
        } else if (token.isEmpty()) {
            return Optional.of(PString.EMPTY);
        } else if ("0123456789-.".indexOf(token.charAt(0)) > -1) {
            try {
                return Optional.of(PNumber.parse(token));
            } catch (ValueFormatException ex) {
                // fall through
            }
        }
        return Optional.of(PString.of(token));
    }

    private static Optional<Value> getQuotedArgument(String token) {
        if (token.isEmpty()) {
            return Optional.of(PString.EMPTY);
        } else {
            return Optional.of(PString.of(token));
        }
    }

    static class TableImpl extends Table {

        private final List<Optional<Value>> values;
        private final int rows;
        private final int columns;

        private TableImpl(List<Optional<Value>> values, int rows, int columns) {
            assert values.size() == rows * columns;
            this.values = values;
            this.rows = rows;
            this.columns = columns;
        }

        @Override
        public Optional<Value> valueAt(int row, int column) {
            if (row < 0 || row >= rows) {
                return Optional.empty();
            }
            if (column < 0 || column >= columns) {
                return Optional.empty();
            }
            return values.get((row * columns) + column);
        }

        @Override
        public int rowCount() {
            return rows;
        }

        @Override
        public int columnCount() {
            return columns;
        }

    }


}
