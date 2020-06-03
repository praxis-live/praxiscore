
module org.praxislive.code.services {
    
    requires java.compiler;
    requires java.logging;
    
    requires org.praxislive.base;
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.script;

    provides org.praxislive.core.RootHub.ExtensionProvider with
            org.praxislive.code.services.CodeServicesExtensionProvider;
    provides org.praxislive.script.CommandInstaller with
            org.praxislive.code.services.CompilerCommandInstaller;
    
    uses org.praxislive.core.services.ComponentFactoryProvider;
//    uses org.praxislive.code.services.tools.JavaCompilerProvider;
}
