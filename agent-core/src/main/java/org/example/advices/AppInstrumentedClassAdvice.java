package org.example.advices;


import net.bytebuddy.asm.Advice;

public class AppInstrumentedClassAdvice {
    @Advice.OnMethodEnter(inline = false)
    public static void before() {
        System.out.println("before");
    }
}
