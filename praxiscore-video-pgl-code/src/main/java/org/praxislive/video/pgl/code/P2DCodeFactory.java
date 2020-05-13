

package org.praxislive.video.pgl.code;

import org.praxislive.code.CodeContext;
import org.praxislive.code.CodeFactory;
import org.praxislive.core.ComponentType;


public class P2DCodeFactory extends CodeFactory<P2DCodeDelegate> {
    
    private final static P2DClassBodyContext CBC = new P2DClassBodyContext();
    
    private final boolean emptyDefault;
    
    public P2DCodeFactory(String type) {
        super(CBC, ComponentType.of(type), P2DClassBodyContext.TEMPLATE);
        emptyDefault = true;
    }
    
    public P2DCodeFactory(String type, String sourceTemplate) {
        super(CBC, ComponentType.of(type), sourceTemplate);
        emptyDefault = false;
    }

    @Override
    public Task<P2DCodeDelegate> task() {
        return new P2DContextCreator();
    }

    private class P2DContextCreator extends Task<P2DCodeDelegate> {

        private P2DContextCreator() {
            super(P2DCodeFactory.this);
        }

        @Override
        protected CodeContext<P2DCodeDelegate> createCodeContext(P2DCodeDelegate delegate) {
            return new P2DCodeContext(new P2DCodeConnector(this, delegate));
        }


    }
    
}
