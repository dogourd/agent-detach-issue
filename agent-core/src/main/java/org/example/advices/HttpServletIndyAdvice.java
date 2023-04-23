package org.example.advices;


import net.bytebuddy.asm.Advice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HttpServletIndyAdvice {

    @Advice.OnMethodEnter(inline = false, suppress = Throwable.class)
    public static void before(HttpServletRequest req, HttpServletResponse resp) {
        System.err.println("before HttpServletIndyAdvice");
    }
}
