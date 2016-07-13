package org.rxbus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation a target callback method.
 * @author John Kenrinus Lee
 * @version 2016-07-10
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
    // TODO If apply "int[] codes();" on next version or not?
    // TODO If replace with "String code();" on next version or not?
    /** event code or command code */
    int code();
    /**
     * call target callback method on which thread, clamp in [0, Integer.MAX_VALUE),
     * see SCHEDULER_* in this annotation, if custom it, just see SCHEDULER_FOR_FIRST_CUSTOM
     */
    int scheduler();

    /** builtin scheduler, which call target method on current thread */
    int SCHEDULER_CURRENT_THREAD = 0;
    /** builtin scheduler, which call target method on new thread */
    int SCHEDULER_NEW_THREAD = 1;
    /** builtin scheduler, which call target method on io thread pool */
    int SCHEDULER_IO_POOL_THREAD = 2;
    /** builtin scheduler, which call target method on compute thread */
    int SCHEDULER_COMPUTE_POOL_THREAD = 4;
    /** the first custom scheduler should large than the value */
    int SCHEDULER_FOR_FIRST_CUSTOM = 1024;
}
