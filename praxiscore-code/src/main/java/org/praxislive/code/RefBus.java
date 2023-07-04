/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2023 Neil C Smith.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
class RefBus {

    private final Map<String, RefImpl<?>> publishers;
    private final Map<String, List<RefImpl<?>>> subscribers;

    RefBus() {
        this.publishers = new HashMap<>();
        this.subscribers = new HashMap<>();
    }

    boolean publish(String name, RefImpl<?> publisher) {
        if (publishers.putIfAbsent(name, publisher) == null) {
            notify(name, publisher);
            return true;
        } else {
            return false;
        }
    }

    boolean unpublish(String name, RefImpl<?> publisher) {
        if (publishers.remove(name, publisher)) {
            notify(name, null);
            return true;
        } else {
            return false;
        }
    }

    void subscribe(String name, RefImpl<?> sub) {
        var list = subscribers.computeIfAbsent(name, k -> new ArrayList<RefImpl<?>>());
        if (list.contains(sub)) {
            throw new IllegalStateException();
        }
        list.add(sub);
        var publisher = publishers.get(name);
        if (publisher != null) {
            sub.updateFromPublisher(publisher);
        } else {
            sub.clear();
        }
    }

    void unsubscribe(String name, RefImpl<?> sub) {
        var list = subscribers.get(name);
        if (list != null) {
            list.remove(sub);
            if (list.isEmpty()) {
                subscribers.remove(name);
            }
        }
    }

    boolean notifySubscribers(String name, RefImpl<?> source) {
        if (publishers.get(name) == source) {
            notify(name, source);
            return true;
        } else {
            return false;
        }
    }

    private void notify(String name, RefImpl<?> source) {
        var list = subscribers.get(name);
        if (list != null) {
            if (source != null) {
                list.forEach(sub -> sub.updateFromPublisher(source));
            } else {
                list.forEach(sub -> sub.clear());
            }
        }
    }

}
