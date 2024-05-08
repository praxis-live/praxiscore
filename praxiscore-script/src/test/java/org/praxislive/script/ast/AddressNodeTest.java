package org.praxislive.script.ast;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.praxislive.core.Value;
import org.praxislive.core.ComponentAddress;
import org.praxislive.script.Command;
import org.praxislive.script.Namespace;
import org.praxislive.script.Variable;

import static org.junit.jupiter.api.Assertions.*;


public class AddressNodeTest {

    /**
     * Test of writeResult method, of class AddressNode.
     */
    @Test
    public void testWriteResult() {
        Namespace ns = new NS();
        List<Value> scratch = new ArrayList<>();
        List<String> relative = List.of(
                "./to/here",
                "./to/here.control",
                "./to/here!port",
                ".control2");
        List<String> absolute = List.of(
                "/test/address/to/here",
                "/test/address/to/here.control",
                "/test/address/to/here!port",
                "/test/address.control2");
        List<String> result = relative.stream()
                .map(address -> {
                    var addressNode = new AddressNode(address);
                    addressNode.init(ns);
                    scratch.clear();
                    addressNode.writeResult(scratch);
                    return scratch.get(0).toString();
                }).toList();
        assertEquals(absolute, result);

    }

    private class NS implements Namespace, Variable {

        @Override
        public Variable getVariable(String id) {
            if ("_CTXT".equals(id)) {
                return this;
            }
            return null;
        }

        @Override
        public void addVariable(String id, Variable var) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Namespace createChild() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setValue(Value value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Value getValue() {
            return ComponentAddress.of("/test/address");
        }

        @Override
        public Command getCommand(String id) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void addCommand(String id, Command cmd) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

}
