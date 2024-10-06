package nojdbc.core.annotation.result;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface One {

    String method() default "";

    String clazz() default "";

    boolean required() default false;

}
