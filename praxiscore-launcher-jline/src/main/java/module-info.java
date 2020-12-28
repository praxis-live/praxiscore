
module org.praxislive.launcher.jline {
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.launcher;
    requires org.jline.terminal;
    requires org.jline.terminal.jna;
    requires org.jline.reader;
    requires com.sun.jna;

    provides org.praxislive.launcher.TerminalIOProvider with
            org.praxislive.launcher.jline.JLineTerminalIOProvider;
}
