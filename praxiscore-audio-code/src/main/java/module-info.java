
module org.praxislive.audio.code {
    
    requires org.praxislive.audio;
    requires org.praxislive.code;
    requires org.praxislive.core;
    requires org.praxislive.logging;
    
    requires org.praxislive.internal.audioio;
    
    requires org.jaudiolibs.pipes;
    requires org.jaudiolibs.audioservers;
    requires org.jaudiolibs.audioops.impl;
    requires org.jaudiolibs.audioops;

    exports org.praxislive.audio.code;
    exports org.praxislive.audio.code.userapi;
    
    opens org.praxislive.audio.code.resources;
    
}
