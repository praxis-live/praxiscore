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
package org.praxislive.core.code;

import java.lang.reflect.Method;
import org.praxislive.code.CodeConnector;
import org.praxislive.code.CodeContext;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.DefaultCodeDelegate;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.services.LogLevel;

/**
 * Basic data code delegate base class.
 */
public class DataCodeDelegate extends DefaultCodeDelegate {

    /**
     * Hook called whenever the delegate needs to be initialized. Will be called
     * when the root is started, on adding a component to a running root, and
     * any time the code is updated. Because this code is called in a running
     * root, the code should be suitable for real-time usage.
     */
    public void init() {
    }

    /**
     * Hook called whenever the root is started. This method will be called
     * after {@link #init()}. It is not called on code updates.
     */
    public void starting() {
    }

    /**
     * Hook called on every clock update. This will vary depending on the root
     * the component is installed into - it may correspond to every buffer or
     * frame. If a component reacts solely to input and doesn't need to be
     * called every cycle, do not override this method so that the delegate does
     * not have to be connected to the clock (for efficiency).
     */
    public void update() {
    }

    /**
     * Hook called when the root is stopping.
     */
    public void stopping() {
    }

    static class Connector extends CodeConnector<DataCodeDelegate> {

        private final static String UPDATE = "update";

        private boolean foundUpdate;

        Connector(CodeFactory.Task<DataCodeDelegate> contextCreator,
                DataCodeDelegate delegate) {
            super(contextCreator, delegate);
        }

        @Override
        protected boolean requiresClock() {
            return super.requiresClock() || foundUpdate;
        }

        @Override
        protected void analyseMethod(Method method) {

            if (UPDATE.equals(method.getName())
                    && method.getParameterCount() == 0) {
                foundUpdate = true;
            }

            super.analyseMethod(method);
        }

    }

    static class Context extends CodeContext<DataCodeDelegate> {

        public Context(Connector connector) {
            super(connector);
        }

        @Override
        protected void onInit() {
            try {
                getDelegate().init();
            } catch (Exception e) {
                getLog().log(LogLevel.ERROR, e, "Exception thrown during init()");
            }

        }

        @Override
        protected void onStart() {
            try {
                getDelegate().starting();
            } catch (Exception e) {
                getLog().log(LogLevel.ERROR, e, "Exception thrown during starting()");
            }
        }

        @Override
        protected void onStop() {
            try {
                getDelegate().stopping();
            } catch (Exception e) {
                getLog().log(LogLevel.ERROR, e, "Exception thrown during stopping()");
            }
        }

        @Override
        protected void tick(ExecutionContext source) {
            try {
                getDelegate().update();
            } catch (Exception e) {
                getLog().log(LogLevel.ERROR, e, "Exception thrown during update()");
            }
        }

    }

}
