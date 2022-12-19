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
package org.praxislive.code;

import java.util.concurrent.ThreadLocalRandom;
import org.praxislive.code.userapi.Property;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 * Default delegate API for use as trait by delegate subclasses.
 */
public interface DefaultDelegateAPI {

    /**
     * Casting function to convert an object into a double value. If the value
     * is a {@link PNumber} the value will be extracted directly. If the value
     * is a {@link Property} the value will be extracted using
     * {@link Property#getDouble()}. All other types will be converted into a
     * Value (if necessary) and an attempt made to coerce into a PNumber.
     * Otherwise zero is returned.
     *
     * @param value object value to convert
     * @return value as double or zero
     */
    public default double D(Object value) {
        return D(value, 0);
    }

    /**
     * Casting function to convert an object into a double value. If the value
     * is a {@link PNumber} the value will be extracted directly. If the value
     * is a {@link Property} the value will be extracted using
     * {@link Property#getDouble(double)}. All other types will be converted
     * into a Value (if necessary) and an attempt made to coerce into a PNumber.
     * Otherwise the provided default is returned.
     *
     * @param value object value to convert
     * @param def default value
     * @return value as double or default value
     */
    public default double D(Object value, double def) {
        if (value instanceof PNumber) {
            return ((PNumber) value).value();
        } else if (value instanceof Property) {
            return ((Property) value).getDouble(def);
        } else {
            return PNumber.from(Value.ofObject(value))
                    .map(PNumber::value)
                    .orElse(def);
        }
    }

    /**
     * Casting function to convert an object into an int value. If the value is
     * a {@link PNumber} the value will be extracted directly. If the value is a
     * {@link Property} the value will be extracted using
     * {@link Property#getInt()}. All other types will be converted into a Value
     * (if necessary) and an attempt made to coerce into a PNumber. Otherwise
     * zero is returned.
     *
     * @param value object value to convert
     * @return value as int or zero
     */
    public default int I(Object value) {
        return I(value, 0);
    }

    /**
     * Casting function to convert an object into an int value. If the value is
     * a {@link PNumber} the value will be extracted directly. If the value is a
     * {@link Property} the value will be extracted using
     * {@link Property#getInt(int)}. All other types will be converted into a
     * Value (if necessary) and an attempt made to coerce into a PNumber.
     * Otherwise the provided default is returned.
     *
     * @param value object value to convert
     * @param def default value
     * @return value as int or default value
     */
    public default int I(Object value, int def) {
        if (value instanceof PNumber) {
            return ((PNumber) value).toIntValue();
        } else if (value instanceof Property) {
            return ((Property) value).getInt(def);
        } else {
            return PNumber.from(Value.ofObject(value))
                    .map(PNumber::toIntValue)
                    .orElse(def);
        }
    }

    /**
     * Casting function to convert an object into a boolean value. If the value
     * is a {@link PBoolean} the value will be extracted directly. If the value
     * is a {@link Property} the value will be extracted using
     * {@link Property#getBoolean()}. All other types will be converted into a
     * Value (if necessary) and an attempt made to coerce into a PBoolean.
     * Otherwise false is returned.
     *
     * @param value object value to convert
     * @return value as boolean or false
     */
    public default boolean B(Object value) {
        if (value instanceof PBoolean) {
            return ((PBoolean) value).value();
        } else if (value instanceof Property) {
            return ((Property) value).getBoolean();
        } else {
            return PBoolean.from(Value.ofObject(value))
                    .map(PBoolean::value)
                    .orElse(false);
        }
    }

    /**
     * Casting function to convert an object into a String value. If the value
     * is a {@link Property} the value is extracted and converted to a String.
     * Otherwise the value is converted to a String directly, which covers Value
     * and non-Value types. Null values are returned as an empty String.
     *
     * @param value object value to convert
     * @return value as String
     */
    public default String S(Object value) {
        if (value instanceof Property) {
            return ((Property) value).get().toString();
        } else {
            return value == null ? "" : value.toString();
        }
    }

    /**
     * Casting function to convert an object into an appropriate Value subtype.
     * If the input is already a Value it is returned directly. If the input is
     * a {@link Property} the value is extracted using {@link Property#get()}.
     * Otherwise the input is converted using
     * {@link Value#ofObject(java.lang.Object)}.
     *
     * @param value object value to convert
     * @return value as Value subtype
     */
    public default Value V(Object value) {
        if (value instanceof Value) {
            return (Value) value;
        } else if (value instanceof Property) {
            return ((Property) value).get();
        } else {
            return Value.ofObject(value);
        }
    }

    /**
     * Extract a double from the Property's current Value, or zero if the value
     * cannot be coerced.
     *
     * @param p
     * @return
     */
    @Deprecated
    public default double d(Property p) {
        return p.getDouble();
    }

    /**
     * Convert the provided Value into a double, or zero if the Value cannot be
     * coerced.
     *
     * @param v
     * @return
     */
    @Deprecated
    public default double d(Value v) {
        if (v instanceof PNumber) {
            return ((PNumber) v).value();
        } else {
            return PNumber.from(v).orElse(PNumber.ZERO).value();
        }
    }

    /**
     * Parse the provided String into a double, or zero if invalid.
     *
     * @param s
     * @return
     */
    @Deprecated
    public default double d(String s) {
        return d(PString.of(s));
    }

    /**
     * Extract an int from the Property's current Value, or zero if the value
     * cannot be coerced.
     *
     * @param p
     * @return
     */
    @Deprecated
    public default int i(Property p) {
        return p.getInt();
    }

    /**
     * Convert the provided Value into an int, or zero if the Value cannot be
     * coerced.
     *
     * @param v
     * @return
     */
    @Deprecated
    public default int i(Value v) {
        if (v instanceof PNumber) {
            return ((PNumber) v).toIntValue();
        } else {
            return PNumber.from(v).orElse(PNumber.ZERO).toIntValue();
        }
    }

    /**
     * Parse the provided String into an int, or zero if invalid.
     *
     * @param s
     * @return
     */
    @Deprecated
    public default int i(String s) {
        return i(PString.of(s));
    }

    /**
     * Extract the Property's current value as a boolean. If the value cannot be
     * coerced, returns false.
     *
     * @param p
     * @return
     */
    @Deprecated
    public default boolean b(Property p) {
        return p.getBoolean();
    }

    /**
     * Convert the provided Value into a boolean according to the parsing rules
     * of {@link PBoolean}. If the Value cannot be coerced, returns false.
     *
     * @param v
     * @return
     */
    @Deprecated
    public default boolean b(Value v) {
        if (v instanceof PBoolean) {
            return ((PBoolean) v).value();
        } else {
            return PBoolean.from(v).orElse(PBoolean.FALSE).value();
        }
    }

    /**
     * Parse the given String into a boolean according to the parsing rules of
     * {@link PBoolean}. If the String is invalid, returns false.
     *
     * @param s
     * @return
     */
    @Deprecated
    public default boolean b(String s) {
        return b(PString.of(s));
    }

    /**
     * Extract the Property's current value into a String representation.
     *
     * @param p
     * @return
     */
    @Deprecated
    public default String s(Property p) {
        return p.get().toString();
    }

    /**
     * Convert the provided Value into a String representation.
     *
     * @param v
     * @return
     */
    @Deprecated
    public default String s(Value v) {
        return v.toString();
    }

    /**
     * Attempt to extract a {@link PArray} from the given Property. An empty
     * PArray will be returned if the property's value is not a PArray and
     * cannot be coerced.
     *
     * @see #array(org.praxislive.core.types.Value)
     *
     * @param p
     * @return
     */
    public default PArray array(Property p) {
        return PArray.from(p.get()).orElse(PArray.EMPTY);
    }

    /**
     * Convert the given Value into a {@link PArray}. If the Value is already a
     * PArray it will be returned, otherwise an attempt will be made to coerce
     * it. If the Value cannot be converted, an empty PArray will be returned.
     *
     * @param s
     * @return
     */
    public default PArray array(Value v) {
        return PArray.from(v).orElse(PArray.EMPTY);
    }

    /**
     * Parse the given String into a {@link PArray}. If the String is not a
     * valid representation of an array, returns an empty PArray.
     *
     * @param s
     * @return
     */
    public default PArray array(String s) {
        return array(PString.of(s));
    }

    /**
     * Return a random number between zero and max (exclusive)
     *
     * @param max the upper bound of the range
     * @return
     */
    public default double random(double max) {
//        return RND.nextDouble() * max;
        return ThreadLocalRandom.current().nextDouble(max);
    }

    /**
     * Return a random number between min (inclusive) and max (exclusive)
     *
     * @param min the lower bound of the range
     * @param max the upper bound of the range
     * @return
     */
    public default double random(double min, double max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    public default double randomOf(double... values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    public default int randomOf(int... values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    public default String randomOf(String... values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /**
     * Calculate the absolute value of the given value. If the value is
     * positive, the value is returned. If the value is negative, the negation
     * of the value is returned.
     *
     * @param n
     * @return
     */
    public default double abs(double n) {
        return Math.abs(n);
    }

    /**
     * Calculate the square of the given value.
     *
     * @param a
     * @return
     */
    public default double sq(double a) {
        return a * a;
    }

    /**
     * Calculate the square root of the given value.
     *
     * @see Math#sqrt(double)
     *
     * @param a
     * @return
     */
    public default double sqrt(double a) {
        return Math.sqrt(a);
    }

    /**
     * Calculate the natural logarithm if the given value.
     *
     * @see Math#log(double)
     *
     * @param a
     * @return
     */
    public default double log(double a) {
        return Math.log(a);
    }

    /**
     * Calculate Euler's number raised to the power of the given value.
     *
     * @see Math#exp(double)
     *
     * @param a
     * @return
     */
    public default double exp(double a) {
        return Math.exp(a);
    }

    /**
     * Calculate the value of the first argument raised to the power of the
     * second argument.
     *
     * @see Math#pow(double, double)
     *
     * @param a the base
     * @param b the exponent
     * @return the value a<sup>b</sup>
     */
    public default double pow(double a, double b) {
        return Math.pow(a, b);
    }

    /**
     * Calculate the maximum of two values.
     *
     * @param a
     * @param b
     * @return
     */
    public default int max(int a, int b) {
        return Math.max(a, b);
    }

    /**
     * Calculate the maximum of three values.
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    public default int max(int a, int b, int c) {
        return Math.max(a, Math.max(b, c));
    }

    /**
     * Calculate the maximum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return maximum value
     */
    public default int max(int... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = Math.max(value, values[i]);
        }
        return value;
    }

    /**
     * Calculate the maximum of two values.
     *
     * @param a
     * @param b
     * @return
     */
    public default double max(double a, double b) {
        return Math.max(a, b);
    }

    /**
     * Calculate the maximum of three values.
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    public default double max(double a, double b, double c) {
        return Math.max(a, Math.max(b, c));
    }

    /**
     * Calculate the maximum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return maximum value
     */
    public default double max(double... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        double value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = Math.max(value, values[i]);
        }
        return value;
    }

    /**
     * Calculate the minimum of two values.
     *
     * @param a
     * @param b
     * @return
     */
    public default int min(int a, int b) {
        return Math.min(a, b);
    }

    /**
     * Calculate the minimum of three values.
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    public default int min(int a, int b, int c) {
        return Math.min(a, Math.min(b, c));
    }

    /**
     * Calculate the minimum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return minimum value
     */
    public default int min(int... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = Math.min(value, values[i]);
        }
        return value;
    }

    /**
     * Calculate the minimum of two values.
     *
     * @param a
     * @param b
     * @return
     */
    public default double min(double a, double b) {
        return Math.min(a, b);
    }

    /**
     * Calculate the minimum of three values.
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    public default double min(double a, double b, double c) {
        return Math.min(a, Math.min(b, c));
    }

    /**
     * Calculate the minimum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return minimum value
     */
    public default double min(double... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        double value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = Math.min(value, values[i]);
        }
        return value;
    }

    /**
     * Constrain a value between the range of the given low and high values.
     *
     * @param amt input value
     * @param low lowest allowed value
     * @param high highest allowed value
     * @return constrained value
     */
    public default int constrain(int amt, int low, int high) {
        return (amt < low) ? low : ((amt > high) ? high : amt);
    }

    /**
     * Constrain a value between the range of the given low and high values.
     *
     * @param amt input value
     * @param low lowest allowed value
     * @param high highest allowed value
     * @return constrained value
     */
    public default double constrain(double amt, double low, double high) {
        return (amt < low) ? low : ((amt > high) ? high : amt);
    }

    /**
     * Round a value to the nearest integer.
     *
     * @param amt input value
     * @return rounded value
     */
    public default int round(double amt) {
        return Math.round((float) amt);
    }

    /**
     * Converts an angle in radians to an angle in degrees.
     *
     * @see Math#toDegrees(double)
     *
     * @param radians
     * @return
     */
    public default double degrees(double radians) {
        return Math.toDegrees(radians);
    }

    /**
     * Converts an angle in degrees to an angle in radians.
     *
     * @see Math#toRadians(double)
     *
     * @param degrees
     * @return
     */
    public default double radians(double degrees) {
        return Math.toRadians(degrees);
    }

    /**
     * Returns the trigonometric sine of an angle
     *
     * @see Math#sin(double)
     *
     * @param angle
     * @return
     */
    public default double sin(double angle) {
        return Math.sin(angle);
    }

    /**
     * Returns the trigonometric cosine of an angle.
     *
     * @see Math#cos(double)
     *
     * @param angle
     * @return
     */
    public default double cos(double angle) {
        return Math.cos(angle);
    }

    /**
     * Returns the trigonometric tangent of an angle.
     *
     * @see Math#tan(double)
     *
     * @param angle
     * @return
     */
    public default double tan(double angle) {
        return Math.tan(angle);
    }

    /**
     * Returns the arc sine of a value.
     *
     * @see Math#asin(double)
     *
     * @param value
     * @return
     */
    public default double asin(double value) {
        return Math.asin(value);
    }

    /**
     * Returns the arc cosine of a value.
     *
     * @see Math#acos(double)
     *
     * @param value
     * @return
     */
    public default double acos(double value) {
        return Math.acos(value);
    }

    /**
     * Returns the arc tangent of a value.
     *
     * @see Math#atan(double)
     *
     * @param value
     * @return
     */
    public default double atan(double value) {
        return Math.atan(value);
    }

    /**
     * Returns the angle theta from the conversion of rectangular coordinates
     * (x, y) to polar coordinates (r, theta).
     *
     * @see Math#atan2(double, double)
     *
     * @param y
     * @param x
     * @return
     */
    public default double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    /**
     * Re-map (scale) an input value from one range to another. Numbers outside
     * the range are not clamped.
     *
     * @param value the value to be converted
     * @param start1 lower bound of the value's current range
     * @param stop1 upper bound of the value's current range
     * @param start2 lower bound of the value's target range
     * @param stop2 upper bound of the value's target range
     * @return
     */
    public default double map(double value,
            double start1, double stop1,
            double start2, double stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    /**
     * Calculates the distance between two points.
     *
     * @return
     * @param x1 x-coordinate of the first point
     * @param y1 y-coordinate of the first point
     * @param x2 x-coordinate of the second point
     * @param y2 y-coordinate of the second point
     */
    public default double dist(double x1, double y1, double x2, double y2) {
        return sqrt(sq(x2 - x1) + sq(y2 - y1));
    }

    /**
     * Calculates the distance between two points.
     *
     * @return
     * @param x1 x-coordinate of the first point
     * @param y1 y-coordinate of the first point
     * @param z1 z-coordinate of the first point
     * @param x2 x-coordinate of the second point
     * @param y2 y-coordinate of the second point
     * @param z2 z-coordinate of the second point
     */
    public default double dist(double x1, double y1, double z1,
            double x2, double y2, double z2) {
        return sqrt(sq(x2 - x1) + sq(y2 - y1) + sq(z2 - z1));
    }

    /**
     * Calculates a number between two numbers at a specific increment. The
     * <b>amt</b> parameter is the amount to interpolate between the two values
     * where 0.0 equal to the first point, 0.1 is very near the first point, 0.5
     * is half-way in between, etc. The lerp function is convenient for creating
     * motion along a straight path and for drawing dotted lines.
     *
     * @return
     * @param start first value
     * @param stop second value
     * @param amt between 0.0 and 1.0
     */
    public default double lerp(double start, double stop, double amt) {
        return start + (stop - start) * amt;
    }

    /**
     * Normalizes a number from another range into a value between 0 and 1.
     * <p>
     * Identical to map(value, low, high, 0, 1);
     * <p>
     * Numbers outside the range are not clamped to 0 and 1, because
     * out-of-range values are often intentional and useful.
     *
     * @return
     * @param value the incoming value to be converted
     * @param start lower bound of the value's current range
     * @param stop upper bound of the value's current range
     */
    public default double norm(double value, double start, double stop) {
        return (value - start) / (stop - start);
    }

}
