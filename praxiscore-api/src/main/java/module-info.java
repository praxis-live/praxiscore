
module org.praxislive.core {
    
    requires java.logging;
    
    exports org.praxislive.core;
    exports org.praxislive.core.protocols;
    exports org.praxislive.core.services;
    exports org.praxislive.core.syntax;
    exports org.praxislive.core.types;
    
    uses org.praxislive.core.Lookup;
    uses org.praxislive.core.Port.TypeProvider;
    uses org.praxislive.core.Protocol.TypeProvider;
}
