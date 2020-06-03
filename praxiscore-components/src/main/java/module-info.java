
module org.praxislive.core.components {

    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.core.code;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.core.components.CoreComponents;
    
    opens org.praxislive.core.components;
    opens org.praxislive.core.components.resources;
    
}
