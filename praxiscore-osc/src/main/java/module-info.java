

module org.praxislive.osc {
    
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.internal.osc;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.osc.components.OSCFactoryProvider;
    
    opens org.praxislive.osc.components to
            org.praxislive.base;
    
}
