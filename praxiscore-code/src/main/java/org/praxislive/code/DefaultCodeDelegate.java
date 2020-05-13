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

import java.lang.reflect.Array;
import java.util.Optional;
import java.util.Random;
import org.praxislive.code.userapi.Constants;
import org.praxislive.code.userapi.Property;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlPort;
import org.praxislive.core.Lookup;
import org.praxislive.core.Port;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.core.Value;
import org.praxislive.logging.LogLevel;

/**
 * Default base for code delegates providing a variety of functions.
 */
public class DefaultCodeDelegate extends CodeDelegate {

    final static String[] IMPORTS = {
        "java.util.*",
        "java.util.function.*",
        "java.util.stream.*",
        "org.praxislive.core.*",
        "org.praxislive.core.types.*",
        "org.praxislive.code.userapi.*",
        "static org.praxislive.code.userapi.Constants.*"
    };

    protected final Random RND;

    public DefaultCodeDelegate() {
        RND = new Random();
    }

    /**
     * Send a log message.
     * 
     * @param level
     * @param msg
     */
    public final void log(LogLevel level, String msg) {
        getContext().getLog().log(level, msg);
    }

    /**
     * Send a log message with associated Exception type.
     * 
     * @param level
     * @param ex
     */
    public final void log(LogLevel level, Exception ex) {
        getContext().getLog().log(level, ex);
    }

    /**
     * Send a log message with associated Exception.
     * 
     * @param level
     * @param ex
     * @param msg
     */
    public final void log(LogLevel level, Exception ex, String msg) {
        getContext().getLog().log(level, ex, msg);
    }

    /**
     * Send a log message with associated Exception type.
     * 
     * @param level
     * @param type
     * @param msg
     */
    public final void log(LogLevel level, Class<? extends Exception> type, String msg) {
        getContext().getLog().log(level, type, msg);
    }

    /**
     * Check whether the messages at the given log level are being sent.
     * 
     * @param level
     * @return
     */
    public final boolean isLoggable(LogLevel level) {
        return getContext().getLogLevel().isLoggable(level);
    }

    /**
     * Send a value to a port on another component. The other component must have
     * the same parent.
     * 
     * @param componentID ID of the other component
     * @param portID ID of the port on the other component
     * @param value
     */
    public final void transmit(String componentID, String portID, String value) {
        this.transmit(componentID, portID, PString.of(value));
    }

    /**
     * Send a value to a port on another component. The other component must have
     * the same parent.
     * 
     * @param componentID ID of the other component
     * @param portID ID of the port on the other component
     * @param value
     */
    public final void transmit(String componentID, String portID, Value value) {
        ControlPort.Input port = findPort(componentID, portID);
        if (port == null) {
            log(LogLevel.ERROR, "Can't find an input port at " + componentID + "!" + portID);
        } else {
            try {
                port.receive(time(), value);
            } catch (Exception ex) {
                log(LogLevel.ERROR, ex);
            }
        }
    }

    /**
     * Send a value to a port on another component. The other component must have
     * the same parent.
     * 
     * @param componentID ID of the other component
     * @param portID ID of the port on the other component
     * @param value
     */
    public final void transmit(String componentID, String portID, double value) {
        ControlPort.Input port = findPort(componentID, portID);
        if (port == null) {
            log(LogLevel.ERROR, "Can't find an input port at " + componentID + "!" + portID);
        } else {
            try {
                port.receive(time(), value);
            } catch (Exception ex) {
                log(LogLevel.ERROR, ex);
            }
        }
    }

    private ControlPort.Input findPort(String cmp, String port) {
        Component thisCmp = getContext().getComponent();
        Container parent = thisCmp.getParent();
        if (parent == null) {
            return null;
        }
        Component thatCmp = parent.getChild(cmp);
        if (thatCmp == null) {
            return null;
        }
        Port thatPort = thatCmp.getPort(port);
        if (thatPort instanceof ControlPort.Input) {
            return (ControlPort.Input) thatPort;
        } else {
            return null;
        }
    }
    
    /**
     * Send a message to a Control.
     * 
     * @param destination address of control
     * @param value message value
     */
    public final void tell(ControlAddress destination, String value) {
        tell(destination, PString.of(value));
    }

    /**
     * Send a message to a Control.
     * 
     * @param destination address of control
     * @param value message value
     */
    public final void tell(ControlAddress destination, double value) {
        tell(destination, PNumber.of(value));
    }

    /**
     * Send a message to a Control.
     * 
     * @param destination address of control
     * @param value message value
     */
    public final void tell(ControlAddress destination, Value value) {
        Call call = Call.createQuiet(destination, self("_log"), getContext().getTime(), value);
        getContext().getComponent().getPacketRouter().route(call);
    }
    
    /**
     * Send a message to a Control in the given number of seconds or fractions
     * of second from now.
     * 
     * @param seconds from now
     * @param destination address of control
     * @param value message value
     */
    public final void tellIn(double seconds, ControlAddress destination, String value) {
        tellIn(seconds, destination, PString.of(value));
    }

    /**
     * Send a message to a Control in the given number of seconds or fractions
     * of second from now.
     * 
     * @param seconds from now
     * @param destination address of control
     * @param value message value
     */
    public final void tellIn(double seconds, ControlAddress destination, double value) {
        tellIn(seconds, destination, PNumber.of(value));
    }

    /**
     * Send a message to a Control in the given number of seconds or fractions
     * of second from now.
     * 
     * @param seconds from now
     * @param destination address of control
     * @param value message value
     */
    public final void tellIn(double seconds, ControlAddress destination, Value value) {
        long time = getContext().getTime() + ((long) (seconds * 1_000_000_000));
        Call call = Call.createQuiet(destination, self("_log"), time, value);
        getContext().getComponent().getPacketRouter().route(call);
    }

    /**
     * Get this component's address.
     * 
     * @return address of self
     */
    public final ComponentAddress self() {
        return getContext().getComponent().getAddress();
    }

    /**
     * Get the address of a control on this component.
     * 
     * @param control id of control
     * @return address of control
     */
    public final ControlAddress self(String control) {
        return ControlAddress.of(self(), control);
    }
    
    
    
    /**
     * Return a Lookup for finding instances of features.
     * 
     * @return Lookup context
     */
    public Lookup getLookup() {
        return getContext().getLookup();
    }
    
    /**
     * Search for an instance of the given type.
     * @param <T>
     * @param type class to search for
     * @return Optional wrapping the result if found, or empty if not
     */
    public <T> Optional<T> find(Class<T> type) {
        return getLookup().find(type);
    }

    /**
     * The current clocktime in nanoseconds. May only be used relatively to itself, 
     * and may be negative.
     * 
     * @return
     */
    public final long time() {
        return getContext().getTime();
    }

    /**
     * The current time in milliseconds since the root was started.
     * 
     * @return
     */
    public final long millis() {
        return (time() - getContext().getExecutionContext().getStartTime())
                / 1_000_000;
    }

    /**
     * Extract a double from the Property's current Value, or zero if the value
     * cannot be coerced.
     * 
     * @param p
     * @return
     */
    public final double d(Property p) {
        return p.getDouble();
    }

    /**
     * Convert the provided Value into a double, or zero if the Value cannot be
     * coerced.
     * 
     * @param v
     * @return
     */
    public final double d(Value v) {
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
    public final double d(String s) {
        return d(PString.of(s));
    }

    /**
     * Extract an int from the Property's current Value, or zero if the value
     * cannot be coerced.
     * 
     * @param p
     * @return
     */
    public final int i(Property p) {
        return p.getInt();
    }

    /**
     * Convert the provided Value into an int, or zero if the Value cannot be
     * coerced.
     * 
     * @param v
     * @return
     */
    public final int i(Value v) {
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
    public final int i(String s) {
        return i(PString.of(s));
    }

    /**
     * Extract the Property's current value as a boolean. If the value cannot be
     * coerced, returns false.
     * 
     * @param p
     * @return
     */
    public final boolean b(Property p) {
        return p.getBoolean();
    }

    /**
     * Convert the provided Value into a boolean according to the parsing rules of
     * {@link PBoolean}. If the Value cannot be coerced, returns false.
     * 
     * @param v
     * @return
     */
    public final boolean b(Value v) {
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
    public final boolean b(String s) {
        return b(PString.of(s));
    }

    /**
     * Extract the Property's current value into a String representation.
     * 
     * @param p
     * @return
     */
    public final String s(Property p) {
        return p.get().toString();
    }

    /**
     * Convert the provided Value into a String representation.
     * 
     * @param v
     * @return
     */
    public final String s(Value v) {
        return v.toString();
    }

    /**
     * Attempt to extract a {@link PArray} from the given Property. An empty PArray
     * will be returned if the property's value is not a PArray and cannot be coerced.
     * 
     * @see #array(org.praxislive.core.types.Value) 
     * 
     * @param p
     * @return
     */
    public final PArray array(Property p) {
        return PArray.from(p.get()).orElse(PArray.EMPTY);
    }

    /**
     * Convert the given Value into a {@link PArray}. If the Value is already a
     * PArray it will be returned, otherwise an attempt will be made to coerce it.
     * If the Value cannot be converted, an empty PArray will be returned.
     * 
     * @param s
     * @return
     */
    public final PArray array(Value v) {
        return PArray.from(v).orElse(PArray.EMPTY);
    }

    /**
     * Parse the given String into a {@link PArray}. If the String is not a valid
     * representation of an array, returns an empty PArray.
     * 
     * @param s
     * @return
     */
    public final PArray array(String s) {
        return array(PString.of(s));
    }

    /**
     * Return a random number between zero and max (exclusive)
     * 
     * @param max the upper bound of the range
     * @return
     */
    public final double random(double max) {
        return RND.nextDouble() * max;
    }

    /**
     * Return a random number between min (inclusive) and max (exclusive)
     * 
     * @param min the lower bound of the range
     * @param max the upper bound of the range
     * @return
     */
    public final double random(double min, double max) {
        if (min >= max) {
            return min;
        }
        return random(max - min) + min;
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    public final double randomOf(double... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    public final int randomOf(int... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    public final String randomOf(String... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Calculate the absolute value of the given value. If the value is
     * positive, the value is returned. If the value is negative, the negation
     * of the value is returned.
     *
     * @param n
     * @return
     */
    public final double abs(double n) {
        return Math.abs(n);
    }

    /**
     * Calculate the square of the given value.
     *
     * @param a
     * @return
     */
    public final double sq(double a) {
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
    public final double sqrt(double a) {
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
    public final double log(double a) {
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
    public final double exp(double a) {
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
    public final double pow(double a, double b) {
        return Math.pow(a, b);
    }

    /**
     * Calculate the maximum of two values.
     *
     * @param a
     * @param b
     * @return
     */
    public final int max(int a, int b) {
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
    public final int max(int a, int b, int c) {
        return max(a, max(b, c));
    }

    /**
     * Calculate the maximum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return maximum value
     */
    public final int max(int ... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = max(value, values[i]);
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
    public final double max(double a, double b) {
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
    public final double max(double a, double b, double c) {
        return max(a, max(b, c));
    }

    /**
     * Calculate the maximum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return maximum value
     */
    public final double max(double ... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        double value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = max(value, values[i]);
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
    public final int min(int a, int b) {
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
    public final int min(int a, int b, int c) {
        return min(a, min(b, c));
    }

    /**
     * Calculate the minimum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return minimum value
     */
    public final int min(int ... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = min(value, values[i]);
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
    public final double min(double a, double b) {
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
    public final double min(double a, double b, double c) {
        return min(a, min(b, c));
    }

    /**
     * Calculate the minimum value in the provided array.
     *
     * @param values value list - must not be empty
     * @return minimum value
     */
    public final double min(double ... values) {
        if (values.length == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        double value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = min(value, values[i]);
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
    public final int constrain(int amt, int low, int high) {
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
    public final double constrain(double amt, double low, double high) {
        return (amt < low) ? low : ((amt > high) ? high : amt);
    }
    
    /**
     * Round a value to the nearest integer.
     *
     * @param amt input value
     * @return rounded value
     */
    public final int round(double amt) {
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
    public final double degrees(double radians) {
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
    public final double radians(double degrees) {
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
    public final double sin(double angle) {
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
    public final double cos(double angle) {
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
    public final double tan(double angle) {
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
    public final double asin(double value) {
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
    public final double acos(double value) {
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
    public final double atan(double value) {
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
    public final double atan2(double y, double x) {
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
    public final double map(double value,
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
    public final double dist(double x1, double y1, double x2, double y2) {
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
    public final double dist(double x1, double y1, double z1,
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
    public final double lerp(double start, double stop, double amt) {
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
    public final double norm(double value, double start, double stop) {
        return (value - start) / (stop - start);
    }

    // PERLIN NOISE - copied from Processing core.
    // @TODO fully convert to double???
    private static final int PERLIN_YWRAPB = 4;
    private static final int PERLIN_YWRAP = 1 << PERLIN_YWRAPB;
    private static final int PERLIN_ZWRAPB = 8;
    private static final int PERLIN_ZWRAP = 1 << PERLIN_ZWRAPB;
    private static final int PERLIN_SIZE = 4095;
//  private static final float sinLUT[];
    private static final float cosLUT[];
    private static final float SINCOS_PRECISION = 0.5f;
    private static final int SINCOS_LENGTH = (int) (360f / SINCOS_PRECISION);

    static {
//    sinLUT = new float[SINCOS_LENGTH];
        cosLUT = new float[SINCOS_LENGTH];
        for (int i = 0; i < SINCOS_LENGTH; i++) {
//      sinLUT[i] = (float) Math.sin(i * Constants.DEG_TO_RAD * SINCOS_PRECISION);
            cosLUT[i] = (float) Math.cos(i * Constants.DEG_TO_RAD * SINCOS_PRECISION);
        }
    }
    private int perlin_octaves = 4; // default to medium smooth
    private float perlin_amp_falloff = 0.5f; // 50% reduction/octave
    private int perlin_TWOPI, perlin_PI;
    private float[] perlin_cosTable;
    private float[] perlin;
    private Random perlinRandom;

    /**
     * Computes the Perlin noise function value at point x.
     *
     * @param x
     * @return
     */
    public final double noise(double x) {
        return noise(x, 0f, 0f);
    }

    /**
     * Computes the Perlin noise function value at the point x, y.
     *
     * @param x
     * @param y
     * @return
     */
    public final double noise(double x, double y) {
        return noise(x, y, 0f);
    }

    /**
     * Computes the Perlin noise function value at x, y, z.
     *
     * @param x
     * @param z
     * @param y
     * @return
     */
    public final double noise(double x, double y, double z) {
        if (perlin == null) {
            if (perlinRandom == null) {
                perlinRandom = new Random();
            }
            perlin = new float[PERLIN_SIZE + 1];
            for (int i = 0; i < PERLIN_SIZE + 1; i++) {
                perlin[i] = perlinRandom.nextFloat();
            }
            perlin_cosTable = cosLUT;
            perlin_TWOPI = perlin_PI = SINCOS_LENGTH;
            perlin_PI >>= 1;
        }

        if (x < 0) {
            x = -x;
        }
        if (y < 0) {
            y = -y;
        }
        if (z < 0) {
            z = -z;
        }

        int xi = (int) x, yi = (int) y, zi = (int) z;
        float xf = (float) (x - xi);
        float yf = (float) (y - yi);
        float zf = (float) (z - zi);
        float rxf, ryf;

        float r = 0;
        float ampl = 0.5f;

        float n1, n2, n3;

        for (int i = 0; i < perlin_octaves; i++) {
            int of = xi + (yi << PERLIN_YWRAPB) + (zi << PERLIN_ZWRAPB);

            rxf = noise_fsc(xf);
            ryf = noise_fsc(yf);

            n1 = perlin[of & PERLIN_SIZE];
            n1 += rxf * (perlin[(of + 1) & PERLIN_SIZE] - n1);
            n2 = perlin[(of + PERLIN_YWRAP) & PERLIN_SIZE];
            n2 += rxf * (perlin[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n2);
            n1 += ryf * (n2 - n1);

            of += PERLIN_ZWRAP;
            n2 = perlin[of & PERLIN_SIZE];
            n2 += rxf * (perlin[(of + 1) & PERLIN_SIZE] - n2);
            n3 = perlin[(of + PERLIN_YWRAP) & PERLIN_SIZE];
            n3 += rxf * (perlin[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n3);
            n2 += ryf * (n3 - n2);

            n1 += noise_fsc(zf) * (n2 - n1);

            r += n1 * ampl;
            ampl *= perlin_amp_falloff;
            xi <<= 1;
            xf *= 2;
            yi <<= 1;
            yf *= 2;
            zi <<= 1;
            zf *= 2;

            if (xf >= 1.0f) {
                xi++;
                xf--;
            }
            if (yf >= 1.0f) {
                yi++;
                yf--;
            }
            if (zf >= 1.0f) {
                zi++;
                zf--;
            }
        }
        return r;
    }

    private float noise_fsc(float i) {
        return 0.5f * (1.0f - perlin_cosTable[(int) (i * perlin_PI) % perlin_TWOPI]);
    }

    // make perlin noise quality user controlled to allow
    // for different levels of detail. lower values will produce
    // smoother results as higher octaves are surpressed
    /**
     *
     * @param lod
     */
    public final void noiseDetail(int lod) {
        if (lod > 0) {
            perlin_octaves = lod;
        }
    }

    /**
     *
     * @param lod
     * @param falloff
     */
    public final void noiseDetail(int lod, double falloff) {
        if (lod > 0) {
            perlin_octaves = lod;
        }
        if (falloff > 0) {
            perlin_amp_falloff = (float) falloff;
        }
    }

    /**
     *
     * @param what
     */
    public final void noiseSeed(long what) {
        if (perlinRandom == null) {
            perlinRandom = new Random();
        }
        perlinRandom.setSeed(what);
        perlin = null;
    }

    // end of Perlin noise functions
    // start of PApplet statics

    /**
     * Copies an array (or part of an array) to another array. The src array is
     * copied to the dst array, beginning at the position specified by srcPos 
     * and into the position specified by dstPos. The number of elements to copy
     * is determined by length.
     * 
     * @param src
     * @param srcPosition
     * @param dst
     * @param dstPosition
     * @param length
     */
    public final void arrayCopy(Object src, int srcPosition, Object dst, int dstPosition, int length) {
        System.arraycopy(src, srcPosition, dst, dstPosition, length);
    }

    /**
     * Copies an array (or part of an array) to another array. The src array is
     * copied to the dst array. The number of elements to copy
     * is determined by length.
     * 
     * @param src
     * @param dst
     * @param length
     */
    public final void arrayCopy(Object src, Object dst, int length) {
        System.arraycopy(src, 0, dst, 0, length);
    }

    /**
     * Copies an array to another array. The src array is copied to the dst array.
     * 
     * @param src
     * @param dst
     */
    public final void arrayCopy(Object src, Object dst) {
        System.arraycopy(src, 0, dst, 0, Array.getLength(src));
    }

}
