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
package org.praxislive.core;

import java.util.List;
import java.util.Set;
import java.util.SequencedSet;

/**
 * A {@link Set} with consistent order of entries. All static factory methods
 * produce unmodifiable sets.
 *
 * @param <E> value type
 */
public sealed interface OrderedSet<E> extends SequencedSet<E> permits OrderedSetImpl {

    /**
     * A {@link List} containing all the values of this OrderedSet.
     * <p>
     * Because Set equality ignores order, code needing to verify whether an
     * OrderedSet contains the same values in the same order should also check
     * for equality of the values list.
     *
     * @return values as list
     */
    public List<E> values();

    @Override
    public OrderedSet<E> reversed();

    /**
     * Compares the provided object with this map for equality in accordance
     * with the specification of {@link Map#equals(java.lang.Object)}.
     * <p>
     * Because Map equality does not depends on order, code needing to verify
     * whether an OrderedMap contains the same mappings in the same order should
     * also check for equality of the {@link #keys()} list.
     *
     * @param obj object to compare
     * @return true if the object is a map containing the same mappings, not
     * necessarily in the same order
     */
    @Override
    public boolean equals(Object obj);

    /**
     * Returns an empty OrderedSet.
     *
     * @param <E> value type
     * @return empty OrderedSet
     */
    public static <E> OrderedSet<E> of() {
        return new OrderedSetImpl<>(Set.of(), List.of());
    }

    /**
     * Returns an OrderedSet with a single element.
     *
     * @param <E> element type
     * @param element element
     * @return an OrderedSet of the provided element
     * @throws NullPointerException if element is null
     */
    public static <E> OrderedSet<E> of(E element) {
        return new OrderedSetImpl<>(Set.of(element), List.of(element));
    }

    /**
     * Returns an OrderedSet with two elements.
     *
     * @param <E> element type
     * @param e1 first element
     * @param e2 second element
     * @return an OrderedSet of the provided elements
     * @throws NullPointerException if any elements are null
     * @throws IllegalArgumentException if any elements are duplicated
     */
    public static <E> OrderedSet<E> of(E e1, E e2) {
        return new OrderedSetImpl<>(Set.of(e1, e2), List.of(e1, e2));
    }

    /**
     * Returns an OrderedSet with three elements.
     *
     * @param <E> element type
     * @param e1 first element
     * @param e2 second element
     * @param e3 third element
     * @return an OrderedSet of the provided elements
     * @throws NullPointerException if any elements are null
     * @throws IllegalArgumentException if any elements are duplicated
     */
    public static <E> OrderedSet<E> of(E e1, E e2, E e3) {
        return new OrderedSetImpl<>(Set.of(e1, e2, e3), List.of(e1, e2, e3));
    }

    /**
     * Returns an OrderedSet with four elements.
     *
     * @param <E> element type
     * @param e1 first element
     * @param e2 second element
     * @param e3 third element
     * @param e4 fourth element
     * @return an OrderedSet of the provided elements
     * @throws NullPointerException if any elements are null
     * @throws IllegalArgumentException if any elements are duplicated
     */
    public static <E> OrderedSet<E> of(E e1, E e2, E e3, E e4) {
        return new OrderedSetImpl<>(Set.of(e1, e2, e3, e4), List.of(e1, e2, e3, e4));
    }

    /**
     * Returns an OrderedSet with five elements.
     *
     * @param <E> element type
     * @param e1 first element
     * @param e2 second element
     * @param e3 third element
     * @param e4 fourth element
     * @param e5 fifth element
     * @return an OrderedSet of the provided elements
     * @throws NullPointerException if any elements are null
     * @throws IllegalArgumentException if any elements are duplicated
     */
    public static <E> OrderedSet<E> of(E e1, E e2, E e3, E e4, E e5) {
        return new OrderedSetImpl<>(Set.of(e1, e2, e3, e4, e5), List.of(e1, e2, e3, e4, e5));
    }

    /**
     * Returns an OrderedSet of the provided elements.
     *
     * @param <E> element type
     * @param elements elements
     * @return an OrderedSet of the provided elements
     * @throws c
     * @throws IllegalArgumentException if any elements are duplicated
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> OrderedSet<E> of(E... elements) {
        return new OrderedSetImpl<>(Set.of(elements), List.of(elements));
    }

    /**
     * Returns an unmodifiable OrderedSet that contains a copy of the provided
     * Set. The order will be the same as the iteration order of the set.
     * <p>
     * If the set is already an unmodifiable OrderedSet it may be returned as
     * is.
     *
     * @param <E> element type
     * @param set set to copy
     * @return an ordered set copy of the provided set
     * @throws NullPointerException if any element is null or the set is null
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <E> OrderedSet<E> copyOf(Set<? extends E> set) {
        if (set instanceof OrderedSetImpl) {
            return (OrderedSet<E>) set;
        } else {
            return (OrderedSet<E>) OrderedSet.of(set.toArray());
        }
    }
}
