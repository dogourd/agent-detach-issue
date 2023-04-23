package org.example;


import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.TimeUnit;

public class Premain {

    private static Class<?> agentClass;
    private static URLClassLoader agentClassLoader;


    public static void premain(String args, Instrumentation instrumentation) throws MalformedURLException {
        String detachFile = System.getProperty("agent.detach.file");
        if (detachFile == null) {
            throw new NullPointerException("system property: [agent.detach.file]");
        }

        String agentFile = System.getProperty("agent.file");
        if (agentFile == null) {
            throw new NullPointerException("system property: [agent.file]");
        }

        agentClassLoader = new MyURLClassLoader(new URL[]{new File(agentFile).toURI().toURL()}, null);
        try {
            agentClass = Class.forName("org.example.AgentMain", true, agentClassLoader);
            agentClass.getDeclaredMethod("init", String.class, Instrumentation.class)
                    .invoke(null, args, instrumentation);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        new Thread(() -> {
            while (true) {
                File flag = new File(detachFile);
                if (!flag.exists()) {
                    try {
                        TimeUnit.SECONDS.sleep(5L);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                try {
                    agentClass.getDeclaredMethod("detach")
                                    .invoke(null);
                    System.err.println("release agent class.");
                    agentClass = null;
                    System.err.println("close agent class loader.");
                    agentClassLoader.close();
                    System.err.println("release agent class loader.");
                    agentClassLoader = null;

                    System.gc();
                    System.runFinalization();
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                break;
            }
        }).start();
    }


}
