
module org.praxislive.hub {
    
    requires java.logging;
    
    requires org.praxislive.base;
    requires org.praxislive.core;
    requires org.praxislive.script;
    requires org.praxislive.util;
    
    exports org.praxislive.hub;
    
    uses org.praxislive.core.services.ComponentFactoryProvider;
    uses org.praxislive.core.RootHub.ExtensionProvider;
    
}
