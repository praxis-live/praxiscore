
module org.praxislive.audio.components {
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.code;
    requires org.praxislive.audio;
    requires org.praxislive.audio.code;
    
    requires org.jaudiolibs.pipes;
    requires org.jaudiolibs.pipes.units;
    requires org.jaudiolibs.audioservers;
    requires org.jaudiolibs.audioops.impl;
    requires org.jaudiolibs.audioops;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.audio.components.AudioComponents,
            org.praxislive.audio.impl.components.AudioFactoryProvider;
    
    
    uses org.jaudiolibs.audioservers.AudioServerProvider;
    
    opens org.praxislive.audio.components;
    opens org.praxislive.audio.components.resources;
    opens org.praxislive.audio.impl.components to
            org.praxislive.base;
}
