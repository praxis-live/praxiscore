
module org.praxislive.audio.components {
    
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.audio;
    requires org.praxislive.audio.code;
    
    requires org.jaudiolibs.pipes;
    requires org.jaudiolibs.audioservers;
    requires org.jaudiolibs.audioops.impl;
    requires org.jaudiolibs.audioops;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.audio.components.AudioComponents;
    
    opens org.praxislive.audio.components;
    opens org.praxislive.audio.components.resources;
}
