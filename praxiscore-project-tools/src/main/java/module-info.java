module org.praxislive.project.tools {

    requires static java.desktop;
    requires org.praxislive.core;
    requires org.praxislive.project;
    requires org.praxislive.script;
    
    provides org.praxislive.script.CommandInstaller with
            org.praxislive.project.tools.ProjectCommands;
}
