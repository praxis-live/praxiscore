
package org.praxislive.bin;

import java.util.ArrayList;
import java.util.List;
import org.praxislive.launcher.Launcher;


public class Main {
    
    public static void main(String[] args) {
        var handle = ProcessHandle.current();
        handle.info().command().ifPresent(System.out::println);
        handle.info().arguments().ifPresent(System.out::println);
        handle.info().commandLine().ifPresent(System.out::println);
        System.out.println(System.getProperty("java.class.path"));
        System.out.println(System.getProperty("jdk.module.path"));
        System.out.println(System.getenv());
        System.out.println(Main.class.getModule());
        Launcher.main(new LauncherCtxt(), args);
    }
    
    private static class LauncherCtxt implements Launcher.Context {
        
        private final String command;
        private final String modulePath;
        private final String classPath;
        
        private LauncherCtxt() {
            command = ProcessHandle.current().info().command().orElse("java");
            modulePath = System.getProperty("jdk.module.path");
            classPath = System.getProperty("java.class.path");
        }

        @Override
        public ProcessBuilder createChildProcessBuilder(List<String> javaOptions,
                List<String> arguments) {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(javaOptions);
            if (modulePath == null || modulePath.isEmpty()) {
                cmd.add("-classpath");
                cmd.add(classPath);
                cmd.add("org.praxislive.bin.Main");
            } else {
                cmd.add("-p");// -p %classpath -m org.praxislive.bin/org.praxislive.bin.Main
                cmd.add(modulePath);
                cmd.add("-m");
                cmd.add("org.praxislive.bin/org.praxislive.bin.Main");
            }
            cmd.addAll(arguments);
            return new ProcessBuilder(cmd);
        }
        
    }
    
}
