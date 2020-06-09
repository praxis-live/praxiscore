
module org.praxislive.base {
    
    requires java.logging;
    requires org.praxislive.core;
    
    exports org.praxislive.base;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.base.components.BaseComponents;
    
}
