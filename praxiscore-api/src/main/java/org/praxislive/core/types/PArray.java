/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2022 Neil C Smith.
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
package org.praxislive.core.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;
import org.praxislive.core.Value;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;

/**
 * An ordered list of Values.
 */
public final class PArray extends Value implements Iterable<Value> {

    /**
     * An empty PArray.
     */
    public final static PArray EMPTY = new PArray(List.of(), "");

    private final List<Value> data;
    private volatile String str;

    private PArray(List<Value> data) {
        this(data, null);
    }

    private PArray(List<Value> data, String str) {
        this.data = List.copyOf(data);
        this.str = str;
    }

    /**
     * Query the value at the given index in the list. If the index is negative
     * or greater than size, the index is modulated into range rather than
     * throwing an exception - useful for cycling. If this PArray is empty, this
     * is returned.
     *
     * @param index position of value
     * @return value at index
     */
    public Value get(int index) {
        int count = data.size();
        if (count > 0) {
            index %= count;
            return index < 0 ? data.get(index + count) : data.get(index);
        } else {
            return this;
        }
    }

    /**
     * Query the number of values in the list.
     *
     * @return size of list
     */
    public int size() {
        return data.size();
    }

    @Override
    public String toString() {
        if (str == null) {
            if (!data.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Value entry : data) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    if (entry instanceof PArray || entry instanceof PMap) {
                        sb.append('{')
                                .append(entry.toString())
                                .append('}');
                    } else {
                        sb.append(Utils.escape(String.valueOf(entry)));
                    }
                }
                str = sb.toString();
            } else {
                str = "";
            }
        }

        return str;

    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean equivalent(Value arg) {
        try {
            if (arg == this) {
                return true;
            }
            PArray other = PArray.coerce(arg);
            int size = data.size();
            if (size != other.data.size()) {
                return false;
            }
            for (int i = 0; i < size; i++) {
                if (!Utils.equivalent(data.get(i), other.data.get(i))) {
                    return false;
                }
            }
            return true;
        } catch (ValueFormatException ex) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PArray) {
            PArray o = (PArray) obj;
            return data.equals(o.data);
        }
        return false;
    }

    @Override
    public Iterator<Value> iterator() {
        return data.iterator();
    }

    /**
     * An ordered stream over the list of values.
     *
     * @return stream of values
     */
    public Stream<Value> stream() {
        return data.stream();
    }

    /**
     * An unmodifiable {@link List} view of this list of values.
     *
     * @return view as unmodifiable list
     */
    public List<Value> asList() {
        return data;
    }

    /**
     * Create a PArray from the given collection of values.
     *
     * @param collection collection of values
     * @return new PArray
     */
    public static PArray of(Collection<? extends Value> collection) {
        return new PArray(List.copyOf(collection));
    }

    /**
     * Create a PArray from the given collection of values.
     *
     * @param values array of values
     * @return new PArray
     */
    public static PArray of(Value... values) {
        return new PArray(List.of(values));
    }

    /**
     * Parse the given text into a PArray.
     *
     * @param text text to parse
     * @return parsed PArray
     * @throws ValueFormatException
     */
    public static PArray parse(String text) throws ValueFormatException {
        if (text.length() == 0) {
            return PArray.EMPTY;
        }
        try {
            List<Token> tk = Tokenizer.parse(text);
            List<Value> list = new ArrayList<>();
            for (Token t : tk) {
                Token.Type type = t.getType();
                switch (type) {
                    case PLAIN:
                    case QUOTED:
                        list.add(PString.of(t.getText()));
                        break;
                    case BRACED:
                        String s = t.getText();
                        list.add(PString.of(s));
                        break;
                    case COMMENT:
                    case EOL:
                        continue;
                    default:
                        throw new ValueFormatException();
                }
            }
            int size = list.size();
            if (size == 0) {
                return PArray.EMPTY;
            } else {
                return new PArray(list, text);
            }
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }

    }

    private static PArray coerce(Value arg) throws ValueFormatException {
        if (arg instanceof PArray) {
            return (PArray) arg;
        } else {
            return parse(arg.toString());
        }
    }

    /**
     * Cast or convert the provided value into a PArray, wrapped in an Optional.
     * If the value is already a PArray, the Optional will wrap the existing
     * value. If the value is not a PArray and cannot be converted into one, an
     * empty Optional is returned.
     *
     * @param value value
     * @return optional PArray
     */
    public static Optional<PArray> from(Value value) {
        try {
            return Optional.of(coerce(value));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * Utility method to create an {@link ArgumentInfo} for arguments of type
     * PArray.
     *
     * @return argument info
     */
    public static ArgumentInfo info() {
        return ArgumentInfo.of(PArray.class, null);
    }

    /**
     * Create a {@link Collector} that can create a PArray from a Stream of
     * Values.
     *
     * @param <T> Value type
     * @return new PArray collector
     */
    public static <T extends Value> Collector<T, ?, PArray> collector() {

        return Collector.<T, List<T>, PArray>of(ArrayList::new,
                List::add,
                (list1, list2) -> {
                    list1.addAll(list2);
                    return list1;
                },
                PArray::of
        );
    }

}
