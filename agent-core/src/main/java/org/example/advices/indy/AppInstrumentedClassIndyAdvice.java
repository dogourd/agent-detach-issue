package org.example.advices.indy;


import net.bytebuddy.asm.Advice;

public class AppInstrumentedClassIndyAdvice {

    @Advice.OnMethodEnter(inline = false)
    public static void before() {
        System.out.println("before");
    }
}
