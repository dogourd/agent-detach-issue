package org.example.advices;

import net.bytebuddy.asm.Advice;

public class HttpServletAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void before() {
        System.err.println("before HttpServletAdvice");
    }
}
