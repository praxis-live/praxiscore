
module org.praxislive.tracker {
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.core.code;
    
    exports org.praxislive.tracker;
    
    provides org.praxislive.code.TypeConverter.Provider with
            org.praxislive.tracker.impl.PatternSupport;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.tracker.impl.Components;
    
    opens org.praxislive.tracker.impl;
    opens org.praxislive.tracker.impl.resources;
    
}
