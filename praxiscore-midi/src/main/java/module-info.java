
module org.praxislive.midi {
    
    requires java.desktop;
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.util;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.midi.components.MidiFactoryProvider;
    
    opens org.praxislive.midi.components to
            org.praxislive.base;
    
}
