
module org.praxislive.code {

    requires java.compiler;
    requires java.desktop;
    requires java.logging;

    requires transitive org.praxislive.core;
    requires org.praxislive.base;
    
    exports org.praxislive.code;
    exports org.praxislive.code.userapi;

    provides javax.annotation.processing.Processor with 
            org.praxislive.code.internal.GenerateTemplateProcessor;
    provides org.praxislive.core.Port.TypeProvider with
            org.praxislive.code.internal.CodePortTypeProvider;
    provides org.praxislive.core.Protocol.TypeProvider with
            org.praxislive.code.internal.CodeProtocolsProvider;
    
    uses org.praxislive.code.CodeConnector.Plugin;
    uses org.praxislive.code.TypeConverter.Provider;
    
}
